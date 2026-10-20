package com.travel.modules.guidebook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.LinkValidateResponse;
import com.travel.modules.guidebook.dto.ResourceItemDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.service.GuidebookEditService;
import com.travel.modules.guidebook.service.LinkSignService;
import com.travel.modules.guidebook.service.ResourceService;

/** FR-8：资源搜索 → 绑定（版本 +1）→ 签名转链 → 批量校验。 */
@Tag("integration")
class ResourceLinkFlowIntegrationTest extends IntegrationFlowTestSupport {

    @Autowired
    private ResourceService resourceService;
    @Autowired
    private LinkSignService linkSignService;
    @Autowired
    private GuidebookEditService editService;

    @Test
    void bindsResourceAndSignsClick() {
        ResourceItemDto hotel = resourceService
                .search("hotel", "成都", null, null, 1, 10)
                .getList()
                .stream()
                .filter(item -> "r70011".equals(item.getResourceSlotId()))
                .findFirst()
                .orElseThrow();
        assertThat(hotel.getBookingLink()).startsWith("/api/link/click?rid=r70011&sig=");

        Guidebook guidebook = createGuidebook(Guidebook.STATUS_DRAFT, true);
        GuidebookEditResponse response = editService.bindResource(
                guidebook.getId(), "n-it-1", "r70011");
        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getChangeSummary()).contains("宽窄巷子");

        Guidebook refreshed = guidebookMapper.selectById(guidebook.getId());
        assertThat(refreshed.getStatus()).isEqualTo(Guidebook.STATUS_EDITING);
        assertThat(refreshed.getDays().get(0).getNodes().get(0).getResourceSlotId())
                .isEqualTo("r70011");
        assertThat(versionMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<com.travel.modules.guidebook.entity.GuidebookVersion>lambdaQuery()
                .eq(com.travel.modules.guidebook.entity.GuidebookVersion::getGuidebookId,
                        guidebook.getId())))
                .singleElement()
                .extracting(com.travel.modules.guidebook.entity.GuidebookVersion::getVersion)
                .isEqualTo(2);

        String signature = linkSignService.sign("r70011");
        String redirect = resourceService.resolveClick("r70011", signature);
        assertThat(redirect).startsWith("https://example.com/partner/hotel/chengdu-1");
        assertThat(redirect).contains("utm_source=travel-platform");

        assertThatThrownBy(() -> resourceService.resolveClick("r70011", signature + "x"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getResultCode())
                        .isEqualTo(ResultCode.NOT_OWNER));

        LinkValidateResponse validation = resourceService.validate(
                java.util.List.of("r70011", "r70014"));
        assertThat(validation.getResults()).extracting(LinkValidateResponse.Result::getResourceSlotId)
                .containsExactly("r70011", "r70014");
        assertThat(validation.getResults().get(0).isValid()).isTrue();
        assertThat(validation.getResults().get(1).isValid()).isFalse();
        assertThat(validation.getResults().get(1).getReason()).isEqualTo("sold_out");
    }

    @Test
    void rejectsBindingSoldOutResource() {
        Guidebook guidebook = createGuidebook(Guidebook.STATUS_DRAFT, true);
        assertThatThrownBy(() -> editService.bindResource(
                guidebook.getId(), "n-it-1", "r70014"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getResultCode())
                        .isEqualTo(ResultCode.CONFLICT));
    }
}
