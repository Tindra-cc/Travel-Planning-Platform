package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.PageResult;
import com.travel.modules.expert.dto.ClientAccessLogResponse;
import com.travel.modules.expert.entity.ClientAccessLog;
import com.travel.modules.expert.mapper.ClientAccessLogMapper;

@ExtendWith(MockitoExtension.class)
class ClientAccessAuditServiceTest {

    @Mock
    private ClientAccessLogMapper logMapper;

    @Test
    void recordStoresOnlyAccessMetadata() {
        ClientAccessAuditService service = new ClientAccessAuditService(logMapper);

        service.record(7L, 11L, ClientAccessAuditService.ACTION_VIEW_CLIENT, null,
                "expert_client", 11L, "127.0.0.1");

        ArgumentCaptor<ClientAccessLog> captor = ArgumentCaptor.forClass(ClientAccessLog.class);
        verify(logMapper).insert(captor.capture());
        ClientAccessLog saved = captor.getValue();
        assertThat(saved.getExpertId()).isEqualTo(7L);
        assertThat(saved.getClientId()).isEqualTo(11L);
        assertThat(saved.getAction()).isEqualTo("view_client");
        assertThat(saved.getResourceType()).isEqualTo("expert_client");
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void listMapsPageRecords() {
        ClientAccessAuditService service = new ClientAccessAuditService(logMapper);
        ClientAccessLog log = new ClientAccessLog();
        log.setId(3L);
        log.setAction("view_client");
        log.setResourceType("expert_client");
        log.setResourceId(11L);
        log.setIp("10.0.0.1");
        Page<ClientAccessLog> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(log));
        when(logMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<ClientAccessLogResponse> result = service.list(11L, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        ClientAccessLogResponse item = result.getList().get(0);
        assertThat(item.getLogId()).isEqualTo("3");
        assertThat(item.getResourceId()).isEqualTo("11");
        assertThat(item.getAction()).isEqualTo("view_client");
    }
}
