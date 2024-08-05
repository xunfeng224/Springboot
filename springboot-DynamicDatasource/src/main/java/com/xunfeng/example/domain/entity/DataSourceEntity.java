package com.xunfeng.example.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源管理表
 *
 * @author
 * @date 2024-06-06 10:00:42
 */
@Data
@TableName("data_source")
public class DataSourceEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 数据源主键id
	 */
	@TableId
	private Long id;
	/**
	 * 数据源类型
	 */
	private String type;
	/**
	 * 数据库驱动
	 */
	private String driverClassName;
	/**
	 * 数据库地址
	 */
	private String url;
	/**
	 * 连接名称
	 */
	private String name;
	/**
	 * 主机名或IP地址
	 */
	private String host;
	/**
	 * 端口号默认3306
	 */
	private Integer port;
	/**
	 * 用户名
	 */
	private String username;
	/**
	 * 密码
	 */
	private String password;
	/**
	 * 数据库名
	 */
	private String dataBase;
	/**
	 * 参数
	 */
	private String param;
	/**
	 * 数据源状态：0连接失败，1连接成功
	 */
	private String state;
	/**
	 * 删除标志（0代表存在 1代表删除）
	 */
	private String delFlag;
	/**
	 * 创建者
	 */
	private String createBy;
	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;
	/**
	 * 更新者
	 */
	private String updateBy;
	/**
	 * 更新时间
	 */
	private LocalDateTime updateTime;
	/**
	 * 备注
	 */
	private String remark;

}
