package com.travel.modules.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.modules.auth.entity.User;

public interface UserMapper extends BaseMapper<User> {

    /** 配额扣减需要行锁，避免并发请求同时通过检查。 */
    @Select("SELECT * FROM t_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User selectForUpdate(@Param("id") Long id);
}
