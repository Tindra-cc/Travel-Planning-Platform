package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.AdminGuard;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.dto.DataSourceConfigItem;
import com.travel.modules.guidebook.dto.UpdateDataSourceRequest;
import com.travel.modules.guidebook.service.AdminDataService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class AdminDataControllerTest {

    @Mock
    private AdminGuard adminGuard;
    @Mock
    private AdminDataService adminDataService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new AdminDataController(adminGuard, adminDataService));
    }

    @Test
    void listsDataSources() throws Exception {
        when(adminDataService.listSources()).thenReturn(List.of(
                new DataSourceConfigItem("weather", true, 10, 1800, Map.of())));

        mvc.perform(get("/api/admin/data-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceId").value("weather"))
                .andExpect(jsonPath("$.data[0].priority").value(10));
    }

    @Test
    void updatesDataSource() throws Exception {
        when(adminDataService.updateSource(any(), any())).thenReturn(
                new DataSourceConfigItem("weather", false, 5, 600, Map.of()));

        mvc.perform(put("/api/admin/data-sources/weather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"priority\":5,\"cacheTtlSeconds\":600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.priority").value(5));
    }

    @Test
    void rejectsNonAdmin() throws Exception {
        doThrow(new BizException(ResultCode.FORBIDDEN, "需要管理员权限"))
                .when(adminGuard).requireAdmin();

        mvc.perform(get("/api/admin/data-sources"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalWeatherQueryReturnsBlock() throws Exception {
        when(adminDataService.weather("东京", "2026-11-01"))
                .thenReturn(DataBlock.ok("weather", "晴", Map.of(), "now"));

        mvc.perform(get("/api/admin/data/weather")
                        .param("destination", "东京")
                        .param("date", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("晴"));

        verify(adminGuard).requireAdmin();
    }
}
