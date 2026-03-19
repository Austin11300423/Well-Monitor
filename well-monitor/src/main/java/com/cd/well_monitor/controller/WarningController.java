package com.cd.well_monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWarningLog;
import com.cd.well_monitor.mapper.SysWarningLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/warning")
public class WarningController {

    @Autowired
    private SysWarningLogMapper warningLogMapper;

    // 🌟 获取最新的预警日志，按时间倒序排列
    @GetMapping("/list")
    public List<SysWarningLog> getLatestWarnings() {
        QueryWrapper<SysWarningLog> queryWrapper = new QueryWrapper<>();
        // 按照创建时间倒序，最新的警报在最上面
        queryWrapper.orderByDesc("CREATE_TIME");
        // 为了防止数据量太大，前端拿到数据后可以自己截取前 20 条展示
        return warningLogMapper.selectList(queryWrapper);
    }

    // 🌟 新增：更新预警日志的处理状态和级别
    @PutMapping("/update")
    public String updateWarningLog(@RequestBody SysWarningLog warningLog) {
        // 使用 MyBatis-Plus 自带的 updateById 方法更新数据库记录
        // 确保你的前端传过来的数据包含了 id
        int result = warningLogMapper.updateById(warningLog);
        if (result > 0) {
            return "日志更新成功";
        } else {
            return "日志更新失败，请检查 ID 是否有效";
        }
    }
}