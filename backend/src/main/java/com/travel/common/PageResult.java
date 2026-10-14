package com.travel.common;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一分页结构，定义见接口文档 §1.4：{list, page, pageSize, total}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> list;
    private long page;
    private long pageSize;
    private long total;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    public static <T> PageResult<T> of(List<T> list, long page, long pageSize, long total) {
        return new PageResult<>(list, page, pageSize, total);
    }
}
