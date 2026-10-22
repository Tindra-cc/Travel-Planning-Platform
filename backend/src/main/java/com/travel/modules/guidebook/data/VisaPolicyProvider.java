package com.travel.modules.guidebook.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.entity.VisaPolicy;
import com.travel.modules.guidebook.mapper.VisaPolicyMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 签证政策：读人工维护的 t_visa_policy，按目的地模糊匹配中国护照。
 * 没有记录就明确标注 unavailable，不用通用知识替代。
 */
@Slf4j
@Component
public class VisaPolicyProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String NATIONALITY = "CN";

    private final VisaPolicyMapper visaPolicyMapper;

    public VisaPolicyProvider(VisaPolicyMapper visaPolicyMapper) {
        this.visaPolicyMapper = visaPolicyMapper;
    }

    @Override
    public String source() {
        return "visa";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        String destination = intent == null ? null : intent.getDestination();
        if (!StringUtils.hasText(destination)) {
            return Optional.empty();
        }
        if (DomesticDestination.isDomestic(destination)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("destination", destination);
            data.put("nationality", NATIONALITY);
            data.put("policyType", "domestic");
            data.put("policySummary", "国内旅行，无需办理签证；请携带有效身份证件。");
            return Optional.of(DataBlock.ok(source(),
                    "国内旅行，无需签证；请携带有效身份证件", data,
                    LocalDateTime.now().format(TS)));
        }
        try {
            List<VisaPolicy> policies = visaPolicyMapper.selectList(
                    new LambdaQueryWrapper<VisaPolicy>()
                            .eq(VisaPolicy::getNationality, NATIONALITY));
            Optional<VisaPolicy> matched = policies.stream()
                    .filter(p -> StringUtils.hasText(p.getDestination()))
                    .filter(p -> destination.contains(p.getDestination())
                            || p.getDestination().contains(destination))
                    .findFirst();
            if (matched.isEmpty()) {
                return Optional.of(DemoTravelData.visaFallback(destination, null));
            }
            VisaPolicy policy = matched.get();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("destination", policy.getDestination());
            data.put("nationality", policy.getNationality());
            data.put("policySummary", policy.getPolicySummary());
            data.put("entryRequirements", policy.getEntryRequirements());
            data.put("sourceName", policy.getSourceName());
            data.put("sourceUrl", policy.getSourceUrl());
            data.put("lastVerifiedAt", policy.getLastVerifiedAt() == null
                    ? null : policy.getLastVerifiedAt().format(DATE));
            String summary = (policy.getPolicySummary() == null ? "签证政策待核实" : policy.getPolicySummary())
                    + "（来源：" + (policy.getSourceName() == null ? "人工维护" : policy.getSourceName())
                    + "，更新于 " + (policy.getLastVerifiedAt() == null
                            ? "未知" : policy.getLastVerifiedAt().format(DATE)) + "）";
            return Optional.of(DataBlock.ok(source(), summary, data,
                    LocalDateTime.now().format(TS)));
        } catch (Exception e) {
            log.info("签证政策读取失败 destination={}: {}", destination, e.getMessage());
            return Optional.of(DemoTravelData.visaFallback(destination, null));
        }
    }
}
