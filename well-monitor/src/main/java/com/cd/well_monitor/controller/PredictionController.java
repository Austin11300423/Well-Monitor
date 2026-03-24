package com.cd.well_monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import com.cd.well_monitor.utils.PredictAlgorithmUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/well/predict")
public class PredictionController {

    @Autowired
    private SysWellProductionMapper productionMapper;

    @GetMapping("/gm11")
    public Map<String, Object> predictGM11(@RequestParam String wellId, @RequestParam int days) {
        // 1. 获取历史生产数据
        QueryWrapper<SysWellProduction> wrapper = new QueryWrapper<>();
        wrapper.eq("WELL_ID", wellId).orderByAsc("RECORD_DATE");
        List<SysWellProduction> history = productionMapper.selectList(wrapper);

        int sampleSize = Math.min(history.size(), 30);
        double[] liquidData = new double[sampleSize];
        double[] oilData = new double[sampleSize];

        int startIndex = history.size() - sampleSize;
        for (int i = 0; i < sampleSize; i++) {
            SysWellProduction prod = history.get(startIndex + i);
            liquidData[i] = prod.getLiquidVol() == null ? 0.0 : prod.getLiquidVol();
            oilData[i] = prod.getOilVol() == null ? 0.0 : prod.getOilVol();
        }

        // 2. 调用核心算法进行纯数学预测
        double[] predLiquid = PredictAlgorithmUtil.predictGM11(liquidData, days);
        double[] predOil = PredictAlgorithmUtil.predictGM11(oilData, days);

        // 🌟🌟 3. 核心修复：溯源寻找【真正的正常生产基准】，跳过模拟器生成的停机异常 🌟🌟
        double validLastLiq = 10.0; // 兜底安全值
        double validLastOil = 2.0;  // 兜底安全值

        // 倒序遍历，寻找最近一天【非零、非异常】的真实产量
        for (int i = sampleSize - 1; i >= 0; i--) {
            if (liquidData[i] > 1.0 && oilData[i] > 0.1) {
                validLastLiq = liquidData[i];
                validLastOil = oilData[i];
                break; // 找到了正常的基准，立刻停止回溯
            }
        }

        // 用找到的【正常天】计算初始含水率基准，绝不会再是 100%
        double baseWC = ((validLastLiq - validLastOil) / validLastLiq) * 100.0;

        for (int i = 0; i < days; i++) {
            double pLiq = predLiquid[i];

            // 防护 1：如果数学模型把产液量预测得太低（比如跌破基准的50%），强行用平滑衰减接管，防止分母过小导致含水率计算崩溃
            if (pLiq < validLastLiq * 0.5) {
                pLiq = validLastLiq * Math.exp(-0.002 * (i + 1));
                predLiquid[i] = pLiq;
            }

            double pOil = predOil[i];
            double mathWC = pLiq > 0 ? ((pLiq - pOil) / pLiq) * 100.0 : 0;

            // 防护 2：含水率的单日波动包络线（允许微跌，主要防暴涨）
            double baselineWC = baseWC + 0.02; // 预期每天微涨
            double minWC = baselineWC - 0.05;
            double maxWC = baselineWC + 0.15;

            double finalWC = mathWC;
            if (finalWC > maxWC) {
                finalWC = maxWC;
            } else if (finalWC < minWC) {
                finalWC = minWC;
            }

            // 绝对物理兜底：含水率不可能超过 99%，给产油量留一丝生机
            finalWC = Math.min(99.0, Math.max(0.0, finalWC));

            // 根据修正后的合理含水率，反推产油量
            predOil[i] = pLiq * (1.0 - finalWC / 100.0);

            // 更新明天的基准
            baseWC = finalWC;
        }

        // 4. 封装返回格式
        Map<String, Object> data = new HashMap<>();
        data.put("predLiquid", predLiquid);
        data.put("predOil", predOil);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        result.put("msg", "GM(1,1) 物理约束预测成功");

        return result;
    }
}