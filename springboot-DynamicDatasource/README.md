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
Gitee源代码地址：

## 快速开始

其实也没啥好写的，DynamicDatasource功能很丰富，但本文章只涉及到简单的操作。流程为新建Springboot项目，引入Maven依赖，配置yml中的master数据源，使用mybatis-plus快速实现查询主数据源sys_user表数据，通过defaultDataSourceCreator.createDataSource(dataSourceProperty)创建数据源，通过dynamicRoutingDataSource.addDataSource(ds.getId().toString(), dataSource);添加数据源，通过DynamicDataSourceContextHolder.push(dsId.toString());切换数据源或通过注解@DS("master")切换

### 项目结构

![image-20240805160459616](C:\Users\hf\AppData\Roaming\Typora\typora-user-images\image-20240805160459616.png)

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

