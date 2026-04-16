package com.cd.well_monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import com.cd.well_monitor.utils.PredictAlgorithmUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/well/predict")
public class PredictionController {

    @Autowired
    private SysWellProductionMapper productionMapper;

    // 初始化一个 RestTemplate，用于发送 HTTP 请求给 Python 微服务
    private final RestTemplate restTemplate = new RestTemplate();

    // ==========================================
    // GM(1,1) 预测接口
    // ==========================================
    @GetMapping("/gm11")
    public Map<String, Object> predictGM11(@RequestParam String wellId, @RequestParam int days) {
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

        double[] predLiquid = PredictAlgorithmUtil.predictGM11(liquidData, days);
        double[] predOil = PredictAlgorithmUtil.predictGM11(oilData, days);

        // 应用物理机理约束
        applyPhysicalConstraints(liquidData, oilData, predLiquid, predOil, sampleSize, days);

        Map<String, Object> data = new HashMap<>();
        data.put("predLiquid", predLiquid);
        data.put("predOil", predOil);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        result.put("msg", "GM(1,1) 物理约束预测成功");

        return result;
    }

    // ==========================================
    // 🌟 LSTM 预测接口 (支持参数透传) 🌟
    // ==========================================
    @GetMapping("/lstm")
    public Map<String, Object> predictLSTM(
            @RequestParam String wellId,
            @RequestParam int days,
            @RequestParam(defaultValue = "7") int timeSteps, // 接收前端传来的时间步
            @RequestParam(defaultValue = "200") int epochs)  // 接收前端传来的训练轮数
    {
        QueryWrapper<SysWellProduction> wrapper = new QueryWrapper<>();
        wrapper.eq("WELL_ID", wellId).orderByAsc("RECORD_DATE");
        List<SysWellProduction> history = productionMapper.selectList(wrapper);

        int sampleSize = Math.min(history.size(), 30);
        List<Double> liquidDataList = new ArrayList<>();
        List<Double> oilDataList = new ArrayList<>();

        int startIndex = history.size() - sampleSize;
        for (int i = 0; i < sampleSize; i++) {
            SysWellProduction prod = history.get(startIndex + i);
            liquidDataList.add(prod.getLiquidVol() == null ? 0.0 : prod.getLiquidVol());
            oilDataList.add(prod.getOilVol() == null ? 0.0 : prod.getOilVol());
        }

        // 打包数据发给 Python
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("well_id", wellId);
        requestBody.put("liquid_data", liquidDataList);
        requestBody.put("oil_data", oilDataList);
        requestBody.put("predict_days", days);
        requestBody.put("time_steps", timeSteps); // 透传给 Python
        requestBody.put("epochs", epochs);        // 透传给 Python

        String pythonApiUrl = "http://localhost:5000/api/ml/lstm/predict";
        Map<String, Object> pythonResponse;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonApiUrl, requestBody, Map.class);
            pythonResponse = response.getBody();
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("msg", "连接 Python 失败: " + e.getMessage());
            return error;
        }

        Map<String, Object> responseData = (Map<String, Object>) pythonResponse.get("data");
        List<Double> predLiquidList = (List<Double>) responseData.get("predLiquid");
        List<Double> predOilList = (List<Double>) responseData.get("predOil");

        double[] predLiquid = predLiquidList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] predOil = predOilList.stream().mapToDouble(Double::doubleValue).toArray();

        double[] liquidDataArr = liquidDataList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] oilDataArr = oilDataList.stream().mapToDouble(Double::doubleValue).toArray();

        // 🌟 应用修复后的约束器 🌟
        applyPhysicalConstraints(liquidDataArr, oilDataArr, predLiquid, predOil, sampleSize, days);

        Map<String, Object> data = new HashMap<>();
        data.put("predLiquid", predLiquid);
        data.put("predOil", predOil);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        result.put("msg", "LSTM 微服务协同预测成功");

        return result;
    }

    // ==========================================
    // 🌟 修复后的物理机理约束层 (解决含水率暴涨Bug) 🌟
    // ==========================================
    private void applyPhysicalConstraints(double[] histLiquid, double[] histOil, double[] predLiquid, double[] predOil, int sampleSize, int days) {
        if (sampleSize == 0) return;

        // 动态追溯最后一次的真实有效数据，彻底弃用硬编码兜底
        double validLastLiq = histLiquid[sampleSize - 1] > 0 ? histLiquid[sampleSize - 1] : 10.0;
        double validLastOil = histOil[sampleSize - 1] > 0 ? histOil[sampleSize - 1] : 5.0;

        for (int i = sampleSize - 1; i >= 0; i--) {
            if (histLiquid[i] > 1.0 && histOil[i] > 0.1) {
                validLastLiq = histLiquid[i];
                validLastOil = histOil[i];
                break;
            }
        }

        // 获取真实的基准含水率
        double baseWC = ((validLastLiq - validLastOil) / validLastLiq) * 100.0;

        for (int i = 0; i < days; i++) {
            double pLiq = predLiquid[i];

            // 防护 1：产液量平滑跌落保护
            if (pLiq < validLastLiq * 0.5) {
                pLiq = validLastLiq * Math.exp(-0.002 * (i + 1));
                predLiquid[i] = pLiq;
            }

            double pOil = predOil[i];
            double mathWC = pLiq > 0 ? ((pLiq - pOil) / pLiq) * 100.0 : 0;

            // 防护 2：严格的每日波动范围限制（含水率每天最多涨 0.5%，最多跌 0.5%）
            double maxWC = baseWC + 0.5;
            double minWC = baseWC - 0.5;

            double finalWC = mathWC;
            if (finalWC > maxWC) finalWC = maxWC;
            else if (finalWC < minWC) finalWC = minWC;

            // 绝对物理兜底
            finalWC = Math.min(99.0, Math.max(0.0, finalWC));

            // 根据平滑处理后的含水率重新校准产油量
            predOil[i] = pLiq * (1.0 - finalWC / 100.0);

            // 将今天的含水率作为明天的基准，继续向后推演
            baseWC = finalWC;
        }
    }
}