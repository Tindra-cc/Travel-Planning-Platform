package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.expert.dto.ExpertClientRequest;
import com.travel.modules.expert.dto.ExpertClientResponse;
import com.travel.modules.expert.entity.ClientAuthorization;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.expert.mapper.ClientAuthorizationMapper;
import com.travel.modules.expert.mapper.ExpertClientMapper;

@ExtendWith(MockitoExtension.class)
class ExpertClientServiceTest {

    private static final long EXPERT_ID = 7L;

    @Mock
    private ExpertClientMapper clientMapper;
    @Mock
    private ClientAuthorizationMapper authorizationMapper;
    @Mock
    private UserMapper userMapper;

    private ExpertClientService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(EXPERT_ID, "expert"));
        service = new ExpertClientService(clientMapper, authorizationMapper, userMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listOnlyReturnsOwnClientsWithAuthorizationSummary() {
        ExpertClient first = client(11L, EXPERT_ID);
        ExpertClient second = client(12L, EXPERT_ID);
        Page<ExpertClient> page = new Page<>(1, 20, 2);
        page.setRecords(List.of(first, second));
        when(clientMapper.selectPage(any(Page.class), any())).thenReturn(page);
        ClientAuthorization active = authorization(11L, ClientAuthorization.STATUS_ACTIVE);
        ClientAuthorization pending = authorization(12L, ClientAuthorization.STATUS_PENDING);
        when(authorizationMapper.selectList(any())).thenReturn(List.of(active, pending));

        PageResult<ExpertClientResponse> result = service.list(1, 20, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList()).extracting(ExpertClientResponse::getAuthorizationStatus)
                .containsExactly("active", "pending");
        assertThat(result.getList().get(0).getAuthorizedScopes()).containsExactly("profile");
        assertThat(result.getList().get(1).getAuthorizedAt()).isNull();
    }

    @Test
    void listDefaultsMissingAuthorizationToNone() {
        Page<ExpertClient> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(client(11L, EXPERT_ID)));
        when(clientMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(authorizationMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.list(1, 20, "active").getList().get(0).getAuthorizationStatus())
                .isEqualTo("none");
    }

    @Test
    void createTrimsFieldsAndStoresNullForBlank() {
        ExpertClientRequest request = new ExpertClientRequest();
        request.setName("  客户A ");
        request.setContact("   ");
        request.setNote(null);

        ExpertClientResponse response = service.create(request);

        ArgumentCaptor<ExpertClient> captor = ArgumentCaptor.forClass(ExpertClient.class);
        verify(clientMapper).insert(captor.capture());
        ExpertClient saved = captor.getValue();
        assertThat(saved.getExpertId()).isEqualTo(EXPERT_ID);
        assertThat(saved.getName()).isEqualTo("客户A");
        assertThat(saved.getContact()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ExpertClient.STATUS_ACTIVE);
        assertThat(response.getAuthorizationStatus()).isEqualTo("none");
    }

    @Test
    void createRejectsMissingLinkedUser() {
        ExpertClientRequest request = new ExpertClientRequest();
        request.setName("客户A");
        request.setLinkedUserId(99L);
        when(userMapper.selectById(99L)).thenReturn(null);

        assertBizCode(() -> service.create(request), ResultCode.UNPROCESSABLE);
        verify(clientMapper, never()).insert(any(ExpertClient.class));
    }

    @Test
    void requireOwnedRejectsMissingClient() {
        when(clientMapper.selectById(11L)).thenReturn(null);

        assertBizCode(() -> service.requireOwned(11L), ResultCode.NOT_FOUND);
    }

    @Test
    void requireOwnedRejectsForeignClient() {
        when(clientMapper.selectById(11L)).thenReturn(client(11L, EXPERT_ID + 1));

        assertBizCode(() -> service.requireOwned(11L), ResultCode.NOT_OWNER);
    }

    @Test
    void archiveSetsStatusInsteadOfDeleting() {
        when(clientMapper.selectById(11L)).thenReturn(client(11L, EXPERT_ID));

        service.archive(11L);

        ArgumentCaptor<ExpertClient> captor = ArgumentCaptor.forClass(ExpertClient.class);
        verify(clientMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpertClient.STATUS_ARCHIVED);
    }

    private ExpertClient client(long id, long expertId) {
        ExpertClient client = new ExpertClient();
        client.setId(id);
        client.setExpertId(expertId);
        client.setName("客户" + id);
        client.setStatus(ExpertClient.STATUS_ACTIVE);
        return client;
    }

    private ClientAuthorization authorization(long clientId, String status) {
        ClientAuthorization auth = new ClientAuthorization();
        auth.setClientId(clientId);
        auth.setStatus(status);
        auth.setScope(List.of("profile"));
        return auth;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
