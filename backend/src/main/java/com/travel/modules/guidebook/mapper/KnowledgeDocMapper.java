package com.travel.modules.guidebook.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.modules.guidebook.entity.KnowledgeDoc;

public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    /** 目的地精确匹配优先，其次按关键词标题/正文子串匹配与权重排序。 */
    List<KnowledgeDoc> search(@Param("destination") String destination,
            @Param("keywords") List<String> keywords,
            @Param("limit") int limit);
}
