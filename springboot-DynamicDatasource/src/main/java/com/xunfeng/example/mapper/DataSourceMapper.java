package com.xunfeng.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xunfeng.example.domain.entity.DataSourceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源管理表
 *
 * @author
 * @date 2024-06-06 10:00:42
 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourceEntity> {

}
