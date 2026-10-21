package com.travel.modules.expert.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.expert.dto.ExpertClientRequest;
import com.travel.modules.expert.dto.ExpertClientResponse;
import com.travel.modules.expert.entity.ClientAuthorization;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.expert.mapper.ClientAuthorizationMapper;
import com.travel.modules.expert.mapper.ExpertClientMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpertClientService {

    private final ExpertClientMapper clientMapper;
    private final ClientAuthorizationMapper authorizationMapper;
    private final UserMapper userMapper;

    public PageResult<ExpertClientResponse> list(int page, int pageSize, String status) {
        Page<ExpertClient> result = clientMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<ExpertClient>lambdaQuery()
                        .eq(ExpertClient::getExpertId, UserContext.getUserId())
                        .eq(status != null && !status.isBlank(), ExpertClient::getStatus, status)
                        .orderByDesc(ExpertClient::getId));
        List<ExpertClientResponse> items = result.getRecords().stream().map(this::toResponse).toList();
        fillAuthorizationSummary(result.getRecords(), items);
        return PageResult.of(items, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public ExpertClientResponse create(ExpertClientRequest req) {
        if (req.getLinkedUserId() != null && userMapper.selectById(req.getLinkedUserId()) == null) {
            throw new BizException(ResultCode.UNPROCESSABLE, "绑定的用户不存在");
        }
        ExpertClient client = new ExpertClient();
        client.setExpertId(UserContext.getUserId());
        client.setName(req.getName().trim());
        client.setContact(trimToNull(req.getContact()));
        client.setNote(trimToNull(req.getNote()));
        client.setLinkedUserId(req.getLinkedUserId());
        client.setStatus(ExpertClient.STATUS_ACTIVE);
        clientMapper.insert(client);
        ExpertClientResponse response = toResponse(client);
        response.setAuthorizationStatus("none");
        return response;
    }

    /** 归属校验：不存在 40400，属于别人 40301。 */
    public ExpertClient requireOwned(Long clientId) {
        ExpertClient client = clientMapper.selectById(clientId);
        if (client == null) {
            throw new BizException(ResultCode.NOT_FOUND, "客户不存在");
        }
        if (!client.getExpertId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该客户");
        }
        return client;
    }

    /** 详情视图：附当前授权状态，数据源仍是一次授权查询而非冗余字段。 */
    public ExpertClientResponse toDetailResponse(ExpertClient client) {
        ExpertClientResponse response = toResponse(client);
        fillAuthorizationSummary(List.of(client), List.of(response));
        return response;
    }

    public ExpertClient findById(Long clientId) {
        return clientMapper.selectById(clientId);
    }

    public void archive(Long clientId) {
        ExpertClient client = requireOwned(clientId);
        if (!ExpertClient.STATUS_ARCHIVED.equals(client.getStatus())) {
            client.setStatus(ExpertClient.STATUS_ARCHIVED);
            clientMapper.updateById(client);
        }
    }

    public ExpertClientResponse toResponse(ExpertClient client) {
        ExpertClientResponse resp = new ExpertClientResponse();
        resp.setClientId(String.valueOf(client.getId()));
        resp.setName(client.getName());
        resp.setContact(client.getContact());
        resp.setNote(client.getNote());
        resp.setLinkedUserId(client.getLinkedUserId() == null
                ? null : String.valueOf(client.getLinkedUserId()));
        resp.setStatus(client.getStatus());
        resp.setCreatedAt(client.getCreateTime());
        return resp;
    }

    /** 一次查询补全整页的授权状态，避免逐行查询。 */
    private void fillAuthorizationSummary(List<ExpertClient> clients,
            List<ExpertClientResponse> responses) {
        if (clients.isEmpty()) {
            return;
        }
        List<Long> clientIds = clients.stream().map(ExpertClient::getId).toList();
        List<ClientAuthorization> authorizations = authorizationMapper.selectList(
                Wrappers.<ClientAuthorization>lambdaQuery()
                        .in(ClientAuthorization::getClientId, clientIds)
                        .in(ClientAuthorization::getStatus,
                                ClientAuthorization.STATUS_ACTIVE,
                                ClientAuthorization.STATUS_PENDING)
                        .orderByDesc(ClientAuthorization::getId));
        Map<Long, ClientAuthorization> active = authorizations.stream()
                .filter(auth -> ClientAuthorization.STATUS_ACTIVE.equals(auth.getStatus()))
                .collect(Collectors.toMap(ClientAuthorization::getClientId, Function.identity(),
                        (first, second) -> first));
        Map<Long, ClientAuthorization> pending = authorizations.stream()
                .filter(auth -> ClientAuthorization.STATUS_PENDING.equals(auth.getStatus()))
                .collect(Collectors.toMap(ClientAuthorization::getClientId, Function.identity(),
                        (first, second) -> first));
        for (int i = 0; i < clients.size(); i++) {
            Long clientId = clients.get(i).getId();
            ExpertClientResponse response = responses.get(i);
            ClientAuthorization auth = active.get(clientId);
            if (auth != null) {
                response.setAuthorizationStatus(ClientAuthorization.STATUS_ACTIVE);
                response.setAuthorizedScopes(auth.getScope());
                response.setAuthorizedAt(auth.getGrantedAt());
            } else if (pending.containsKey(clientId)) {
                response.setAuthorizationStatus(ClientAuthorization.STATUS_PENDING);
            } else {
                response.setAuthorizationStatus("none");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
