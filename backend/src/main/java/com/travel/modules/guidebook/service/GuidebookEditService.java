package com.travel.modules.guidebook.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.AddNodeRequest;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.MoveNodeRequest;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.UpdateNodeRequest;
import com.travel.modules.guidebook.edit.ConflictDetector;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;

/**
 * FR-5 手动编辑：节点修改 / 新增 / 删除 / 移动。
 * 所有写操作都会让版本号 +1，并在同一事务内追加一条 manual 快照。
 */
@Service
@RequiredArgsConstructor
public class GuidebookEditService {

    private static final List<String> EDITABLE_STATUSES =
        List.of(Guidebook.STATUS_DRAFT,Guidebook.STATUS_EDITING);
    private static final List<String> NODE_TYPES =
        List.of("activity","meal","transport","stay","free");
    
    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionService versionService;
    private final ConflictDetector conflictDetector = new ConflictDetector();

    /**
     *编辑节点
     * @param guidebookId
     * @param day
     * @param nodeId
     * @param req
     * @return
     */
    @Transactional
    public GuidebookEditResponse updateNode(Long guidebookId, Integer day, String nodeId, UpdateNodeRequest req){
        Guidebook guidebook = requireEditable(guidebookId);
        GuidebookNodeDto node = findNode(findDay(guidebook,day),nodeId);

        

        if (req.getTimeSlot() != null) {
            node.setTimeSlot(req.getTimeSlot());
        }
        if (req.getPlace() != null) {
            node.setPlace(req.getPlace());
        }
        if (req.getNote() != null) {
            node.setNote(req.getNote());
        }
        return  save(guidebook, "修改Day" + day + "节点" + nodeName(node));
    }

    /**
     * 新增节点 在指定日期的指定位置插入节点
     * @param guidebookId
     * @param day
     * @param req
     * @return
     */
    @Transactional
    public GuidebookEditResponse addNode(Long guidebookId,Integer day, AddNodeRequest req){
        Guidebook guidebook = requireEditable(guidebookId);
        GuidebookDayDto targetDay = findDay(guidebook, day);

        if (req.getType() == null || !NODE_TYPES.contains(req.getType())) {
            throw new BizException(ResultCode.PARAM_ERROR,"节点类型不合法");
        }
        if (req.getTimeSlot() == null || req.getPlace() == null || !StringUtils.hasText(req.getPlace().getName())) {
            throw new BizException(ResultCode.PARAM_ERROR,"时间与地点名称必填");
        }

        List<GuidebookNodeDto> nodes = targetDay.getNodes();
        if (nodes == null) {
            nodes = new ArrayList<>();
            targetDay.setNodes(nodes);
        }
        int index = req.getIndex() == null ? nodes.size() :req.getIndex();
        if (index < 0 || index > nodes.size()) {
            throw new BizException(ResultCode.PARAM_ERROR,"插入位置超出范围");
        }

        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(newNodeId(guidebook));
        node.setType(req.getType());
        node.setTimeSlot(req.getTimeSlot());
        node.setPlace(req.getPlace());
        node.setNote(req.getNote());
        nodes.add(index, node);

        return save(guidebook, "在Day" + day + "第" + (index+1) + "位新增节点" + nodeName(node));
    }
    /**
     * 删除指定日期下的节点
     * @param guidebookId
     * @param day
     * @param nodeId
     * @return
     */
    @Transactional
    public GuidebookEditResponse deleteNode(Long guidebookId, Integer day, String nodeId){
        Guidebook guidebook = requireEditable(guidebookId);
        GuidebookDayDto targetDay = findDay(guidebook, day);
        GuidebookNodeDto node = findNode(targetDay, nodeId);

        String name = nodeName(node);
        targetDay.getNodes().removeIf(n -> nodeId.equals(n.getNodeId()));
        return  save(guidebook,"删除Day" + day + "节点" + name);
    }


