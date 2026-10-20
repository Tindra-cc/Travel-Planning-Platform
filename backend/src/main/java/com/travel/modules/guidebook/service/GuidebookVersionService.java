package com.travel.modules.guidebook.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookVersionMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuidebookVersionService {

    private static final int SUMMARY_MAX = 500;
    private final GuidebookVersionMapper versionMapper;

    /**
     * 追加一份快照
     * (guidebook_id, version) 已存在时直接跳过，保证流水线重复执行安全
     */
    public void append(Guidebook guidebook , String changeType, String changeSummary, Long createdBy){

        Long exists = versionMapper.selectCount(new LambdaQueryWrapper<GuidebookVersion>()
                        .eq(GuidebookVersion :: getGuidebookId, guidebook.getId())
                        .eq(GuidebookVersion :: getVersion, guidebook.getVersion()));
        if (exists != null && exists > 0) {
            return;
        }


        GuidebookVersion snapshot = new GuidebookVersion();
        snapshot.setGuidebookId(guidebook.getId());
        snapshot.setVersion(guidebook.getVersion());
        snapshot.setTitle(guidebook.getTitle());
        snapshot.setDays(guidebook.getDays());
        snapshot.setBudgetBreakdown(guidebook.getBudgetBreakdown());
        snapshot.setPackingList(guidebook.getPackingList());
        snapshot.setCulturalTips(guidebook.getCulturalTips());
        snapshot.setPitfallTips(guidebook.getPitfallTips());
        snapshot.setChangeType(changeType);
        snapshot.setChangeSummary(truncate(changeSummary));
        snapshot.setCreatedBy(createdBy);
    
    try{
        versionMapper.insert(snapshot);
    }catch(DuplicateKeyException ignored){
            //两个线程同时写同一版本时，唯一约束兜底；视为已经写入
    }
    }

    //截断：边界处理
    private String truncate(String summary) {
        if (summary == null) {
            return "";
        }
        return  summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX);
    }
}
