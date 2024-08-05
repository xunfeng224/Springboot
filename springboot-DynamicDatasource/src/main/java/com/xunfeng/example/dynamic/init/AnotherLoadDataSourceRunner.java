package com.xunfeng.example.dynamic.init;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.xunfeng.example.domain.entity.DataSourceEntity;
import com.xunfeng.example.dynamic.DynamicDataSource;
import com.xunfeng.example.mapper.DataSourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author 
 * @date 2024/6/17 14:31
 */
@Component
public class AnotherLoadDataSourceRunner implements CommandLineRunner {
    @Autowired
    private DataSourceMapper dataSourceMapper;
    @Resource(name = "anotherDynamicDataSource")
    private DynamicDataSource anotherDynamicDataSource;

    @Override
    public void run(String... args) throws Exception {
        List<DataSourceEntity> list = dataSourceMapper.selectList(null);
        if (CollectionUtils.isNotEmpty(list)) {
            anotherDynamicDataSource.createDataSource(list);
        }
    }
}