    /**
     * 把节点移到目标日的目标位置 支持同一天内换位可跨天移动
     * @param guidebookId
     * @param nodeId
     * @param req
     * @return
     */
    @Transactional
    public GuidebookEditResponse moveNode(Long guidebookId, String nodeId, MoveNodeRequest req){
        Guidebook guidebook = requireEditable(guidebookId);
        if (req.getTargetDay() == null || req.getTargetIndex() == null) {
            throw new BizException(ResultCode.PARAM_ERROR,"目标日期和目标位置必填");
        }

        GuidebookDayDto sourceDay = null;
        GuidebookNodeDto moving = null;
        for(GuidebookDayDto d : guidebook.getDays()){
            if (d.getNodes() == null) {
                continue;
            }
            for(GuidebookNodeDto n : d.getNodes()){
                if (nodeId.equals(n.getNodeId())) {
                    sourceDay = d;
                    moving = n;
                    break;
                }
            }
            if (moving != null) {
                break;
            }
        }
        if (moving == null) {
            throw new  BizException(ResultCode.NOT_FOUND,"节点不存在");
        }
        GuidebookDayDto targetDay = findDay(guidebook,req.getTargetDay());
        int finalSize = (targetDay == sourceDay)
                        ? targetDay.getNodes().size() - 1
                        : targetDay.getNodes().size();

        if (req.getTargetIndex() < 0 || req.getTargetIndex() > finalSize) {
            throw new BizException(ResultCode.PARAM_ERROR,"目标位置超出范围");
        }

        sourceDay.getNodes().remove(moving);
        targetDay.getNodes().add(req.getTargetIndex(), moving);

        return  save(guidebook, "将节点【" + nodeName(moving) + "]从Day" + sourceDay.getDay() + "移动到Day" +targetDay.getDay() + "第" + (req.getTargetIndex() + 1)  + "位");
        }

        /**
         * 保存编辑结果：转台转editing -> 乐观锁更新当前表 + 追加manual 快照
         * @param guidebook
         * @param summary
         * @return
         */
    private GuidebookEditResponse save(Guidebook guidebook, String summary) {
        guidebook.setStatus(Guidebook.STATUS_EDITING);
        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.CONFLICT,"攻略已被修改，请刷新后重试");
        }
        versionService.append(guidebook, GuidebookVersion.CHANGE_TYPE_MANUAL, summary, UserContext.getUserId());

        List<NodeConflictDto> conflicts = conflictDetector.detect(guidebook);

        return  new GuidebookEditResponse(guidebook.getId(),guidebook.getVersion(),summary,conflicts);
    }

    /**
     * 读取并校验可编辑的攻略 ：存在且属于当前用户 状态为 draft/editing
     * @param guidebookId
     * @return
     */
    private Guidebook requireEditable(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND,"攻略不存在");
        }
        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER,"无权访问该攻略");
        }
        if (!EDITABLE_STATUSES.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不可编辑");
        }
        return  guidebook;
    }

    /**
     * 取地点名用于变更摘要 地点缺失时给一个可读占位 避免出现null
     * @param node
     * @return
     */
    private String nodeName(GuidebookNodeDto node) {
        return node.getPlace() != null && StringUtils.hasText(node.getPlace().getName())
                ?node.getPlace().getName()
                :"(未命名地点)";
    }

    /**
     *在指定日期内按nodeId 查找节点 找不到抛 40400
     * @param targetDay
     * @param nodeId
     * @return
     */
    private GuidebookNodeDto findNode(GuidebookDayDto targetDay, String nodeId) {
        if (targetDay.getNodes() == null || !StringUtils.hasText(nodeId)) {
            throw new BizException(ResultCode.NOT_FOUND,"节点不存在");
        }
        return  targetDay.getNodes().stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "节点不存在"));
    }

    /**
     * 按【第几天】查找日程
     * @param guidebook
     * @param day
     * @return
     */
    private GuidebookDayDto findDay(Guidebook guidebook, Integer day) {
        if (guidebook.getDays() == null || day == null) {
            throw new BizException(ResultCode.NOT_FOUND,"指定日期不存在");
        }
        return  guidebook.getDays().stream()
                .filter(d -> Objects.equals(d.getDay(), day))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,"指定日期不存在"));
    }
    
    /**
     * 生成全攻略唯一的节点ID
     * 不能沿用生成阶段的d{day} -{index}: 移动或插入后序号会重复
     * @param guidebook
     * @return
     */
    private String newNodeId(Guidebook guidebook) {
        String id;
        do{
            id = "n" + UUID.randomUUID().toString().replace("-", "").substring(0,8);
        }while(nodeIdExists(guidebook,id));
        return  id;
    }

    /**
     * 检查nodeId是否已被任意一天的节点占用
     * @param guidebook
     * @param nodeId
     * @return
     */
    private boolean nodeIdExists(Guidebook guidebook,String nodeId){
        if (guidebook.getDays() == null) {
            return false;
        }
        return  guidebook.getDays().stream()
                .filter(d -> d.getNodes() != null)
                .flatMap(d -> d.getNodes().stream())
                .anyMatch(n -> nodeId.equals(n.getNodeId()));
    }

}
