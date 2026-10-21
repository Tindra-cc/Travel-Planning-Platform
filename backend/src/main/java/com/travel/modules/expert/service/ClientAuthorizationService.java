package com.travel.modules.expert.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.expert.dto.AuthorizationConfirmResponse;
import com.travel.modules.expert.dto.AuthorizeResponse;
import com.travel.modules.expert.entity.ClientAuthorization;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.expert.mapper.ClientAuthorizationMapper;

import lombok.RequiredArgsConstructor;

/**
 * 客户授权的完整状态机：达人发起 → 客户本人确认 → active；任一方可撤回。
 * 令牌只存哈希，确认时必须同时满足「令牌有效」与「登录用户 = 客户绑定账号」。
 */
@Service
@RequiredArgsConstructor
public class ClientAuthorizationService {

    public static final String SCOPE_PROFILE = "profile";
    public static final String SCOPE_GUIDEBOOKS = "guidebooks";

    private static final Set<String> ALLOWED_SCOPES = Set.of(SCOPE_PROFILE, SCOPE_GUIDEBOOKS);
    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientAuthorizationMapper authorizationMapper;
    private final ExpertClientService expertClientService;

    public AuthorizeResponse authorize(Long clientId, List<String> requestedScope) {
        ExpertClient client = expertClientService.requireOwned(clientId);
        if (client.getLinkedUserId() == null) {
            throw new BizException(ResultCode.UNPROCESSABLE, "客户尚未绑定平台账号，无法发起授权");
        }
        if (!ExpertClient.STATUS_ACTIVE.equals(client.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "客户已归档，无法发起授权");
        }
        List<String> scope = validateScope(requestedScope);

        // 同一客户只保留最新一条待确认授权，减少误点
        authorizationMapper.update(null, Wrappers.<ClientAuthorization>update()
                .eq("client_id", clientId)
                .eq("status", ClientAuthorization.STATUS_PENDING)
                .set("status", ClientAuthorization.STATUS_REVOKED)
                .set("revoked_at", LocalDateTime.now()));

        String rawToken = newToken();
        ClientAuthorization auth = new ClientAuthorization();
        auth.setClientId(clientId);
        auth.setExpertId(UserContext.getUserId());
        auth.setTokenHash(sha256(rawToken));
        auth.setScope(scope);
        auth.setStatus(ClientAuthorization.STATUS_PENDING);
        auth.setExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
        authorizationMapper.insert(auth);

        AuthorizeResponse resp = new AuthorizeResponse();
        resp.setAuthorizationId(String.valueOf(auth.getId()));
        resp.setConfirmToken(rawToken);
        resp.setExpiresAt(auth.getExpiresAt());
        resp.setScope(scope);
        return resp;
    }

    @Transactional
    public AuthorizationConfirmResponse confirm(String rawToken) {
        Long userId = UserContext.getUserId();
        ClientAuthorization auth = authorizationMapper.selectOne(
                Wrappers.<ClientAuthorization>lambdaQuery()
                        .eq(ClientAuthorization::getTokenHash, sha256(rawToken)));
        if (auth == null || !ClientAuthorization.STATUS_PENDING.equals(auth.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "授权请求不存在或已处理");
        }
        if (auth.getExpiresAt() != null && !auth.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT, "授权请求已过期");
        }
        ExpertClient client = expertClientService.findById(auth.getClientId());
        if (client == null || !userId.equals(client.getLinkedUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "当前账号不是该客户的绑定账号");
        }

        // 先撤回旧 active，满足部分唯一索引
        authorizationMapper.update(null, Wrappers.<ClientAuthorization>update()
                .eq("client_id", client.getId())
                .eq("status", ClientAuthorization.STATUS_ACTIVE)
                .set("status", ClientAuthorization.STATUS_REVOKED)
                .set("revoked_at", LocalDateTime.now()));

        LocalDateTime grantedAt = LocalDateTime.now();
        int rows = authorizationMapper.update(null, Wrappers.<ClientAuthorization>update()
                .eq("id", auth.getId())
                .eq("status", ClientAuthorization.STATUS_PENDING)
                .set("status", ClientAuthorization.STATUS_ACTIVE)
                .set("granted_at", grantedAt)
                .set("evidence", "user_confirmed:" + userId));
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT, "授权请求已被处理");
        }

        AuthorizationConfirmResponse resp = new AuthorizationConfirmResponse();
        resp.setAuthorizationId(String.valueOf(auth.getId()));
        resp.setClientId(String.valueOf(client.getId()));
        resp.setStatus(ClientAuthorization.STATUS_ACTIVE);
        resp.setScope(auth.getScope());
        resp.setGrantedAt(grantedAt);
        return resp;
    }

    /** 达人撤回客户当前的 active 授权；无 active 时幂等返回。 */
    public void revokeActiveForClient(Long clientId) {
        expertClientService.requireOwned(clientId);
        ClientAuthorization active = findActive(clientId);
        if (active != null) {
            revoke(active.getId());
        }
    }

    /** 达人本人或被绑定客户都可以撤回。 */
    public void revoke(Long authorizationId) {
        ClientAuthorization auth = authorizationMapper.selectById(authorizationId);
        if (auth == null) {
            throw new BizException(ResultCode.NOT_FOUND, "授权不存在");
        }
        Long userId = UserContext.getUserId();
        boolean expert = userId.equals(auth.getExpertId());
        boolean client = isLinkedClient(auth.getClientId(), userId);
        if (!expert && !client) {
            throw new BizException(ResultCode.NOT_OWNER, "无权撤回该授权");
        }
        if (ClientAuthorization.STATUS_REVOKED.equals(auth.getStatus())) {
            return;
        }
        authorizationMapper.update(null, Wrappers.<ClientAuthorization>update()
                .eq("id", authorizationId)
                .ne("status", ClientAuthorization.STATUS_REVOKED)
                .set("status", ClientAuthorization.STATUS_REVOKED)
                .set("revoked_at", LocalDateTime.now()));
    }

    /**
     * 每次访问都实时查询授权，撤回即时生效。
     * 归属错误 40301，授权缺失/过期 40300，便于前端区分「找客户」与「让客户授权」。
     */
    public ExpertClient requireAuthorized(Long clientId, String scope) {
        ExpertClient client = expertClientService.requireOwned(clientId);
        ClientAuthorization auth = findActive(clientId);
        if (auth == null || auth.getScope() == null || !auth.getScope().contains(scope)) {
            throw new BizException(ResultCode.FORBIDDEN, "客户未授权访问该数据");
        }
        return client;
    }

    public ClientAuthorization findActive(Long clientId) {
        return authorizationMapper.selectOne(
                Wrappers.<ClientAuthorization>lambdaQuery()
                        .eq(ClientAuthorization::getClientId, clientId)
                        .eq(ClientAuthorization::getStatus, ClientAuthorization.STATUS_ACTIVE));
    }

    private boolean isLinkedClient(Long clientId, Long userId) {
        ExpertClient client = expertClientService.findById(clientId);
        return client != null && userId.equals(client.getLinkedUserId());
    }

    private List<String> validateScope(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "scope 不能为空");
        }
        List<String> result = requested.stream().distinct().toList();
        for (String item : result) {
            if (!ALLOWED_SCOPES.contains(item)) {
                throw new BizException(ResultCode.PARAM_ERROR, "不支持的授权范围：" + item);
            }
        }
        return result;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
