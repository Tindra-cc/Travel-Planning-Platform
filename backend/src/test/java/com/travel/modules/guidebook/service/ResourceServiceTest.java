package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.LinkValidateResponse;
import com.travel.modules.guidebook.entity.ResourceSlot;
import com.travel.modules.guidebook.mapper.ResourceSlotMapper;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceSlotMapper resourceSlotMapper;

    private final LinkSignService signService = new LinkSignService("test-secret");

    private ResourceService service() {
        return new ResourceService(resourceSlotMapper, signService);
    }

    @Test
    void resolveClickRedirectsWithAffiliateParams() {
        ResourceSlot slot = slot("r70001", "available");
        slot.setAffiliateParams(Map.of("utm_source", "travel-platform", "aid", "demo"));
        when(resourceSlotMapper.selectById("r70001")).thenReturn(slot);

        String url = service().resolveClick("r70001", signService.sign("r70001"));

        assertThat(url).startsWith("https://example.com/partner/hotel?");
        assertThat(url).contains("utm_source=travel-platform", "aid=demo");
    }

    @Test
    void resolveClickRejectsBadSignature() {
        when(resourceSlotMapper.selectById("r70001")).thenReturn(slot("r70001", "available"));

        assertThatThrownBy(() -> service().resolveClick("r70001", "deadbeef"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.NOT_OWNER);
    }

    @Test
    void resolveClickRejectsSoldOutResource() {
        when(resourceSlotMapper.selectById("r70001")).thenReturn(slot("r70001", "sold_out"));

        assertThatThrownBy(() -> service().resolveClick("r70001", signService.sign("r70001")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.CONFLICT);
    }

    @Test
    void validateReportsFallbackSupplierForSoldOut() {
        ResourceSlot soldOut = slot("r70001", "sold_out");
        when(resourceSlotMapper.selectBatchIds(any())).thenReturn(List.of(soldOut));
        when(resourceSlotMapper.selectList(any())).thenReturn(List.of(slot("r70002", "available")));

        LinkValidateResponse response = service().validate(List.of("r70001"));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).isValid()).isFalse();
        assertThat(response.getResults().get(0).getReason()).isEqualTo("sold_out");
        assertThat(response.getResults().get(0).getFallbackSupplier()).isEqualTo("演示供应商");
    }

    @Test
    void validateReportsStaleResource() {
        ResourceSlot stale = slot("r70001", "available");
        stale.setFreshness(LocalDateTime.now().minusDays(3));
        when(resourceSlotMapper.selectBatchIds(any())).thenReturn(List.of(stale));
        when(resourceSlotMapper.selectList(any())).thenReturn(List.of());

        LinkValidateResponse response = service().validate(List.of("r70001"));

        assertThat(response.getResults().get(0).getReason()).isEqualTo("stale");
    }

    @Test
    void enrichNodesSetsResourceOnlyForBoundNodes() {
        ResourceSlot slot = slot("r70001", "available");
        when(resourceSlotMapper.selectBatchIds(any())).thenReturn(List.of(slot));
        GuidebookNodeDto bound = node("n1", "r70001");
        GuidebookNodeDto unbound = node("n2", null);
        List<GuidebookDayDto> days = List.of(new GuidebookDayDto(1, List.of(bound, unbound)));

        service().enrichNodes(days);

        assertThat(bound.getResource().getResourceSlotId()).isEqualTo("r70001");
        assertThat(bound.getResource().getBookingLink()).contains("sig=");
        assertThat(unbound.getResource()).isNull();
    }

    @Test
    void rejectsUnknownResourceType() {
        assertThatThrownBy(() -> service().search("spa", "东京", null, null, 1, 20))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.PARAM_ERROR);
    }

    private ResourceSlot slot(String id, String availability) {
        ResourceSlot slot = new ResourceSlot();
        slot.setId(id);
        slot.setType("hotel");
        slot.setDestination("东京");
        slot.setSupplier("演示供应商");
        slot.setPriceMin(new BigDecimal("800"));
        slot.setPriceMax(new BigDecimal("1200"));
        slot.setCurrency("CNY");
        slot.setAvailability(availability);
        slot.setRefundPolicy("入住前 7 天可取消");
        slot.setTargetUrl("https://example.com/partner/hotel");
        slot.setFreshness(LocalDateTime.now());
        slot.setEnabled(true);
        slot.setSortWeight(100);
        return slot;
    }

    private GuidebookNodeDto node(String id, String resourceSlotId) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setResourceSlotId(resourceSlotId);
        return node;
    }
}
