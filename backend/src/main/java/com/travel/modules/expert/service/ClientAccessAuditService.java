package com.travel.modules.expert.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.PageResult;
import com.travel.modules.expert.dto.ClientAccessLogResponse;
import com.travel.modules.expert.entity.ClientAccessLog;
import com.travel.modules.expert.mapper.ClientAccessLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 访问事实日志：只记元数据，不复制客户内容。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAccessAuditService {

    public static final String ACTION_VIEW_CLIENT = "view_client";
    public static final String ACTION_READ_PROFILE = "read_profile";
    public static final String ACTION_GENERATE_GUIDEBOOK = "generate_guidebook";
    public static final String ACTION_LIST_GUIDEBOOKS = "list_guidebooks";

    private final ClientAccessLogMapper logMapper;

    /** 审计失败只告警：日志表故障不能让已入队/已完成的操作变成 500。 */
    public void record(Long expertId, Long clientId, String action, String scope,
            String resourceType, Long resourceId, String ip) {
        try {
            ClientAccessLog log = new ClientAccessLog();
            log.setExpertId(expertId);
            log.setClientId(clientId);
            log.setAction(action);
            log.setScope(scope);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setIp(ip);
            logMapper.insert(log);
        } catch (Exception e) {
            log.warn("写入客户访问审计失败 expertId={} clientId={} action={}: {}",
                    expertId, clientId, action, e.getMessage());
        }
    }

    public PageResult<ClientAccessLogResponse> list(Long clientId, int page, int pageSize) {
        Page<ClientAccessLog> result = logMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<ClientAccessLog>lambdaQuery()
                        .eq(ClientAccessLog::getClientId, clientId)
                        .orderByDesc(ClientAccessLog::getId));
        return PageResult.of(result.getRecords().stream().map(this::toResponse).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    private ClientAccessLogResponse toResponse(ClientAccessLog log) {
        ClientAccessLogResponse resp = new ClientAccessLogResponse();
        resp.setLogId(String.valueOf(log.getId()));
        resp.setAction(log.getAction());
        resp.setScope(log.getScope());
        resp.setResourceType(log.getResourceType());
        resp.setResourceId(log.getResourceId() == null ? null : String.valueOf(log.getResourceId()));
        resp.setIp(log.getIp());
        resp.setCreatedAt(log.getCreateTime());
        return resp;
    }
}
