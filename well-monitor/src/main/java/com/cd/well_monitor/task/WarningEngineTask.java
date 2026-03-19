package com.cd.well_monitor.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.entity.SysWarningLog;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import com.cd.well_monitor.mapper.SysWarningLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Component
public class WarningEngineTask {

    @Autowired
    private SysWellProductionMapper productionMapper;

    @Autowired
    private SysWarningLogMapper warningLogMapper;

    // 🌟 核心引擎：每 30 秒自动执行一次扫描
    @Scheduled(cron = "0/30 * * * * ?")
    public void scanAndWarn() {
        System.out.println("🤖 [智能预警引擎] 开始执行增量动态扫描...");

        // 🌟 核心修复 1：计算“1分钟前”的时间点
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -1);
        Date oneMinuteAgo = calendar.getTime();

        // 🌟 核心修复 2：只查询【最近 1 分钟内】上报的数据，不翻旧账！
        QueryWrapper<SysWellProduction> dataWrapper = new QueryWrapper<>();
        dataWrapper.gt("RECORD_DATE", oneMinuteAgo); // 假设字段名叫 RECORD_DATE

        List<SysWellProduction> recentData = productionMapper.selectList(dataWrapper);

        if (recentData.isEmpty()) {
            System.out.println("ℹ️  最近 1 分钟无新数据，跳过扫描。");
            return;
        }

        for (SysWellProduction data : recentData) {
            String level = "";
            String content = "";

            // ⚠️ 规则 1：高含水 (针对油井)
            if (data.getWaterCut() != null && data.getWaterCut() > 90.0) {
                level = "严重";
                content = "高含水报警！当前含水率: " + data.getWaterCut() + "%";
                saveLogIfNotExists(data.getWellId(), level, content);
            }

            // ⚠️ 规则 2：压力异常
            if (data.getPressure() != null) {
                if (data.getPressure() > 25.0) {
                    level = "严重";
                    content = "井口超压报警！当前压力: " + data.getPressure() + " MPa";
                    saveLogIfNotExists(data.getWellId(), level, content);
                } else if (data.getPressure() > 0 && data.getPressure() < 5.0) {
                    // 加上 > 0 的判断，防止传感器还没开时的 0 值误报
                    level = "警告";
                    content = "压力过低报警！当前压力: " + data.getPressure() + " MPa";
                    saveLogIfNotExists(data.getWellId(), level, content);
                }
            }
        }
        System.out.println("✅ [智能预警引擎] 增量扫描完毕。");
    }

    // 🌟 抽取出的保存逻辑：增加更严谨的查重
    private void saveLogIfNotExists(String wellId, String level, String content) {
        QueryWrapper<SysWarningLog> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("WELL_ID", wellId)
                .eq("STATUS", "未处理")
                .gt("CREATE_TIME", new Date(System.currentTimeMillis() - 600000)); // 10分钟内同口井同错不重复报

        if (warningLogMapper.selectCount(checkWrapper) == 0) {
            SysWarningLog log = new SysWarningLog();
            log.setWellId(wellId);
            log.setWarningLevel(level);
            log.setWarningContent(content);
            log.setCreateTime(new Date());
            log.setStatus("未处理");

            warningLogMapper.insert(log);
            System.out.println("🚨 发现新异常！已写入日志：[" + wellId + "] " + content);
        }
    }
}