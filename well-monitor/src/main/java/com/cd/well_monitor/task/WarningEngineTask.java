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

import java.util.*;
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
        System.out.println("🚀 [智能预警引擎 V3.0] 启动多维特征交叉诊断与拓扑风险评估...");
        System.out.println("=======================================================");

        List<SysWellInfo> wells = wellInfoMapper.selectList(null);
        int singleAnomalyCount = 0;
        int groupAnomalyCount = 0;

        Map<String, SysWellProduction> latestDataMap = new HashMap<>();

        // =======================================================
        // 💥 第一层：单井节点级扫描 (基于动态基线与专家诊断系统)
        // =======================================================
        for (SysWellInfo well : wells) {
            QueryWrapper<SysWellProduction> query = new QueryWrapper<>();
            query.eq("WELL_ID", well.getWellId()).orderByDesc("RECORD_DATE");
            // 提取过去 10 条数据，用于计算动态基线(3σ)和一阶导数(斜率)
            query.last("FETCH NEXT 10 ROWS ONLY");
            List<SysWellProduction> historyList = productionMapper.selectList(query);

            if (historyList == null || historyList.isEmpty()) continue;

            // 缓存最新一条给井组拓扑使用
            latestDataMap.put(well.getWellId(), historyList.get(0));

            // 执行高级智能诊断
            if (historyList.size() >= 3 && checkAdvancedAnomaly(well, historyList)) {
                singleAnomalyCount++;
            }
        }

        // =======================================================
        // 💥 第二层：井组网络级扫描 (保留上一版优秀的注采比逻辑)
        // =======================================================
        List<SysWellConnection> connections = connectionMapper.selectList(null);
        Map<String, List<SysWellConnection>> groupMap = connections.stream()
                .filter(c -> c.getWaterWellId() != null)
                .collect(Collectors.groupingBy(SysWellConnection::getWaterWellId));

        for (Map.Entry<String, List<SysWellConnection>> entry : groupMap.entrySet()) {
            String waterWellId = entry.getKey();
            List<SysWellConnection> rels = entry.getValue();

            String groupName = rels.get(0).getGroupName();
            if (groupName == null || groupName.trim().isEmpty()) groupName = waterWellId + " 井组";

            SysWellProduction waterProd = latestDataMap.get(waterWellId);
            double totalInject = (waterProd != null && waterProd.getInjectVol() != null) ? waterProd.getInjectVol() : 0.0;
            double waterPress = (waterProd != null && waterProd.getPressure() != null) ? waterProd.getPressure() : 0.0;

            double totalLiq = 0.0;
            for (SysWellConnection rel : rels) {
                SysWellProduction oilProd = latestDataMap.get(rel.getOilWellId());
                if (oilProd != null && oilProd.getLiquidVol() != null) totalLiq += oilProd.getLiquidVol();
            }

            double ipr = totalLiq > 0 ? totalInject / totalLiq : 0.0;

            if (waterPress < 2.0 || waterProd == null) {
                String msg = String.format("【心脏骤停】中心水井(%s)严重失压，地层驱动能量中断，全组面临停产风险！", waterWellId);
                if (saveWarningLog(groupName, "紧急异常", msg, 2)) groupAnomalyCount++;
            } else if (ipr > 0 && ipr < 0.5 && totalLiq > 5.0) {
                String msg = String.format("【能量亏空】综合注采比跌至 %.2f，地层能量补充严重不足，存在大面积减产风险！", ipr);
                if (saveWarningLog(groupName, "严重异常", msg, 2)) groupAnomalyCount++;
            } else if (ipr > 2.5 && totalInject > 20.0) {
                String msg = String.format("【注采失衡】综合注采比高达 %.2f，存在严重的水窜风险及注水无效损耗！", ipr);
                if (saveWarningLog(groupName, "严重异常", msg, 2)) groupAnomalyCount++;
            }
        }

        System.out.println("-------------------------------------------------------");
        System.out.println("📊 扫描完成！[单井异常: " + singleAnomalyCount + " 起] | [井组预警: " + groupAnomalyCount + " 起]");
        System.out.println("=======================================================\n");
    }

    // ==========================================================
    // 🧠 核心：基于多维特征融合与动态基线的智能异常诊断模型
    // ==========================================================
    private boolean checkAdvancedAnomaly(SysWellInfo well, List<SysWellProduction> history) {
        SysWellProduction current = history.get(0);
        SysWellProduction previous = history.get(1);

        // 1. 数据清洗与提取
        double currLiq = safeGet(current.getLiquidVol());
        double prevLiq = safeGet(previous.getLiquidVol());
        double currPress = safeGet(current.getPressure());
        double prevPress = safeGet(previous.getPressure());
        double currWc = safeGet(current.getWaterCut());

        // 计算近期数据的均值与标准差 (排除当前点，构建动态基线)
        List<Double> pressHistory = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) pressHistory.add(safeGet(history.get(i).getPressure()));
        double pressMean = getMean(pressHistory);
        double pressStdDev = getStdDev(pressHistory, pressMean);

        // 2. 特征工程 (计算变化率/斜率)
        double liqDropRate = prevLiq > 0 ? (prevLiq - currLiq) / prevLiq : 0;
        double pressChangeRate = prevPress > 0 ? (currPress - prevPress) / prevPress : 0;

        boolean hasAnomaly = false;
        String warningContent = "";
        String level = "一般异常";

        // ======================= 【油井专家诊断库】 =======================
        if ("油井".equals(well.getWellType())) {

            // 💡 特征交叉 A：产液骤降 + 压力飙升 = 井筒结蜡/堵塞憋压
            if (liqDropRate > 0.3 && pressChangeRate > 0.2) {
                hasAnomaly = true;
                warningContent = String.format("【多维诊断】产液量剧减 %.1f%% 且井口憋压上升 %.1f%%，疑似井筒严重结蜡或管线堵塞！", liqDropRate * 100, pressChangeRate * 100);
                level = "紧急异常";
            }
            // 💡 特征交叉 B：产液骤降 + 压力下降 = 地层能量不足或抽油机漏失
            else if (liqDropRate > 0.3 && pressChangeRate < -0.15) {
                hasAnomaly = true;
                warningContent = String.format("【多维诊断】产液量剧减 %.1f%% 伴随压力下降，疑似地层能量严重亏空或抽油机凡尔漏失！", liqDropRate * 100);
                level = "严重异常";
            }
            // 💡 动态基线 C：含水率绝对值高 + 动态异动 (水窜)
            else if (currWc > 90.0 && currLiq > prevLiq * 1.2) {
                hasAnomaly = true;
                warningContent = "【突发水窜】含水率极高且产液量异常跳增，疑似注水波及爆发强水窜通道！";
                level = "紧急异常";
            }
            // 保底物理死阈值 (防断流)
            else if (currLiq <= 0.5) {
                hasAnomaly = true;
                warningContent = "【物理断流】当前产液量极低，疑似停喷或严重断流";
                level = "严重异常";
            }
        }
        // ======================= 【水井专家诊断库】 =======================
        else {
            // 💡 变化率监测 A：压力断崖式下跌 (管线爆裂)
            if (pressChangeRate < -0.3) {
                hasAnomaly = true;
                warningContent = String.format("【斜率异常】注水压力在极短时间内骤降 %.1f%%，疑似管线穿孔或严重破裂！", Math.abs(pressChangeRate) * 100);
                level = "紧急异常";
            }
            // 💡 动态基线 B：基于 3-Sigma 准则的统计学异常
            else if (pressStdDev > 0.5 && Math.abs(currPress - pressMean) > 3 * pressStdDev) {
                hasAnomaly = true;
                warningContent = String.format("【统计异常(3σ)】当前压力(%.1f)严重偏离近期动态基线(均值%.1f)，属于极小概率离群点故障！", currPress, pressMean);
                level = "一般异常";
            }
            // 保底物理死阈值
            else if (currPress < 3.0) {
                hasAnomaly = true;
                warningContent = "【绝对低压】当前注水压力跌破安全红线，疑似系统大面积失压";
                level = "严重异常";
            }
        }

        if (hasAnomaly) {
            return saveWarningLog(well.getWellId(), level, warningContent, 0);
        }
        return false;
    }

    // ==========================================
    // 辅助工具与数学计算方法
    // ==========================================
    private double safeGet(Double value) {
        return value == null ? 0.0 : value;
    }

    private double getMean(List<Double> data) {
        if (data.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double d : data) sum += d;
        return sum / data.size();
    }

    private double getStdDev(List<Double> data, double mean) {
        if (data.size() < 2) return 0.0;
        double sumSq = 0.0;
        for (double d : data) sumSq += Math.pow(d - mean, 2);
        return Math.sqrt(sumSq / data.size());
    }

    private boolean saveWarningLog(String wellIdOrGroupName, String level, String content, Integer type) {
        QueryWrapper<SysWarningLog> existQuery = new QueryWrapper<>();
        existQuery.eq("WELL_ID", wellIdOrGroupName).eq("WARNING_TYPE", type).eq("STATUS", "未处理");

        if (warningLogMapper.selectCount(existQuery) == 0) {
            SysWarningLog log = new SysWarningLog();
            log.setWellId(wellIdOrGroupName);
            log.setWarningLevel(level);
            log.setWarningContent(content);
            log.setCreateTime(new Date());
            log.setStatus("未处理");
            log.setWarningType(type);
            warningLogMapper.insert(log);

            String icon = type == 0 ? "🔴 [单井专家诊断]" : "🟣 [井组网络告警]";
            System.out.println(icon + " -> 对象: " + wellIdOrGroupName + " | 详情: " + content);
            return true;
        }
        return false;
    }
}