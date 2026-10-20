package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.AddNodeRequest;
import com.travel.modules.guidebook.dto.AdjustRequest;
import com.travel.modules.guidebook.dto.AdjustResponse;
import com.travel.modules.guidebook.dto.GuidebookConfirmResponse;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookValidateResponse;
import com.travel.modules.guidebook.dto.MoveNodeRequest;
import com.travel.modules.guidebook.dto.UpdateNodeRequest;
import com.travel.modules.guidebook.service.GuidebookAdjustService;
import com.travel.modules.guidebook.service.GuidebookConflictService;
import com.travel.modules.guidebook.service.GuidebookConfirmService;
import com.travel.modules.guidebook.service.GuidebookEditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "攻略编辑", description = "FR-5：节点编辑 / 新增 / 删除 / 移动")
@RestController
@RequestMapping("/api/guidebooks")
@RequiredArgsConstructor
public class GuidebookEditController {

    private final GuidebookEditService editService;
    private final GuidebookConflictService conflictService;
    private final GuidebookAdjustService adjustService;
    private final GuidebookConfirmService confirmService;

    /**
     * 编辑节点：只覆盖传入的时段 / 地点 / 备注。
     */
    @Operation(summary = "编辑节点")
    @PutMapping("/{guidebookId}/days/{day}/nodes/{nodeId}")
    public Result<GuidebookEditResponse> updateNode(
            @PathVariable Long guidebookId,
            @PathVariable Integer day,
            @PathVariable String nodeId,
            @RequestBody UpdateNodeRequest req) {
        return Result.success(editService.updateNode(guidebookId, day, nodeId, req));
    }

    /**
     * 新增节点：index 为空时追加到当天末尾。
     */
    @Operation(summary = "新增节点")
    @PostMapping("/{guidebookId}/days/{day}/nodes")
    public Result<GuidebookEditResponse> addNode(
            @PathVariable Long guidebookId,
            @PathVariable Integer day,
            @RequestBody AddNodeRequest req) {
        return Result.success(editService.addNode(guidebookId, day, req));
    }

    /**
     * 删除节点
     */
    @Operation(summary = "删除节点")
    @DeleteMapping("/{guidebookId}/days/{day}/nodes/{nodeId}")
    public Result<GuidebookEditResponse> deleteNode(
            @PathVariable Long guidebookId,
            @PathVariable Integer day,
            @PathVariable String nodeId) {
        return Result.success(editService.deleteNode(guidebookId, day, nodeId));
    }

    /**
     * 移动节点：支持同一天换位与跨天移动。
     */
    @Operation(summary = "移动节点")
    @PostMapping("/{guidebookId}/nodes/{nodeId}/move")
    public Result<GuidebookEditResponse> moveNode(
            @PathVariable Long guidebookId,
            @PathVariable String nodeId,
            @RequestBody MoveNodeRequest req) {
        return Result.success(editService.moveNode(guidebookId, nodeId, req));
    }

    /**
     * 
     * @param guidebookId
     * @return
     */
    @Operation(summary = "节点冲突检测")
    @PostMapping("/{guidebookId}/validate")
    public Result<GuidebookValidateResponse> validate(@PathVariable  Long guidebookId){
        return Result.success(conflictService.validate(guidebookId));
    }

    /**
     * §7.5 对话式调整：由 AI 解析指令并生成一份新版本。
     * 同步接口，可能耗时数十秒；失败时不改变任何数据。
     * @param guidebookId 攻略 ID
     * @param req 调整指令
     * @return 新版本、摘要、变更与冲突
     */
    @Operation(summary = "对话式调整")
    @PostMapping("/{guidebookId}/adjust")
    public Result<AdjustResponse> adjust(@PathVariable Long guidebookId,
            @RequestBody AdjustRequest req) {
        return Result.success(adjustService.adjust(guidebookId, req.getInstruction()));
    }

    /**
     * §7.7 确认攻略：有未解决冲突时返回 42200，状态不变。
     * @param guidebookId 攻略 ID
     * @return guidebookId 与 confirmed 状态
     */
    @Operation(summary = "确认攻略")
    @PostMapping("/{guidebookId}/confirm")
    public Result<GuidebookConfirmResponse> confirm(@PathVariable Long guidebookId) {
        return Result.success(confirmService.confirm(guidebookId));
    }
}