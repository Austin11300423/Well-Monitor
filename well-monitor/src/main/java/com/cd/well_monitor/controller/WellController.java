package com.cd.well_monitor.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cd.well_monitor.entity.SysWellConnection;
import com.cd.well_monitor.entity.SysWellInfo;
import com.cd.well_monitor.entity.SysWellProduction;
import com.cd.well_monitor.mapper.SysWellConnectionMapper;
import com.cd.well_monitor.mapper.SysWellInfoMapper;
import com.cd.well_monitor.mapper.SysWellProductionMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/well")
public class WellController {

    @Autowired
    private SysWellInfoMapper sysWellInfoMapper;

    @Autowired
    private SysWellConnectionMapper sysWellConnectionMapper;

    @Autowired
    private SysWellProductionMapper sysWellProductionMapper;

    // ================== 【1. 基础台账 CRUD 接口】 ==================

    @GetMapping("/list-with-data")
    public List<java.util.Map<String, Object>> getWellListWithTodayData() {
        List<SysWellInfo> wells = sysWellInfoMapper.selectList(null);
        List<java.util.Map<String, Object>> result = new ArrayList<>();

        for (SysWellInfo well : wells) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("info", well);

            // 🌟 核心：查询该井今天的聚合数据
            List<SysWellProduction> todayData = sysWellProductionMapper.selectDailyAnalysis(
                    well.getWellId(), new Date(), new Date()
            );

            if (!todayData.isEmpty()) {
                map.put("today", todayData.get(0)); // 只有一行，就是今天的平均值
            } else {
                map.put("today", null);
            }
            result.add(map);
        }
        return result;
    }

    @PostMapping("/add")
    public String addWell(@RequestBody SysWellInfo sysWellInfo) {
        sysWellInfoMapper.insert(sysWellInfo);
        return "新增成功";
    }

    @PutMapping("/update")
    public String updateWell(@RequestBody SysWellInfo sysWellInfo) {
        sysWellInfoMapper.updateById(sysWellInfo);
        return "修改成功";
    }

    @DeleteMapping("/delete/{id}")
    public String deleteWell(@PathVariable("id") String id) {
        QueryWrapper<SysWellConnection> query = new QueryWrapper<>();
        query.eq("WATER_WELL_ID", id).or().eq("OIL_WELL_ID", id);
        sysWellConnectionMapper.delete(query);
        sysWellInfoMapper.deleteById(id);
        return "删除成功，关联关系已同步清理";
    }

    // ================== 【2. Excel 导入导出接口】 ==================

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("油水井导入模板", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), SysWellInfo.class).sheet("模板").doWrite(new ArrayList<>());
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        List<SysWellInfo> list = EasyExcel.read(file.getInputStream())
                .head(SysWellInfo.class)
                .sheet()
                .doReadSync();
        int successCount = 0;
        for (SysWellInfo well : list) {
            try {
                if(well.getWellId() == null) continue;
                sysWellInfoMapper.insert(well);
                successCount++;
            } catch (Exception e) {
                System.out.println("跳过已存在的井号: " + well.getWellId());
            }
        }
        return "导入完毕！成功解析并导入 " + successCount + " 条新数据。";
    }

    // ================== 【3. 生产数据维护接口】 ==================

    @GetMapping("/prod/list")
    public List<SysWellProduction> getProdList() {
        return sysWellProductionMapper.selectList(new QueryWrapper<SysWellProduction>().orderByDesc("RECORD_DATE"));
    }

    @PostMapping("/prod/add")
    public String addProd(@RequestBody SysWellProduction prod) {
        sysWellProductionMapper.insert(prod);
        return "数据录入成功";
    }

    @PutMapping("/prod/update")
    public String updateProd(@RequestBody SysWellProduction prod) {
        sysWellProductionMapper.updateById(prod);
        return "数据修改成功";
    }

    @DeleteMapping("/prod/delete/{id}")
    public String deleteProd(@PathVariable("id") String id) {
        sysWellProductionMapper.deleteById(id);
        return "数据删除成功";
    }

    // ================== 【4. 动态注采关系绑定接口】 ==================

    @GetMapping("/relations")
    public List<SysWellConnection> getWellRelations() {
        return sysWellConnectionMapper.selectList(null);
    }

    @PostMapping("/bindRelations")
    public String bindRelations(@RequestParam("wellId") String wellId,
                                @RequestParam("wellType") String wellType,
                                @RequestBody List<String> targetIds) {
        QueryWrapper<SysWellConnection> query = new QueryWrapper<>();
        if ("水井".equals(wellType)) {
            query.eq("WATER_WELL_ID", wellId);
        } else {
            query.eq("OIL_WELL_ID", wellId);
        }
        sysWellConnectionMapper.delete(query);

        if (targetIds != null && !targetIds.isEmpty()) {
            for (String targetId : targetIds) {
                SysWellConnection conn = new SysWellConnection();
                if ("水井".equals(wellType)) {
                    conn.setWaterWellId(wellId);
                    conn.setOilWellId(targetId);
                } else {
                    conn.setWaterWellId(targetId);
                    conn.setOilWellId(wellId);
                }
                sysWellConnectionMapper.insert(conn);
            }
        }
        return "关系同步成功！";
    }

    // ================== 【5. 历史动态分析接口 (已升级为日聚合模式)】 ==================

    // 🌟 A. 给大屏和地图用的：查询最近的原始瞬时数据 (实时跳动)
    @GetMapping("/prod/history")
    public List<SysWellProduction> getWellHistory(@RequestParam("wellId") String wellId) {
        QueryWrapper<SysWellProduction> qw = new QueryWrapper<>();
        // 取最近的 50 条原始记录，按时间正序排，方便前端画流式折线
        qw.eq("WELL_ID", wellId)
                .orderByDesc("RECORD_DATE")
                .last("FETCH NEXT 50 ROWS ONLY");

        List<SysWellProduction> list = sysWellProductionMapper.selectList(qw);
        // 因为是倒序取的最新的，返回前反转一下变成正序给前端画图
        java.util.Collections.reverse(list);
        return list;
    }

    // 🌟 B. 专门给历史递减分析页面用的：查询按天聚合的平均值
    @GetMapping("/prod/daily")
    public List<SysWellProduction> getDailyHistory(
            @RequestParam("wellId") String wellId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern="yyyy-MM-dd") Date startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern="yyyy-MM-dd") Date endDate) {

        // 调用我们之前在 Mapper 里写的 selectDailyAnalysis
        return sysWellProductionMapper.selectDailyAnalysis(wellId, startDate, endDate);
    }


    @PostMapping("/prod/generate-mock")
    public String generateMockHistory() {
        List<SysWellInfo> wells = sysWellInfoMapper.selectList(null);
        int count = 0;
        Calendar cal = Calendar.getInstance();

        for (SysWellInfo well : wells) {
            boolean isOil = "油井".equals(well.getWellType());
            double currentLiquid = isOil ? 60.0 : 0.0;
            double currentWaterCut = isOil ? 40.0 : 0.0;
            double currentInject = isOil ? 0.0 : 150.0;

            for (int i = 90; i >= 0; i--) {
                cal.setTime(new Date());
                cal.add(Calendar.DATE, -i);

                SysWellProduction prod = new SysWellProduction();
                prod.setWellId(well.getWellId());
                prod.setRecordDate(cal.getTime());

                if (isOil) {
                    currentLiquid = currentLiquid - (Math.random() * 0.3);
                    currentWaterCut = currentWaterCut + (Math.random() * 0.2);
                    prod.setLiquidVol(currentLiquid > 0 ? currentLiquid : 0);
                    prod.setWaterCut(currentWaterCut < 100 ? currentWaterCut : 100.0);
                    prod.setPressure(12.0 + Math.random());
                } else {
                    currentInject = currentInject - (Math.random() * 0.6);
                    prod.setInjectVol(currentInject > 0 ? currentInject : 0);
                    prod.setPressure(18.0 + Math.random());
                }

                sysWellProductionMapper.insert(prod);
                count++;
            }
        }
        return "成功生成 " + count + " 条的模拟历史数据！";
    }

    // ================== 【6. 工业物联网(IIoT)传感器接收接口】 ==================

    @PostMapping("/sensor/upload")
    public String receiveSensorData(@RequestBody SysWellProduction sensorData) {
        sensorData.setRecordDate(new java.util.Date());
        sysWellProductionMapper.insert(sensorData);
        System.out.println("📥 收到现场传感器数据 -> 井号: " + sensorData.getWellId());
        return "200 OK";
    }
}