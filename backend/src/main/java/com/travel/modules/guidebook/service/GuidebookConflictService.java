package com.travel.modules.guidebook.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.GuidebookValidateResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.edit.ConflictDetector;
import com.travel.modules.guidebook.edit.ItineraryRepairSupport;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuidebookConflictService {

    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionService versionService;
    private final ConflictDetector conflictDetector = new ConflictDetector();


    /**
     * 只读操作：不写版本 不改变状态 但必须校验属性，避免越权读取他人行程
     * @param guidebookId
     * @return
     */
    public GuidebookValidateResponse validate(Long guidebookId){
        Guidebook guidebook = requireOwned(guidebookId);
        if (Guidebook.STATUS_GENERATING.equals(guidebook.getStatus()) 
            || Guidebook.STATUS_FAILED.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,"攻略尚未生成完成，暂不能校验");
        }
        List<NodeConflictDto> conflicts = detect(guidebook);

        return  new GuidebookValidateResponse(conflicts.isEmpty(),conflicts);
    }

    /**
     * 一键修复可自动处理的冲突：顺延同日重叠时段、把预算上限压回意图预算。
     * 营业时间不匹配无法靠顺延解决，仍会出现在返回的冲突列表里。
     *
     * @param guidebookId 攻略 ID
     * @return 修复后的冲突检测结果
     */
    @Transactional
    public GuidebookValidateResponse repair(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        if (Guidebook.STATUS_GENERATING.equals(guidebook.getStatus())
                || Guidebook.STATUS_FAILED.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "攻略尚未生成完成，暂不能修复");
        }
        boolean changed = ItineraryRepairSupport.repair(guidebook.getDays(),
                guidebook.getBudgetBreakdown(), guidebook.getIntent());
        if (changed) {
            guidebook.setStatus(Guidebook.STATUS_EDITING);
            if (guidebookMapper.updateById(guidebook) != 1) {
                throw new BizException(ResultCode.CONFLICT, "攻略已被修改，请刷新后重试");
            }
            versionService.append(guidebook, GuidebookVersion.CHANGE_TYPE_MANUAL,
                    "一键修复行程冲突", UserContext.getUserId());
        }
        List<NodeConflictDto> conflicts = detect(guidebook);
        return new GuidebookValidateResponse(conflicts.isEmpty(), conflicts);
    }

    /**
     * 对已加载的攻略实体做冲突检测，供编辑/调整/确认复用。
     * @param guidebook 攻略实体
     * @return 冲突列表
     */
    public List<NodeConflictDto> detect(Guidebook guidebook) {
        return conflictDetector.detect(guidebook);
    }


    /**
     *读取并校验攻略归属
     * @param guidebookId
     * @return
     */
    private Guidebook requireOwned(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND,"攻略不存在");
        }

        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER,"无权访问该攻略");
        }

        return  guidebook;
    }
}
