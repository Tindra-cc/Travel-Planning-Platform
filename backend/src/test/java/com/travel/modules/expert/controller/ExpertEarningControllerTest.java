package com.travel.modules.expert.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.modules.expert.dto.EarningItem;
import com.travel.modules.expert.dto.EarningsPageResponse;
import com.travel.modules.expert.service.ExpertEarningService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ExpertEarningControllerTest {

    @Mock
    private ExpertEarningService earningService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ExpertEarningController(earningService));
    }

    @Test
    void returnsEmptyPlaceholderPage() throws Exception {
        EarningsPageResponse response = new EarningsPageResponse();
        response.setList(List.of());
        response.setPage(1);
        response.setPageSize(20);
        response.setTotal(0);
        response.setSettlementAvailable(false);
        when(earningService.list(1, 20)).thenReturn(response);

        mvc.perform(get("/api/expert/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.settlementAvailable").value(false));
    }

    @Test
    void returnsEarningItems() throws Exception {
        EarningsPageResponse response = new EarningsPageResponse();
        response.setList(List.of(new EarningItem("1", "50", "成都四日", 1990, "pending", null)));
        response.setPage(1);
        response.setPageSize(20);
        response.setTotal(1);
        response.setSettlementAvailable(false);
        when(earningService.list(1, 20)).thenReturn(response);

        mvc.perform(get("/api/expert/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].guideTitle").value("成都四日"))
                .andExpect(jsonPath("$.data.list[0].amountCents").value(1990));
    }
}
