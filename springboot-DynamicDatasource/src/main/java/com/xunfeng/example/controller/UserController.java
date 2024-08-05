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
