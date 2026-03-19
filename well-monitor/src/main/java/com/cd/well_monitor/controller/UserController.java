package com.cd.well_monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysUser;
import com.cd.well_monitor.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private SysUserMapper sysUserMapper;

    // 🌟 1. 登录接口 (升级版：加入角色校验) 🌟
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody SysUser loginUser) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        // 严格比对：用户名 + 密码 + 角色 必须全部对应
        queryWrapper.eq("USERNAME", loginUser.getUsername())
                .eq("PASSWORD", loginUser.getPassword())
                .eq("ROLE", loginUser.getRole());

        SysUser user = sysUserMapper.selectOne(queryWrapper);

        if (user != null) {
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("role", user.getRole());
            result.put("realName", user.getRealName());
        } else {
            result.put("success", false);
            result.put("message", "用户名、密码或角色选择错误！");
        }
        return result;
    }

    // 🌟 2. 注册接口 (全新增加) 🌟
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody SysUser newUser) {
        Map<String, Object> result = new HashMap<>();

        // 先检查数据库里是不是已经有这个用户名了
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("USERNAME", newUser.getUsername());
        if (sysUserMapper.selectCount(queryWrapper) > 0) {
            result.put("success", false);
            result.put("message", "注册失败：该用户名已被占用！");
            return result;
        }

        // 没被占用，存入数据库
        sysUserMapper.insert(newUser);
        result.put("success", true);
        result.put("message", "注册成功，请使用新账号登录！");
        return result;
    }
}