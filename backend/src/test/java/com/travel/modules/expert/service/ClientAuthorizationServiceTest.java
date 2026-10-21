package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.expert.dto.AuthorizationConfirmResponse;
import com.travel.modules.expert.dto.AuthorizeResponse;
import com.travel.modules.expert.entity.ClientAuthorization;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.expert.mapper.ClientAuthorizationMapper;

@ExtendWith(MockitoExtension.class)
class ClientAuthorizationServiceTest {

    private static final long EXPERT_ID = 7L;
    private static final long CLIENT_ID = 11L;
    private static final long CLIENT_USER_ID = 21L;

    @Mock
    private ClientAuthorizationMapper authorizationMapper;
    @Mock
    private ExpertClientService expertClientService;

    private ClientAuthorizationService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(EXPERT_ID, "expert"));
        service = new ClientAuthorizationService(authorizationMapper, expertClientService);
        lenient().when(authorizationMapper.insert(any(ClientAuthorization.class)))
                .thenAnswer(invocation -> {
                    ClientAuthorization auth = invocation.getArgument(0);
                    auth.setId(99L);
                    return 1;
                });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void authorizeStoresOnlyTokenHash() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));

        AuthorizeResponse response = service.authorize(CLIENT_ID, List.of("profile", "profile"));

        assertThat(response.getScope()).containsExactly("profile");
        assertThat(response.getConfirmToken()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now());
        ArgumentCaptor<ClientAuthorization> captor =
                ArgumentCaptor.forClass(ClientAuthorization.class);
        verify(authorizationMapper).insert(captor.capture());
        ClientAuthorization saved = captor.getValue();
        assertThat(saved.getTokenHash()).hasSize(64)
                .isNotEqualTo(response.getConfirmToken())
                .isEqualTo(ClientAuthorizationService.sha256(response.getConfirmToken()));
        assertThat(saved.getStatus()).isEqualTo(ClientAuthorization.STATUS_PENDING);
    }

    @Test
    void authorizeRequiresLinkedAccount() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(null));

        assertBizCode(() -> service.authorize(CLIENT_ID, List.of("profile")),
                ResultCode.UNPROCESSABLE);
        verify(authorizationMapper, never()).insert(any(ClientAuthorization.class));
    }

    @Test
    void authorizeRejectsUnknownScope() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));

        assertBizCode(() -> service.authorize(CLIENT_ID, List.of("profile", "orders")),
                ResultCode.PARAM_ERROR);
    }

    @Test
    void authorizeRejectsEmptyScope() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));

        assertBizCode(() -> service.authorize(CLIENT_ID, List.of()), ResultCode.PARAM_ERROR);
    }

    @Test
    void confirmActivatesPendingAuthorization() {
        UserContext.set(new LoginUser(CLIENT_USER_ID, "client"));
        when(authorizationMapper.selectOne(any())).thenReturn(pendingAuth());
        when(expertClientService.findById(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));
        when(authorizationMapper.update(any(), any())).thenReturn(1);

        AuthorizationConfirmResponse response = service.confirm("raw-token");

        assertThat(response.getStatus()).isEqualTo(ClientAuthorization.STATUS_ACTIVE);
        assertThat(response.getGrantedAt()).isNotNull();
        // 先撤回旧 active，再条件更新为 active
        verify(authorizationMapper, times(2)).update(any(), any());
    }

    @Test
    void confirmRejectsWhenLoggedInUserIsNotLinkedClient() {
        UserContext.set(new LoginUser(CLIENT_USER_ID + 1, "other"));
        when(authorizationMapper.selectOne(any())).thenReturn(pendingAuth());
        when(expertClientService.findById(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));

        assertBizCode(() -> service.confirm("raw-token"), ResultCode.NOT_OWNER);
    }

    @Test
    void confirmRejectsExpiredToken() {
        ClientAuthorization auth = pendingAuth();
        auth.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(authorizationMapper.selectOne(any())).thenReturn(auth);

        assertBizCode(() -> service.confirm("raw-token"), ResultCode.CONFLICT);
    }

    @Test
    void confirmRejectsUnknownOrProcessedToken() {
        when(authorizationMapper.selectOne(any())).thenReturn(null);

        assertBizCode(() -> service.confirm("raw-token"), ResultCode.NOT_FOUND);
    }

    @Test
    void confirmRejectsWhenConditionalUpdateLosesRace() {
        UserContext.set(new LoginUser(CLIENT_USER_ID, "client"));
        when(authorizationMapper.selectOne(any())).thenReturn(pendingAuth());
        when(expertClientService.findById(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));
        when(authorizationMapper.update(any(), any())).thenReturn(0);

        assertBizCode(() -> service.confirm("raw-token"), ResultCode.CONFLICT);
    }

    @Test
    void requireAuthorizedFailsWhenNoActiveAuthorization() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));
        when(authorizationMapper.selectOne(any())).thenReturn(null);

        assertBizCode(() -> service.requireAuthorized(CLIENT_ID, "profile"), ResultCode.FORBIDDEN);
    }

    @Test
    void requireAuthorizedFailsWhenScopeMissing() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));
        ClientAuthorization active = pendingAuth();
        active.setStatus(ClientAuthorization.STATUS_ACTIVE);
        when(authorizationMapper.selectOne(any())).thenReturn(active);

        assertBizCode(() -> service.requireAuthorized(CLIENT_ID, "guidebooks"),
                ResultCode.FORBIDDEN);
    }

    @Test
    void requireAuthorizedReturnsClientWhenScopeGranted() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));
        ClientAuthorization active = pendingAuth();
        active.setStatus(ClientAuthorization.STATUS_ACTIVE);
        when(authorizationMapper.selectOne(any())).thenReturn(active);

        assertThat(service.requireAuthorized(CLIENT_ID, "profile").getId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void revokeByExpertMarksAuthorizationRevoked() {
        ClientAuthorization active = pendingAuth();
        active.setStatus(ClientAuthorization.STATUS_ACTIVE);
        when(authorizationMapper.selectById(99L)).thenReturn(active);

        service.revoke(99L);

        verify(authorizationMapper).update(any(), any());
    }

    @Test
    void revokeByLinkedClientIsAllowed() {
        UserContext.set(new LoginUser(CLIENT_USER_ID, "client"));
        ClientAuthorization active = pendingAuth();
        active.setStatus(ClientAuthorization.STATUS_ACTIVE);
        when(authorizationMapper.selectById(99L)).thenReturn(active);
        when(expertClientService.findById(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));

        service.revoke(99L);

        verify(authorizationMapper).update(any(), any());
    }

    @Test
    void revokeRejectsUnrelatedUser() {
        UserContext.set(new LoginUser(CLIENT_USER_ID + 5, "stranger"));
        ClientAuthorization active = pendingAuth();
        active.setStatus(ClientAuthorization.STATUS_ACTIVE);
        when(authorizationMapper.selectById(99L)).thenReturn(active);
        when(expertClientService.findById(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));

        assertBizCode(() -> service.revoke(99L), ResultCode.NOT_OWNER);
        verify(authorizationMapper, never()).update(any(), any());
    }

    @Test
    void revokeIsIdempotentWhenAlreadyRevoked() {
        ClientAuthorization revoked = pendingAuth();
        revoked.setStatus(ClientAuthorization.STATUS_REVOKED);
        when(authorizationMapper.selectById(99L)).thenReturn(revoked);

        service.revoke(99L);

        verify(authorizationMapper, never()).update(any(), any());
    }

    @Test
    void revokeActiveForClientRequiresOwnership() {
        when(expertClientService.requireOwned(CLIENT_ID)).thenReturn(client(CLIENT_USER_ID));
        when(authorizationMapper.selectOne(any())).thenReturn(null);

        service.revokeActiveForClient(CLIENT_ID);

        verify(authorizationMapper, never()).update(any(), any());
    }

    private ClientAuthorization pendingAuth() {
        ClientAuthorization auth = new ClientAuthorization();
        auth.setId(99L);
        auth.setClientId(CLIENT_ID);
        auth.setExpertId(EXPERT_ID);
        auth.setScope(List.of("profile"));
        auth.setStatus(ClientAuthorization.STATUS_PENDING);
        auth.setExpiresAt(LocalDateTime.now().plusDays(1));
        return auth;
    }

    private ExpertClient client(Long linkedUserId) {
        ExpertClient client = new ExpertClient();
        client.setId(CLIENT_ID);
        client.setExpertId(EXPERT_ID);
        client.setLinkedUserId(linkedUserId);
        client.setStatus(ExpertClient.STATUS_ACTIVE);
        return client;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
