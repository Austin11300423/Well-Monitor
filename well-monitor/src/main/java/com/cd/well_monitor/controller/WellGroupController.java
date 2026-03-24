package com.cd.well_monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWellConnection;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.mapper.SysWellConnectionMapper;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import com.cd.well_monitor.vo.WellGroupAnalysisVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/group")
public class WellGroupController {

    @Autowired
    private SysWellConnectionMapper connectionMapper;

    @Autowired
    private SysWellProductionMapper productionMapper;

    @GetMapping("/analysis")
    public Map<String, Object> getGroupAnalysis() {
        List<SysWellConnection> connections = connectionMapper.selectList(null);

        Map<String, List<SysWellConnection>> groupMap = connections.stream()
                .filter(c -> c.getWaterWellId() != null)
                .collect(Collectors.groupingBy(SysWellConnection::getWaterWellId));

        List<WellGroupAnalysisVO> resultList = new ArrayList<>();

        for (Map.Entry<String, List<SysWellConnection>> entry : groupMap.entrySet()) {
            String waterWellId = entry.getKey();
            List<SysWellConnection> rels = entry.getValue();

            WellGroupAnalysisVO vo = new WellGroupAnalysisVO();
            vo.setCenterWaterWell(waterWellId);

            String gName = rels.get(0).getGroupName();
            vo.setGroupName((gName != null && !gName.trim().isEmpty()) ? gName : waterWellId + " 默认井组");

            List<String> oilWells = rels.stream()
                    .map(SysWellConnection::getOilWellId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            vo.setConnectedOilWells(oilWells);

            SysWellProduction waterProd = getLatestProduction(waterWellId);
            double injectVol = (waterProd != null && waterProd.getInjectVol() != null) ? waterProd.getInjectVol() : 0.0;
            vo.setTotalInjectVol(injectVol);

            double totalLiq = 0.0;
            double totalOil = 0.0;
            for (String oilWellId : oilWells) {
                SysWellProduction oilProd = getLatestProduction(oilWellId);
                if (oilProd != null) {
                    totalLiq += (oilProd.getLiquidVol() != null ? oilProd.getLiquidVol() : 0.0);
                    totalOil += (oilProd.getOilVol() != null ? oilProd.getOilVol() : 0.0);
                }
            }
            vo.setTotalLiquidVol(totalLiq);
            vo.setTotalOilVol(totalOil);

            double ipr = totalLiq > 0 ? injectVol / totalLiq : 0.0;
            vo.setInjectionProductionRatio(Double.parseDouble(String.format("%.2f", ipr)));

            double wc = totalLiq > 0 ? ((totalLiq - totalOil) / totalLiq) * 100.0 : 0.0;
            vo.setComprehensiveWaterCut(Double.parseDouble(String.format("%.2f", wc)));

            resultList.add(vo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", resultList);
        response.put("msg", "井组拓扑数据与生产指标升维计算成功");
        return response;
    }

    // 🌟 核心修复：专为编辑井组大屏打造的统一保存接口，彻底接管老接口
    @PostMapping("/save")
    public Map<String, Object> saveGroupTopology(@RequestBody Map<String, Object> payload) {
        String waterWellId = (String) payload.get("waterWellId");
        String groupName = (String) payload.get("groupName");
        List<String> oilWellIds = (List<String>) payload.get("oilWellIds");

        // 1. 删除这口水井原有的所有旧关联
        QueryWrapper<SysWellConnection> query = new QueryWrapper<>();
        query.eq("WATER_WELL_ID", waterWellId);
        connectionMapper.delete(query);

        // 2. 重新插入全新的关联，并同步写入组名
        if (oilWellIds != null && !oilWellIds.isEmpty()) {
            for (String oilId : oilWellIds) {
                SysWellConnection conn = new SysWellConnection();
                conn.setWaterWellId(waterWellId);
                conn.setOilWellId(oilId);
                conn.setGroupName(groupName);
                connectionMapper.insert(conn);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "井组拓扑关系与名称保存成功！");
        return response;
    }

    private SysWellProduction getLatestProduction(String wellId) {
        QueryWrapper<SysWellProduction> query = new QueryWrapper<>();
        query.eq("WELL_ID", wellId).orderByDesc("RECORD_DATE");
        List<SysWellProduction> list = productionMapper.selectList(query);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
}