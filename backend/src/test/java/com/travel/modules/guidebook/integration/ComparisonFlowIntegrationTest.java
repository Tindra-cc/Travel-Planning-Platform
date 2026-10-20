package com.travel.modules.guidebook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.travel.modules.guidebook.compare.ComparisonService;
import com.travel.modules.guidebook.compare.MixService;
import com.travel.modules.guidebook.dto.CompareRequest;
import com.travel.modules.guidebook.dto.CompareResponse;
import com.travel.modules.guidebook.dto.ComparisonDetailResponse;
import com.travel.modules.guidebook.dto.MixRequest;
import com.travel.modules.guidebook.dto.MixResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;

/** FR-4：一次对比只扣一次配额 → 顺序生成多套 → 混搭（无 AI 时降级保留 picks）。 */
@Tag("integration")
class ComparisonFlowIntegrationTest extends IntegrationFlowTestSupport {

    @Autowired
    private ComparisonService comparisonService;
    @Autowired
    private MixService mixService;

    @Test
    void generatesVariantsAndMixesWithTemplateFallback() throws Exception {
        Guidebook base = createGuidebook(Guidebook.STATUS_DRAFT, true);
        CompareRequest request = new CompareRequest(base.getId(), List.of("budget", "balanced"));

        CompareResponse started = comparisonService.start(request);
        assertThat(started.getComparisonId()).isNotNull();
        assertThat(started.getGuidebooks()).hasSize(2)
                .extracting(CompareResponse.CompareGuidebookItem::getVariant)
                .containsExactly("budget", "balanced");

        GuidebookComparison comparison = awaitComparison(started.getComparisonId());
        assertThat(comparison.getStatus()).isEqualTo(GuidebookComparison.STATUS_COMPLETED);
        assertThat(userMapper.selectById(userId).getDailyGenerateUsed()).isEqualTo(1);

        ComparisonDetailResponse detail = comparisonService.detail(started.getComparisonId());
        assertThat(detail.getVariants()).hasSize(2);
        assertThat(detail.getVariants()).allSatisfy(variant -> {
            assertThat(variant.getStatus()).isEqualTo(Guidebook.STATUS_DRAFT);
            assertThat(variant.getDayCount()).isPositive();
        });

        Long budgetId = detail.getVariants().get(0).getGuidebookId();
        Guidebook budget = guidebookMapper.selectById(budgetId);
        String nodeId = budget.getDays().get(0).getNodes().get(0).getNodeId();

        MixRequest mixRequest = new MixRequest(
                List.of(new MixRequest.Pick(budgetId, nodeId)));
        MixResponse mixed = mixService.mix(started.getComparisonId(), mixRequest);
        assertThat(mixed.getStatus()).isEqualTo("draft");
        assertThat(mixed.getGuidebookId()).isNotNull();

        Guidebook mixedGuidebook = guidebookMapper.selectById(mixed.getGuidebookId());
        assertThat(mixedGuidebook.getVariant()).isEqualTo("mixed");
        assertThat(mixedGuidebook.getSessionId()).isNull();
        assertThat(mixedGuidebook.getComparisonId()).isEqualTo(started.getComparisonId());
        assertThat(mixedGuidebook.getDays()).isNotEmpty();
        // 无 AI key：混搭重平衡失败，回退到所选节点并标注降级
        assertThat(mixedGuidebook.getDegradedMode()).isTrue();
        assertThat(mixedGuidebook.getDegradationReasons()).contains("mix_rebalance_failed");
        assertThat(versionMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<com.travel.modules.guidebook.entity.GuidebookVersion>lambdaQuery()
                .eq(com.travel.modules.guidebook.entity.GuidebookVersion::getGuidebookId,
                        mixedGuidebook.getId())))
                .isEqualTo(1);
    }

    private GuidebookComparison awaitComparison(Long comparisonId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(180);
        GuidebookComparison comparison = comparisonMapper.selectById(comparisonId);
        while (System.currentTimeMillis() < deadline && comparison != null
                && GuidebookComparison.STATUS_PROCESSING.equals(comparison.getStatus())) {
            Thread.sleep(500);
            comparison = comparisonMapper.selectById(comparisonId);
        }
        assertThat(comparison).isNotNull();
        assertThat(comparison.getStatus()).isNotEqualTo(GuidebookComparison.STATUS_PROCESSING);
        return comparison;
    }
}
