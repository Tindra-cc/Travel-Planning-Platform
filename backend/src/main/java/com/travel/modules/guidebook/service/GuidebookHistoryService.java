package com.travel.modules.guidebook.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookVersionItem;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.GuidebookVersionMapper;

import lombok.RequiredArgsConstructor;

/**
 * FR-5 版本历史：只追加快照的查询与回溯。
 * 回溯不是回滚：旧快照内容复制成新版本，历史行永不修改。
 */
@Service
@RequiredArgsConstructor
public class GuidebookHistoryService {

    private static final Set<String> EDITABLE_STATUSES =
            Set.of(Guidebook.STATUS_DRAFT, Guidebook.STATUS_EDITING);

    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionMapper versionMapper;
    private final GuidebookVersionService versionService;
    private final GuidebookConflictService conflictService;

    /**
     * §7.8 版本列表：按版本号倒序，轻量字段。
     * @param guidebookId 攻略 ID
     * @return 版本条目
     */
    public List<GuidebookVersionItem> listVersions(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        return versionMapper.selectList(new LambdaQueryWrapper<GuidebookVersion>()
                        .eq(GuidebookVersion::getGuidebookId, guidebook.getId())
                        .orderByDesc(GuidebookVersion::getVersion))
                .stream()
                .map(v -> new GuidebookVersionItem(v.getVersion(), v.getTitle(),
                        v.getChangeType(), v.getChangeSummary(), v.getCreateTime()))
                .toList();
    }

    /**
     * §7.9 回溯到指定版本：复制旧快照成新版本。
     * 仅 draft/editing 可回溯；已确认/执行中的攻略不允许改内容。
     * @param guidebookId 攻略 ID
     * @param targetVersion 目标版本号
     * @return 新版本号、摘要与冲突
     */
    @Transactional
    public GuidebookEditResponse restore(Long guidebookId, Integer targetVersion) {
        Guidebook guidebook = requireEditable(guidebookId);
        if (targetVersion == null || targetVersion < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "版本号不合法");
        }
        GuidebookVersion snapshot = versionMapper.selectOne(
                new LambdaQueryWrapper<GuidebookVersion>()
                        .eq(GuidebookVersion::getGuidebookId, guidebookId)
                        .eq(GuidebookVersion::getVersion, targetVersion)
                        .last("LIMIT 1"));
        if (snapshot == null) {
            throw new BizException(ResultCode.NOT_FOUND, "目标版本不存在");
        }

        guidebook.setStatus(Guidebook.STATUS_EDITING);
        guidebook.setTitle(snapshot.getTitle());
        guidebook.setDays(snapshot.getDays());
        guidebook.setBudgetBreakdown(snapshot.getBudgetBreakdown());
        guidebook.setPackingList(snapshot.getPackingList());
        guidebook.setCulturalTips(snapshot.getCulturalTips());
        guidebook.setPitfallTips(snapshot.getPitfallTips());

        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.CONFLICT, "攻略已被修改，请刷新后重试");
        }

        String summary = "回溯到 v" + targetVersion;
        versionService.append(guidebook, GuidebookVersion.CHANGE_TYPE_RESTORE,
                summary, UserContext.getUserId());

        List<NodeConflictDto> conflicts = conflictService.detect(guidebook);
        return new GuidebookEditResponse(guidebook.getId(), guidebook.getVersion(),
                summary, conflicts);
    }

    /**
     * 读取并校验归属。
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

    /**
     * 读取并校验可编辑状态。
     * @param guidebookId 攻略 ID
     * @return 攻略实体
     */
    private Guidebook requireEditable(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        if (!EDITABLE_STATUSES.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不可编辑");
        }
        return guidebook;
    }
}
