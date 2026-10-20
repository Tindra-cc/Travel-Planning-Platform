package com.travel.modules.guidebook.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.GuidebookConfirmResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;

/**
 * FR-5 确认攻略：状态机闸门。
 * 只有无冲突的 draft/editing 才能进入 confirmed；确认不改变内容，因此不写版本快照。
 */
@Service
@RequiredArgsConstructor
public class GuidebookConfirmService {

    private static final Set<String> CONFIRMABLE_STATUSES =
            Set.of(Guidebook.STATUS_DRAFT, Guidebook.STATUS_EDITING);
    private static final int MAX_MESSAGE_CONFLICTS = 2;

    private final GuidebookMapper guidebookMapper;
    private final GuidebookConflictService conflictService;

    /**
     * §7.7 确认攻略。
     * 先跑服务端冲突检测；有冲突返回 42200，状态保持不变。
     * @param guidebookId 攻略 ID
     * @return guidebookId 与 confirmed 状态
     */
    public GuidebookConfirmResponse confirm(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        if (!CONFIRMABLE_STATUSES.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不可确认");
        }

        List<NodeConflictDto> conflicts = conflictService.detect(guidebook);
        if (!conflicts.isEmpty()) {
            throw new BizException(ResultCode.UNPROCESSABLE, conflictMessage(conflicts));
        }

        guidebook.setStatus(Guidebook.STATUS_CONFIRMED);
        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.CONFLICT, "攻略已被修改，请刷新后重试");
        }
        return new GuidebookConfirmResponse(guidebook.getId(), guidebook.getStatus());
    }

    /**
     * 把冲突压成一条可读错误消息。
     * 完整明细由 §7.6 validate 接口提供，这里只给用户一个可行动的提示。
     * @param conflicts 冲突列表
     * @return 错误消息
     */
    private String conflictMessage(List<NodeConflictDto> conflicts) {
        String details = conflicts.stream()
                .limit(MAX_MESSAGE_CONFLICTS)
                .map(NodeConflictDto::getMessage)
                .collect(Collectors.joining("；"));
        return "存在 " + conflicts.size() + " 处冲突，处理后才能确认：" + details;
    }

    /**
     * 读取并校验攻略归属。
     * @param guidebookId 攻略 ID
     * @return 攻略实体
     */
    private Guidebook requireOwned(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        return guidebook;
    }
}
