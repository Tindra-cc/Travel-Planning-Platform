package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.LinkValidateResponse;
import com.travel.modules.guidebook.service.ResourceService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class LinkControllerTest {

    @Mock
    private ResourceService resourceService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new LinkController(resourceService));
    }

    @Test
    void clickRedirectsToPartner() throws Exception {
        when(resourceService.resolveClick("r70001", "sig"))
                .thenReturn("https://example.com/partner/hotel?utm_source=travel-platform");

        mvc.perform(get("/api/link/click").param("rid", "r70001").param("sig", "sig"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://example.com/partner/hotel?utm_source=travel-platform"));
    }

    @Test
    void clickRejectsInvalidSignature() throws Exception {
        when(resourceService.resolveClick(any(), any()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "转链签名无效"));

        mvc.perform(get("/api/link/click").param("rid", "r70001").param("sig", "bad"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validateReturnsPerResourceResults() throws Exception {
        when(resourceService.validate(any())).thenReturn(new LinkValidateResponse(List.of(
                new LinkValidateResponse.Result("r70001", true, null, null),
                new LinkValidateResponse.Result("r70002", false, "sold_out", "携程 演示"))));

        mvc.perform(post("/api/link/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceSlotIds\":[\"r70001\",\"r70002\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].valid").value(true))
                .andExpect(jsonPath("$.data.results[1].reason").value("sold_out"))
                .andExpect(jsonPath("$.data.results[1].fallbackSupplier").value("携程 演示"));
    }
}
