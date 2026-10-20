package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.PageResult;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.ResourceItemDto;
import com.travel.modules.guidebook.service.GuidebookEditService;
import com.travel.modules.guidebook.service.ResourceService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ResourceControllerTest {

    @Mock
    private ResourceService resourceService;
    @Mock
    private GuidebookEditService editService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ResourceController(resourceService, editService));
    }

    @Test
    void searchesResources() throws Exception {
        ResourceItemDto item = new ResourceItemDto("r70001", "hotel", "Booking 演示",
                new ResourceItemDto.PriceRange(new BigDecimal("800"), new BigDecimal("1200"),
                        "CNY"),
                "available", "可取消", "/api/link/click?rid=r70001&sig=abc", null);
        when(resourceService.search(eq("hotel"), eq("东京"), any(), any(), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(item), 1, 20, 1));

        mvc.perform(get("/api/resources")
                        .param("type", "hotel")
                        .param("destination", "东京"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].resourceSlotId").value("r70001"))
                .andExpect(jsonPath("$.data.list[0].priceRange.currency").value("CNY"))
                .andExpect(jsonPath("$.data.list[0].bookingLink").value(
                        "/api/link/click?rid=r70001&sig=abc"));
    }

    @Test
    void bindsResourceToNode() throws Exception {
        when(editService.bindResource(5L, "d1-1", "r70001"))
                .thenReturn(new GuidebookEditResponse(5L, 3, "绑定资源：浅草寺", List.of()));

        mvc.perform(post("/api/guidebooks/5/nodes/d1-1/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceSlotId\":\"r70001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.changeSummary").value("绑定资源：浅草寺"));
    }
}
