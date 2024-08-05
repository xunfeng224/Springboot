package com.xunfeng.example.dynamic;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.xunfeng.example.domain.entity.DataSourceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author 
 * @date 2024/6/17 14:19
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {
    private final static Map<Object, Object> targetDataSourceMap = new HashMap<>();

    public DynamicDataSource(DataSource defaultDataSource, Map<Object, Object> targetDataSources) {
        super.setDefaultTargetDataSource(defaultDataSource);
        super.setTargetDataSources(targetDataSources);
        targetDataSourceMap.put("master", defaultDataSource);
//        targetDataSourceMap = targetDataSources;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String dataSource = DataSourceContextHolder.getDataSource();
        DataSourceContextHolder.removeDataSource();
        return dataSource;
    }

    /**
     * 添加数据源信息
     *
     * @param dataSources 数据源实体集合
     * @return 返回添加结果
     */
    public Boolean createDataSource(List<DataSourceEntity> dataSources) {
        if (CollectionUtils.isNotEmpty(dataSources)) {
            for (DataSourceEntity ds : dataSources) {
                try {
                    //校验数据库是否可以连接
                    Class.forName(ds.getDriverClassName());
                    DriverManager.getConnection(ds.getUrl(), ds.getUsername(), ds.getPassword());
                    //定义数据源
                    DruidDataSource dataSource = new DruidDataSource();
                    BeanUtils.copyProperties(ds, dataSource);
                    //申请连接时执行validationQuery检测连接是否有效，这里建议配置为TRUE，防止取到的连接不可用
                    dataSource.setTestOnBorrow(true);
                    //建议配置为true，不影响性能，并且保证安全性。
                    //申请连接的时候检测，如果空闲时间大于timeBetweenEvictionRunsMillis，执行validationQuery检测连接是否有效。
                    dataSource.setTestWhileIdle(true);
                    //用来检测连接是否有效的sql，要求是一个查询语句。
                    dataSource.setValidationQuery("select 1 ");
                    dataSource.init();
                    this.targetDataSourceMap.put(ds.getId(), dataSource);
                } catch (ClassNotFoundException | SQLException e) {
                    log.error("---数据源初始化错误---:{}", e.getMessage());
                }
            }
            super.setTargetDataSources(targetDataSourceMap);
            // 将TargetDataSources中的连接信息放入resolvedDataSources管理
            super.afterPropertiesSet();
            return Boolean.TRUE;
        }

        return Boolean.FALSE;
    }

    /**
     * 校验数据源是否存在
     *
     * @param key 数据源保存的key
     * @return 返回结果，true：存在，false：不存在
     */
    public static boolean existsDataSource(Long key) {
        return Objects.nonNull(targetDataSourceMap.get(key));
    }

    public static Map<Object, Object> getTargetDataSourceMap() {
        return targetDataSourceMap;
    }

}
