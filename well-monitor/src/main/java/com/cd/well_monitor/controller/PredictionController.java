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
        // 1. 获取历史生产数据，按时间升序
        QueryWrapper<SysWellProduction> wrapper = new QueryWrapper<>();
        wrapper.eq("WELL_ID", wellId).orderByAsc("RECORD_DATE");
        List<SysWellProduction> history = productionMapper.selectList(wrapper);

        // GM(1,1) 适合短序列预测，提取最近的 30 天数据作为训练样本即可
        int sampleSize = Math.min(history.size(), 30);
        double[] liquidData = new double[sampleSize];
        double[] oilData = new double[sampleSize];

        int startIndex = history.size() - sampleSize;
        for (int i = 0; i < sampleSize; i++) {
            SysWellProduction prod = history.get(startIndex + i);
            liquidData[i] = prod.getLiquidVol() == null ? 0.0 : prod.getLiquidVol();
            oilData[i] = prod.getOilVol() == null ? 0.0 : prod.getOilVol();
        }

        // 2. 调用核心算法工具类进行预测
        double[] predLiquid = PredictAlgorithmUtil.predictGM11(liquidData, days);
        double[] predOil = PredictAlgorithmUtil.predictGM11(oilData, days);

        // 3. 封装返回格式
        Map<String, Object> data = new HashMap<>();
        data.put("predLiquid", predLiquid);
        data.put("predOil", predOil);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        result.put("msg", "GM(1,1) 预测成功");

        return result;
    }
}