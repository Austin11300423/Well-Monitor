package com.cd.well_monitor.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWarningLog;
import com.cd.well_monitor.entity.SysWellConnection;
import com.cd.well_monitor.entity.SysWellInfo;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.mapper.SysWarningLogMapper;
import com.cd.well_monitor.mapper.SysWellConnectionMapper;
import com.cd.well_monitor.mapper.SysWellInfoMapper;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WarningEngineTask {

    @Autowired
    private SysWellInfoMapper wellInfoMapper;

    @Autowired
    private SysWellProductionMapper productionMapper;

    @Autowired
    private SysWarningLogMapper warningLogMapper;

    @Autowired
    private SysWellConnectionMapper connectionMapper;

    @Scheduled(fixedRate = 30000)
    public void runEngine() {
        System.out.println("\n=======================================================");
        System.out.println("开始扫描最近 1 分钟的数据...");
        System.out.println("=======================================================");

        List<SysWellInfo> wells = wellInfoMapper.selectList(null);
        Map<String, SysWellProduction> latestDataMap = new HashMap<>();

        // 🌟 核心：计算出 1 分钟前的时间节点
        Date oneMinuteAgo = new Date(System.currentTimeMillis() - 60 * 1000);

        for (SysWellInfo well : wells) {
            String wellId = well.getWellId();

            QueryWrapper<SysWellProduction> query = new QueryWrapper<>();
            query.eq("WELL_ID", wellId)
                    .ge("RECORD_DATE", oneMinuteAgo)
                    .orderByDesc("RECORD_DATE");

            List<SysWellProduction> minuteData = productionMapper.selectList(query);


            System.out.printf("   -> [%s] 最近 1 分钟内查到 %d 条数据\n", wellId, minuteData.size());

            if (minuteData == null || minuteData.isEmpty()) {
                continue;
            }

            // 把最新的一条缓存起来，留给后面的“井组分析”用
            latestDataMap.put(wellId, minuteData.get(0));

            boolean hasAnomaly = false;
            String warningContent = "";
            String level = "严重异常";

            // 🌟 简单粗暴第二步：遍历这 1 分钟内的所有数据，只要有一条超标，当场判死刑！
            for (SysWellProduction data : minuteData) {
                if ("油井".equals(well.getWellType())) {
                    double liq = data.getLiquidVol() != null ? data.getLiquidVol() : 0.0;
                    double press = data.getPressure() != null ? data.getPressure() : 0.0;

                    // 油井阈值：产液 <= 5，压力 >= 25
                    if (liq <= 5.0 || press >= 25.0) {
                        hasAnomaly = true;
                        warningContent = String.format("最近1分钟内检测到憋压！产液:%.1f, 压力:%.1f", liq, press);
                        break; // 抓到现行，立刻跳出循环！
                    }
                } else {
                    double inj = data.getInjectVol() != null ? data.getInjectVol() : 0.0;
                    double press = data.getPressure() != null ? data.getPressure() : 0.0;

                    // 水井阈值：注水 <= 10，压力 <= 5
                    if (inj <= 10.0 || press <= 5.0) {
                        hasAnomaly = true;
                        warningContent = String.format("最近1分钟内检测到失压！注水:%.1f, 压力:%.1f", inj, press);
                        level = "紧急异常";
                        break; // 抓到现行，立刻跳出循环！
                    }
                }
            }

            // 🌟 简单粗暴第三步：只要标记了异常，立刻写数据库！
            if (hasAnomaly) {
                handleWarningPersistence(wellId, level, warningContent, 0);
            }
        }

        // ==========================================
        // 扫描井组注采比
        // ==========================================
        checkWellGroupHealth(latestDataMap);

        System.out.println("=======================================================\n");
    }

    private void checkWellGroupHealth(Map<String, SysWellProduction> latestDataMap) {
        List<SysWellConnection> connections = connectionMapper.selectList(null);
        Map<String, List<SysWellConnection>> groupMap = connections.stream()
                .filter(c -> c.getWaterWellId() != null)
                .collect(Collectors.groupingBy(SysWellConnection::getWaterWellId));

        for (Map.Entry<String, List<SysWellConnection>> entry : groupMap.entrySet()) {
            String waterWellId = entry.getKey();
            List<SysWellConnection> rels = entry.getValue();
            String groupName = rels.get(0).getGroupName() != null ? rels.get(0).getGroupName() : waterWellId + "井组";

            SysWellProduction waterData = latestDataMap.get(waterWellId);
            double totalInject = (waterData != null && waterData.getInjectVol() != null) ? waterData.getInjectVol() : 0.0;

            double totalLiquid = 0.0;
            for (SysWellConnection rel : rels) {
                SysWellProduction oilData = latestDataMap.get(rel.getOilWellId());
                if (oilData != null && oilData.getLiquidVol() != null) {
                    totalLiquid += oilData.getLiquidVol();
                }
            }

            if (totalLiquid > 0) {
                double ipr = totalInject / totalLiquid;
                if (ipr < 0.6) {
                    handleWarningPersistence(groupName, "严重异常", "🟥 综合注采比跌至 " + String.format("%.2f", ipr) + "，能量严重亏空！", 2);
                } else if (ipr > 1.6) {
                    handleWarningPersistence(groupName, "严重异常", "🟨 综合注采比高达 " + String.format("%.2f", ipr) + "，存在水窜风险！", 2);
                }
            }
        }
    }

    private void handleWarningPersistence(String targetId, String level, String content, Integer type) {
        QueryWrapper<SysWarningLog> existQuery = new QueryWrapper<>();
        existQuery.eq("WELL_ID", targetId).eq("WARNING_TYPE", type).eq("STATUS", "未处理");
        List<SysWarningLog> existLogs = warningLogMapper.selectList(existQuery);

        if (existLogs == null || existLogs.isEmpty()) {
            SysWarningLog log = new SysWarningLog();
            log.setWellId(targetId);
            log.setWarningLevel(level);
            log.setWarningContent(content);
            log.setCreateTime(new Date());
            log.setStatus("未处理");
            log.setWarningType(type);
            warningLogMapper.insert(log);
            System.out.println("🚨 [新发告警] 目标: " + targetId + " | " + content);
        } else {
            SysWarningLog oldLog = existLogs.get(0);
            oldLog.setWarningContent(content + " (系统持续告警中...)");
            oldLog.setCreateTime(new Date());
            warningLogMapper.updateById(oldLog);
            System.out.println("🔄 [更新告警] 目标: " + targetId + " | " + content);
        }
    }
}