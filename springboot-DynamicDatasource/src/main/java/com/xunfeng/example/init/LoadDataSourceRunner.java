package com.xunfeng.example.init;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.xunfeng.example.domain.entity.DataSourceEntity;
import com.xunfeng.example.mapper.DataSourceMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * @author
 * @date 2024/6/17 14:31
 */
@Component
public class LoadDataSourceRunner implements CommandLineRunner {
    @Autowired
    private DataSourceMapper dataSourceMapper;
    @Autowired
    private DefaultDataSourceCreator defaultDataSourceCreator;
    @Autowired
    private DynamicRoutingDataSource dynamicRoutingDataSource;

    @Override
    public void run(String... args) {
        List<DataSourceEntity> dataSourceEntities = dataSourceMapper.selectList(null);
        if (CollectionUtils.isNotEmpty(dataSourceEntities)) {
            for (DataSourceEntity ds : dataSourceEntities) {
                DataSourceProperty dataSourceProperty = new DataSourceProperty();
                BeanUtils.copyProperties(ds, dataSourceProperty);
                DataSource dataSource = defaultDataSourceCreator.createDataSource(dataSourceProperty);
                Connection connection = null;
                try {
                    connection = dataSource.getConnection();
                    // 本质上是个Map，采用key-value形式存储数据源，后续获取数据源需要key
                    dynamicRoutingDataSource.addDataSource(ds.getId().toString(), dataSource);
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                } finally {
                    try {
                        if (connection != null) {
                            connection.close();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        }
    }
}
