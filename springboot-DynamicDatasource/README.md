[TOC]



# 动态数据源DynamicDatasource

## 简介

本文介绍baomidou开源的多数据源扩展插件DynamicDatasource，并用来实现动态数据源及以下功能

介绍功能包括：

1. 提供 **自定义数据源来源** 方案（如全从数据库加载）。
2. 提供项目启动后 **动态增加移除数据源** 方案。
3. 支持 **自定义注解** ，需继承DS(3.2.0+)。

DynamicDatasource[项目地址](https://github.com/baomidou/dynamic-datasource)、[文档地址](https://www.kancloud.cn/tracy5546/dynamic-datasource/2264611)

本文Demo完整源码：
Github源代码地址：https://github.com/xunfeng224/Springboot/tree/main/springboot-DynamicDatasource
Gitee源代码地址：https://gitee.com/xfeng520/Springboot/tree/main/springboot-DynamicDatasource

> [!CAUTION]
>
> 运行源码需注意，由于加入了手动实现的动态数据源，会导致Bean冲突，若想运行DynamicDataSource，将手动代码dynamic包直接删除，若想运行手动实现的动态数据源，将`LoadDataSourceRunner`类全部注释掉，避免找不到Bean报错。手动动态数据源相关代码全部位于dynamic包下。
>
> 原因：自定义数据源管理类`DynamicDataSource`继承了Spring的`AbstractRoutingDataSource`类，而在DynamicDataSou源码中，`DynamicRoutingDataSource`类同样继承了 `AbstractRoutingDataSource`   ,本来想通过自定义Bean注入名称来解决Bean冲突，发现不可行。在`DynamicDataSourceAutoConfiguration`中注册`DynamicRoutingDataSource`的bean时，加入了`@ConditionalOnMissingBean`注解，这将导致有自定义实现类bean注入Spring容器时，`DynamicRoutingDataSource`无法注入Spring容器，从而启动报错。
>
> @ConditionalOnMissingBean作用：判断当前需要注入Spring容器中的bean的实现类是否已经含有，有的话不注入，没有就注入
>
> ```java
>     @Bean
>     @ConditionalOnMissingBean
>     public DataSource dataSource(List<DynamicDataSourceProvider> providers) {
>         DynamicRoutingDataSource dataSource = new DynamicRoutingDataSource(providers);
>         dataSource.setPrimary(this.properties.getPrimary());
>         dataSource.setStrict(this.properties.getStrict());
>         dataSource.setStrategy(this.properties.getStrategy());
>         dataSource.setP6spy(this.properties.getP6spy());
>         dataSource.setSeata(this.properties.getSeata());
>         dataSource.setGraceDestroy(this.properties.getGraceDestroy());
>         return dataSource;
>     }
> ```
>
> 

## 源码分析

### ThreadLocal和AbstractRoutingDataSource

`ThreadLocal`：全称：`thread local variable`。主要是为解决多线程时由于并发而产生数据不一致问题。ThreadLocal为每个线程提供变量副本，确保每个线程在某一时间访问到的不是同一个对象，这样做到了隔离性，增加了内存，但大大减少了线程同步时的性能消耗，减少了线程并发控制的复杂程度。

- ThreadLocal作用：在一个线程中共享，不同线程间隔离
- ThreadLocal原理：ThreadLocal存入值时，会获取当前线程实例作为key，存入当前线程对象中的Map中。

`AbstractRoutingDataSource`：根据用户定义的规则选择当前的数据源，

Spring boot提供了AbstractRoutingDataSource 根据用户定义的规则选择当前的数据源，这样我们可以在执行查询之前，设置使用的数据源。实现可动态路由的数据源，在每次数据库查询操作前执行。它的抽象方法 determineCurrentLookupKey() 决定使用哪个数据源。

### DynamicRoutingDataSource

该类继承上述提到的`AbstractRoutingDataSource`抽象类，实现`determineDataSource()`方法，如上文所述，该方法决定了当前数据库操作所使用的数据源

```java
public class DynamicRoutingDataSource extends AbstractRoutingDataSource implements InitializingBean, DisposableBean {
 //...省略...   
     /**
     * 通过各种方式加载的数据源将存储在该Map中，后续动态切换也是从这里获取
     */
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
        /**
     * 分组数据库
     */
    private final Map<String, GroupDataSource> groupDataSources = new ConcurrentHashMap<>();
    /**
    获取主数据源
    */
    @Override
    protected String getPrimary() {
        return primary;
    }

    @Override
    public DataSource determineDataSource() {
        // 数据源key/数据源名称从DynamicDataSourceContextHolder.peek()中获取
        String dsKey = DynamicDataSourceContextHolder.peek();
        return getDataSource(dsKey);
    }
    
      /**
     * 获取数据源
     *
     * @param ds 数据源名称
     * @return 数据源
     */
    public DataSource getDataSource(String ds) {
        if (DsStrUtils.isEmpty(ds)) {
            // 这里数据源名称为空，调用方法获取主数据源
            return determinePrimaryDataSource();
        } else if (!groupDataSources.isEmpty() && groupDataSources.containsKey(ds)) {
            log.debug("dynamic-datasource switch to the datasource named [{}]", ds);
            return groupDataSources.get(ds).determineDataSource();
        } else if (dataSourceMap.containsKey(ds)) {
            log.debug("dynamic-datasource switch to the datasource named [{}]", ds);
            return dataSourceMap.get(ds);
        }
        if (strict) {
            throw new CannotFindDataSourceException("dynamic-datasource could not find a datasource named " + ds);
        }
        return determinePrimaryDataSource();
    }
    
        /**
     * 添加数据源
     *
     * @param ds         数据源名称
     * @param dataSource 数据源
     */
    public synchronized void addDataSource(String ds, DataSource dataSource) {
        DataSource oldDataSource = dataSourceMap.put(ds, dataSource);
        // 新数据源添加到分组
        this.addGroupDataSource(ds, dataSource);
        // 关闭老的数据源
        if (oldDataSource != null) {
            closeDataSource(ds, oldDataSource, graceDestroy);
        }
        log.info("dynamic-datasource - add a datasource named [{}] success", ds);
    }
 //...省略...  
    
}
```



`DynamicDataSourceContextHolder`类 ，源码自带注解也挺详细的了，不做多解释

```java
import org.springframework.core.NamedThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 核心基于ThreadLocal的切换数据源工具类
 *
 * @author TaoYu Kanyuxia
 * @since 1.0.0
 */
public final class DynamicDataSourceContextHolder {

    /**
     * 为什么要用链表存储(准确的是栈)
     * <pre>
     * 为了支持嵌套切换，如ABC三个service都是不同的数据源
     * 其中A的某个业务要调B的方法，B的方法需要调用C的方法。一级一级调用切换，形成了链。
     * 传统的只设置当前线程的方式不能满足此业务需求，必须使用栈，后进先出。
     * </pre>
     */
    private static final ThreadLocal<Deque<String>> LOOKUP_KEY_HOLDER = new NamedThreadLocal<Deque<String>>("dynamic-datasource") {
        @Override
        protected Deque<String> initialValue() {
            return new ArrayDeque<>();
        }
    };

    private DynamicDataSourceContextHolder() {
    }

    /**
     * 获得当前线程数据源
     *
     * @return 数据源名称
     */
    public static String peek() {
        return LOOKUP_KEY_HOLDER.get().peek();
    }

    /**
     * 设置当前线程数据源
     * <p>
     * 如非必要不要手动调用，调用后确保最终清除
     * </p>
     *
     * @param ds 数据源名称
     * @return 数据源名称
     */
    public static String push(String ds) {
        String dataSourceStr = DsStrUtils.isEmpty(ds) ? "" : ds;
        LOOKUP_KEY_HOLDER.get().push(dataSourceStr);
        return dataSourceStr;
    }

    /**
     * 清空当前线程数据源
     * <p>
     * 如果当前线程是连续切换数据源 只会移除掉当前线程的数据源名称
     * </p>
     */
    public static void poll() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            LOOKUP_KEY_HOLDER.remove();
        }
    }

    /**
     * 强制清空本地线程
     * <p>
     * 防止内存泄漏，如手动调用了push可调用此方法确保清除
     * </p>
     */
    public static void clear() {
        LOOKUP_KEY_HOLDER.remove();
    }
}
```





## DynamicDatasource快速开始

其实也没啥好写的，DynamicDatasource功能很丰富，但本文章只涉及到简单的操作。流程为新建Springboot项目，引入Maven依赖，配置yml中的master数据源，使用mybatis-plus快速实现查询主数据源sys_user表数据，通过defaultDataSourceCreator.createDataSource(dataSourceProperty)创建数据源，通过dynamicRoutingDataSource.addDataSource(ds.getId().toString(), dataSource);添加数据源，通过DynamicDataSourceContextHolder.push(dsId.toString());切换数据源或通过注解@DS("master")切换

### 项目结构

![image-20240806101011218](C:\Users\hf\AppData\Roaming\Typora\typora-user-images\image-20240806101011218.png)

### Maven依赖

引入DynamicDatasource依赖

```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
            <version>4.3.1</version>
        </dependency>
```

引入其他依赖，为本案例项目所需依赖，非实现动态数据源所必须

```xml
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.22</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <!--下面坐标根据自己使用的SpringBoot版本二选一-->
            <!--SpringBoot2使用此版本-->
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>3.5.3.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
            <version>4.3.1</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>1.2.23</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.2.18</version>
        </dependency>
```

### application.yml配置文件

其中spring.datasource.dynamic.datasource即为DynamicDatasource插件所需的配置文件，master为主数据源，主数据源是必须的，但不是一定得从配置文件设置，这里不过多展开，后文介绍逻辑会将明白。

```yaml
# 应用服务 WEB 访问端口
server:
  port: 8080
spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        # 主数据源
        master:
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://${DB_HOST:127.0.0.1}:3306/test?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&allowMultiQueries=true
          username: ${DB_USER:root}
          password: ${DB_PASSWORD:root}
          type: com.alibaba.druid.pool.DruidDataSource
      druid:
        # 等待时间毫秒
        max-wait: 3000
        # 重试次数
        connection-error-retry-attempts: 3
        # 失败后break退出循环，若为false，当getConnection失败时会无限重试
        break-after-acquire-failure: true
```

### sql脚本

sys_user表用于测试获取数据，data_source表存放动态数据源

``` sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码',
  `salt` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '盐值',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '电话号码',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '姓名',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱地址',
  `dept_id` bigint NULL DEFAULT NULL COMMENT '所属部门ID',
  `create_by` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `lock_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '锁定标记，0未锁定，9已锁定',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `wx_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信登录openId',
  `mini_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小程序openId',
  `qq_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'QQ openId',
  `gitee_login` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '码云标识',
  `osc_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开源中国标识',
  PRIMARY KEY (`user_id`) USING BTREE,
  INDEX `user_wx_openid`(`wx_openid`) USING BTREE,
  INDEX `user_qq_openid`(`qq_openid`) USING BTREE,
  INDEX `user_idx1_username`(`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'admin', '$2a$10$8KIKR17eIM4VZFycIYRycOoW/4q0pFuFg4y/16lMm9aPQ1n4.vXx6', '', '17034642999', '/admin/sys-file/local/7fba53c7f1ff449aa22d60660498cf61.jpg', '管理员', '管理员', 'pig4cloud@qq.com', 4, ' ', 'admin', '2018-04-20 07:15:18', '2024-05-09 18:12:49', '0', '0', NULL, 'oBxPy5E-v82xWGsfzZVzkD3wEX64', NULL, 'log4j', NULL);


-- ----------------------------
-- Table structure for data_source
-- ----------------------------
DROP TABLE IF EXISTS `data_source`;
CREATE TABLE `data_source`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据源主键id',
  `type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '数据源类型',
  `driver_class_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '数据库驱动',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '数据库地址',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '连接名称',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主机名或IP地址',
  `port` int NULL DEFAULT NULL COMMENT '端口号默认3306',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '密码',
  `data_base` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '数据库名',
  `param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '参数',
  `state` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '数据源状态：0连接失败，1连接成功',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '数据源管理表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of data_source
-- ----------------------------
INSERT INTO `data_source` VALUES (1, NULL, 'com.mysql.cj.jdbc.Driver', 'jdbc:mysql://192.168.252.15:3306/AIGC?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&allowMultiQueries=true&useInformationSchema=true', '银行业务数据库', '192.168.252.15', 3306, 'hithium', 'Hithium@Dev2024', NULL, NULL, NULL, '0', '', NULL, '', NULL, NULL);
INSERT INTO `data_source` VALUES (2, NULL, 'org.postgresql.Driver', 'jdbc:postgresql://127.0.0.1:5432/test_db', 'openGauss数据库', '127.0.0.1', 5432, 'gaussdb', 'Enmo@123', NULL, NULL, NULL, '0', '', NULL, '', NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;

```

### 动态加载数据源

这里从主数据库data_source表中获取数据源信息，并创建DruidDatasource，并通过dynamicRoutingDataSource.addDataSource()方法将Datasource进行统一管理

LoadDataSourceRunner.java
```java
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

```

### 动态切换数据源

1.代码中切换：DynamicDataSourceContextHolder.push(【数据源key】);
2.注解形式切换@DS("master")

```java
package com.xunfeng.example.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.xunfeng.example.domain.entity.SysUser;
import com.xunfeng.example.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author
 * @date 2024/8/5 10:27
 */
@RestController
@RequestMapping("user")
public class UserController {
    @Autowired
    private SysUserMapper sysUserMapper;

    @GetMapping("/{dsId}/{userId}")
    public String getUser(@PathVariable("dsId") String dsId, @PathVariable("userId") Long userId) {
        DynamicDataSourceContextHolder.push(dsId.toString());
        SysUser sysUser = sysUserMapper.selectById(userId);
        return sysUser.toString();
    }

    @GetMapping("/{userId}")
    @DS("master")
    public String getUserDS(@PathVariable("dsId") String dsId, @PathVariable("userId") Long userId) {
        DynamicDataSourceContextHolder.push(dsId.toString());
        SysUser sysUser = sysUserMapper.selectById(userId);
        return sysUser.toString();
    }
}
```

## 手动实现

模仿DynamicDatasource手动实现简单动态数据源，如果不需要dynamicDatasource那么复杂的功能，可以考虑手动实现。

### 核心类`DataSourceContextHolder`

```java
package com.xunfeng.example.dynamic;

/**
 * @author 
 * @date 2024/6/17 14:20
 */
public class DataSourceContextHolder {
    /**
     * 此类提供线程局部变量。这些变量不同于它们的正常对应关系是每个线程访问一个线程(通过get、set方法),有自己的独立初始化变量的副本。
     */
    private static final ThreadLocal<String> DATASOURCE_HOLDER = new ThreadLocal<>();

    /**
     * 设置数据源
     *
     * @param dataSourceName 数据源名称
     */
    public static void setDataSource(String dataSourceName) {
        DATASOURCE_HOLDER.set(dataSourceName);
    }

    /**
     * 获取当前线程的数据源
     *
     * @return 数据源名称
     */
    public static String getDataSource() {
        return DATASOURCE_HOLDER.get();
    }

    /**
     * 删除当前数据源
     */
    public static void removeDataSource() {
        DATASOURCE_HOLDER.remove();
    }

}

```

### 核心类`DynamicDataSource`

```java
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
```

### 核心类`DynamicDataSourceConfig`

这里主要功能为注册主数据源，也可以在这里注册更多的其他数据源

```java
package com.xunfeng.example.dynamic;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 
 * @date 2024/6/17 14:54
 */
@Configuration
public class DynamicDataSourceConfig {
    @Bean
    @ConfigurationProperties("spring.datasource.dynamic.datasource.master")
    public DataSource masterDataSource(){
        return DruidDataSourceBuilder.create().build();
    }
    @Bean(name = "anotherDynamicDataSource")
    @Primary
    public DynamicDataSource dataSource() {
        Map<Object,Object> dataSourceMap = new HashMap<>();
        DataSource defaultDataSource = masterDataSource();
        dataSourceMap.put("master",defaultDataSource);
        return new DynamicDataSource(defaultDataSource,dataSourceMap);
    }
}
```

### 服务启动加载数据源类`AnotherLoadDataSourceRunner`

```java
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
```

### 自定义数据源切换注解`DataSource`

```java
package com.xunfeng.example.dynamic.annotation;

import java.lang.annotation.*;

/**
 * @author 
 * @date 2024/6/17 15:17
 */
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataSource {
    String value() default "master";
}
```



### 切面`DSAspect`

```java
package com.xunfeng.example.dynamic.aspect;


import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xunfeng.example.domain.entity.DataSourceEntity;
import com.xunfeng.example.dynamic.DataSourceContextHolder;
import com.xunfeng.example.dynamic.annotation.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author 
 * @date 2024/6/17 15:18
 */
@Aspect
@Component
public class DSAspect {

    @Pointcut("@annotation(com.xunfeng.example.dynamic.annotation.DataSource)")
    public void datasourcePoint() {
    }

    @Around("datasourcePoint()")
    public Object datasourceAround(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        DataSource dataSource = method.getAnnotation(DataSource.class);
        if (Objects.nonNull(dataSource)) {
            // 数据源key
            String key = null;
            // 1.从入参中获取数据源key，并切换
            Object[] args = point.getArgs();
            for (Object arg : args) {
                // 自定义入参标准，这里简单用id作为key
                if (arg instanceof DataSourceEntity) {
                    DataSourceEntity req = (DataSourceEntity) arg;
                    key = req.getId().toString();
                }
            }
            // 2.获取注解中的value为数据源key
            if (StringUtils.isEmpty(key)) {
                key = dataSource.value();
            }
            // 实时切换默认数据源
            DataSourceContextHolder.setDataSource(key);


        }
        try {
            return point.proceed();
        } finally {
            DataSourceContextHolder.removeDataSource();
        }
    }
}

```



