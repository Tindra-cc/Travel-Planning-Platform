package com.travel.modules.guidebook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.GuidebookValidateResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.edit.ConflictDetector;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuidebookConflictService {

    private final GuidebookMapper guidebookMapper;
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
        List<NodeConflictDto> conflicts = conflictDetector.detect(guidebook);

        return  new GuidebookValidateResponse(conflicts.isEmpty(),conflicts);
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
