package com.travel.common;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 实体与DTO转换总入口
 * DtoMapper
 */
@Mapper
public interface DtoMapper {
    DtoMapper INSTANCE = Mappers.getMapper(DtoMapper.class);
}
