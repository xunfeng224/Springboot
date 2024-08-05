package com.xunfeng.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xunfeng.example.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author
 * @date 2024/8/5 10:25
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
