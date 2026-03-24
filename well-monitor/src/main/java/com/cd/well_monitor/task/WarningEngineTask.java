package com.cd.well_monitor.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWarningLog;
import com.cd.well_monitor.entity.SysWellInfo;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.mapper.SysWarningLogMapper;
import com.cd.well_monitor.mapper.SysWellInfoMapper;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import com.cd.well_monitor.utils.PredictAlgorithmUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class WarningEngineTask {

    @Autowired
    private SysWellInfoMapper wellInfoMapper;

    @Autowired
    private SysWellProductionMapper productionMapper;

    @Autowired
    private SysWarningLogMapper warningLogMapper;

    @Scheduled(fixedRate = 30000)
    public void runEngine() {
        System.out.println("\n=======================================================");
        System.out.println("🚀 [智能预警引擎] 开始执行全区井位扫描与风险预测...");
        System.out.println("=======================================================");

        List<SysWellInfo> wells = wellInfoMapper.selectList(null);
        int normalCount = 0, anomalyCount = 0, riskCount = 0;

        for (SysWellInfo well : wells) {
            QueryWrapper<SysWellProduction> query = new QueryWrapper<>();
            query.eq("WELL_ID", well.getWellId()).orderByDesc("RECORD_DATE");
            List<SysWellProduction> historyList = productionMapper.selectList(query);

            if (historyList == null || historyList.isEmpty()) {
                System.out.println("⚪ [无数据] 井号: " + well.getWellId() + " 暂无生产动态记录");
                continue;
            }

            SysWellProduction latestData = historyList.get(0);

            // 1. 检查实时异常
            boolean hasAnomaly = checkRealTimeAnomaly(well, latestData);
            boolean hasRisk = false;

            // 2. 如果没有实时异常，且是油井，再检查未来风险
            if (!hasAnomaly && "油井".equals(well.getWellType()) && historyList.size() >= 4) {
                hasRisk = checkPredictiveRisk(well, historyList);
            }

            // 3. 统计并输出简单信息
            if (hasAnomaly) {
                anomalyCount++;
            } else if (hasRisk) {
                riskCount++;
            } else {
                normalCount++;
                printNormalWellInfo(well, latestData);
            }
        }

        System.out.println("-------------------------------------------------------");
        System.out.println("📊 扫描完成！[正常: " + normalCount + " 口] | [实时异常: " + anomalyCount + " 口] | [预测风险: " + riskCount + " 口]");
        System.out.println("=======================================================\n");
    }

    // 打印正常井的精简信息
    private void printNormalWellInfo(SysWellInfo well, SysWellProduction latest) {
        if ("油井".equals(well.getWellType())) {
            double liq = latest.getLiquidVol() != null ? latest.getLiquidVol() : 0.0;
            double wc = latest.getWaterCut() != null ? latest.getWaterCut() : 0.0;
            System.out.printf("✅ [正常] 井号: %-6s | 最新产液量: %-5.1f t/d | 含水率: %.1f%%\n", well.getWellId(), liq, wc);
        } else {
            double press = latest.getPressure() != null ? latest.getPressure() : 0.0;
            double inj = latest.getInjectVol() != null ? latest.getInjectVol() : 0.0;
            System.out.printf("✅ [正常] 井号: %-6s | 最新注水压力: %-4.1f MPa | 注水量: %.1f m³/d\n", well.getWellId(), press, inj);
        }
    }

    // 检查实时异常 (改为返回 boolean)
    private boolean checkRealTimeAnomaly(SysWellInfo well, SysWellProduction latest) {
        boolean hasAnomaly = false;
        String warningContent = "";
        String level = "一般告警";

        if ("油井".equals(well.getWellType())) {
            if (latest.getLiquidVol() != null && latest.getLiquidVol() <= 0.5) {
                hasAnomaly = true;
                warningContent = "当前产液量极低，疑似停喷或严重断流";
                level = "严重异常";
            } else if (latest.getWaterCut() != null && latest.getWaterCut() >= 98.0) {
                hasAnomaly = true;
                warningContent = "当前含水率异常突增，疑似发生水窜";
                level = "严重异常";
            }
        } else {
            if (latest.getPressure() != null && latest.getPressure() < 5.0) {
                hasAnomaly = true;
                warningContent = "当前注水压力骤降，疑似管线穿孔或破裂";
                level = "严重异常";
            }
        }

        if (hasAnomaly) {
            QueryWrapper<SysWarningLog> existQuery = new QueryWrapper<>();
            existQuery.eq("WELL_ID", well.getWellId()).eq("WARNING_TYPE", 0).eq("STATUS", "未处理");
            if (warningLogMapper.selectCount(existQuery) == 0) {
                saveWarningLog(well.getWellId(), level, warningContent, 0);
            } else {
                System.out.println("🚨 [实时异常] 井号: " + well.getWellId() + " (已存在未处理工单，拦截重复推送)");
            }
            return true;
        }
        return false;
    }

    // 检查未来风险 (改为返回 boolean)
    private boolean checkPredictiveRisk(SysWellInfo well, List<SysWellProduction> historyDesc) {
        QueryWrapper<SysWarningLog> checkExist = new QueryWrapper<>();
        checkExist.eq("WELL_ID", well.getWellId()).eq("WARNING_TYPE", 1).eq("STATUS", "未处理");
        if (warningLogMapper.selectCount(checkExist) > 0) {
            System.out.println("🔮 [预测风险] 井号: " + well.getWellId() + " (已存在预测风险工单，持续观察中...)");
            return true;
        }

        int sampleSize = Math.min(historyDesc.size(), 15);
        double[] liquidArray = new double[sampleSize];
        double[] oilArray = new double[sampleSize];

        for (int i = 0; i < sampleSize; i++) {
            SysWellProduction prod = historyDesc.get(sampleSize - 1 - i);
            liquidArray[i] = prod.getLiquidVol() == null ? 0 : prod.getLiquidVol();
            oilArray[i] = prod.getOilVol() == null ? 0 : prod.getOilVol();
        }

        int predictDays = 7;
        double[] predLiq = PredictAlgorithmUtil.predictGM11(liquidArray, predictDays);
        double[] predOil = PredictAlgorithmUtil.predictGM11(oilArray, predictDays);
        double currentLiq = liquidArray[sampleSize - 1];

        for (int i = 0; i < predictDays; i++) {
            double l = predLiq[i];
            double o = predOil[i];
            double futureWaterCut = l > 0 ? ((l - o) / l) * 100 : 0;

            if (futureWaterCut > 95.0) {
                String msg = String.format("【风险预测】算法研判未来第 %d 天含水率将突破 95%% (预估值: %.1f%%)，建议提前调剖堵水", i + 1, futureWaterCut);
                saveWarningLog(well.getWellId(), "预测风险", msg, 1);
                return true;
            }
            if (currentLiq > 5.0 && l < currentLiq * 0.7) {
                String msg = String.format("【减产风险】算法研判未来第 %d 天产液量将衰减超 30%% (预估值: %.1f t/d)，建议排查地层能量", i + 1, l);
                saveWarningLog(well.getWellId(), "预测风险", msg, 1);
                return true;
            }
        }
        return false;
    }

    private void saveWarningLog(String wellId, String level, String content, Integer type) {
        SysWarningLog log = new SysWarningLog();
        log.setWellId(wellId);
        log.setWarningLevel(level);
        log.setWarningContent(content);
        log.setCreateTime(new Date());
        log.setStatus("未处理");
        log.setWarningType(type);
        warningLogMapper.insert(log);

        String icon = type == 0 ? "🚨 [新异常触发]" : "🔮 [新风险触发]";
        System.out.println(icon + " -> 井号: " + wellId + " | 内容: " + content);
    }
}