package com.travel.modules.guidebook.offline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.GuidebookVersionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 从版本快照组装离线包内容。快照是唯一事实源；只有快照缺失时才回退当前行。
 */
@Service
@RequiredArgsConstructor
public class OfflinePackContentBuilder {

    private static final String DISCLAIMER =
            "攻略由 AI 辅助生成，请出行前核实开放时间、票价与天气；离线内容以生成时版本为准。";
    private static final String EMERGENCY_NOTE =
            "紧急电话仅供参考，请以当地官方信息为准；出境游请自行记录使领馆联系方式。";

    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionMapper versionMapper;
    private final StaticMapProvider staticMapProvider;

    public OfflinePackContent build(Long guidebookId, Integer version, boolean includeMaps) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        GuidebookVersion snapshot = versionMapper.selectOne(
                new LambdaQueryWrapper<GuidebookVersion>()
                        .eq(GuidebookVersion::getGuidebookId, guidebookId)
                        .eq(GuidebookVersion::getVersion, version)
                        .last("LIMIT 1"));

        String title = snapshot != null ? snapshot.getTitle() : guidebook.getTitle();
        List<GuidebookDayDto> days = snapshot != null ? snapshot.getDays() : guidebook.getDays();

        boolean mapUnavailable = false;
        List<OfflinePackContent.DayContent> dayContents = new ArrayList<>();
        if (days != null) {
            for (GuidebookDayDto day : days) {
                if (day == null) {
                    continue;
                }
                List<OfflinePackContent.NodeContent> nodes = new ArrayList<>();
                if (day.getNodes() != null) {
                    for (GuidebookNodeDto node : day.getNodes()) {
                        if (node != null) {
                            nodes.add(toNode(node));
                        }
                    }
                }

                byte[] mapImage = null;
                String mapType = null;
                if (includeMaps) {
                    Optional<Centroid> centroid = centroid(nodes);
                    if (centroid.isPresent()) {
                        Optional<StaticMapProvider.MapImage> image =
                                staticMapProvider.fetch(centroid.get().lat(), centroid.get().lng());
                        if (image.isPresent()) {
                            mapImage = image.get().bytes();
                            mapType = image.get().type();
                        } else {
                            mapUnavailable = true;
                        }
                    }
                }

                dayContents.add(OfflinePackContent.DayContent.builder()
                        .day(day.getDay())
                        .nodes(nodes)
                        .mapImage(mapImage)
                        .mapImageType(mapType)
                        .build());
            }
        }

        return OfflinePackContent.builder()
                .guidebookId(guidebookId)
                .version(version)
                .title(StringUtils.hasText(title) ? title : "旅行攻略")
                .intent(guidebook.getIntent())
                .days(dayContents)
                .budget(snapshot != null ? snapshot.getBudgetBreakdown() : guidebook.getBudgetBreakdown())
                .packingList(snapshot != null ? snapshot.getPackingList() : guidebook.getPackingList())
                .culturalTips(snapshot != null ? snapshot.getCulturalTips() : guidebook.getCulturalTips())
                .pitfallTips(snapshot != null ? snapshot.getPitfallTips() : guidebook.getPitfallTips())
                .emergencyContacts(emergencyContacts())
                .emergencyNote(EMERGENCY_NOTE)
                .disclaimer(DISCLAIMER)
                .mapUnavailable(mapUnavailable)
                .build();
    }

    private OfflinePackContent.NodeContent toNode(GuidebookNodeDto node) {
        return OfflinePackContent.NodeContent.builder()
                .start(node.getTimeSlot() == null ? null : node.getTimeSlot().getStart())
                .end(node.getTimeSlot() == null ? null : node.getTimeSlot().getEnd())
                .type(node.getType())
                .placeName(node.getPlace() == null ? null : node.getPlace().getName())
                .lat(node.getPlace() == null ? null : node.getPlace().getLat())
                .lng(node.getPlace() == null ? null : node.getPlace().getLng())
                .note(node.getNote())
                .source(node.getSource())
                .dataFreshness(node.getDataFreshness())
                .build();
    }

    private Optional<Centroid> centroid(List<OfflinePackContent.NodeContent> nodes) {
        double sumLat = 0;
        double sumLng = 0;
        int count = 0;
        for (OfflinePackContent.NodeContent node : nodes) {
            if (node.getLat() != null && node.getLng() != null) {
                sumLat += node.getLat();
                sumLng += node.getLng();
                count++;
            }
        }
        return count == 0 ? Optional.empty() : Optional.of(new Centroid(sumLat / count, sumLng / count));
    }

    private List<OfflinePackContent.Contact> emergencyContacts() {
        return List.of(
                new OfflinePackContent.Contact("报警", "110"),
                new OfflinePackContent.Contact("急救", "120"),
                new OfflinePackContent.Contact("火警", "119"));
    }

    private record Centroid(double lat, double lng) {
    }
}
