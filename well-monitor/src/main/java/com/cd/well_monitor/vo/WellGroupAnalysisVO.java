package com.cd.well_monitor.vo;

import java.util.List;

public class WellGroupAnalysisVO {
    private String groupName;          // 井组名称
    private String centerWaterWell;    // 中心水井
    private List<String> connectedOilWells; // 关联的油井集合

    private Double totalInjectVol;     // 井组总注水量
    private Double totalLiquidVol;     // 井组总产液量
    private Double totalOilVol;        // 井组总产油量

    private Double injectionProductionRatio; // 🌟核心指标：注采比
    private Double comprehensiveWaterCut;    // 🌟核心指标：井组综合含水率

    // --- Getter 和 Setter ---
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getCenterWaterWell() { return centerWaterWell; }
    public void setCenterWaterWell(String centerWaterWell) { this.centerWaterWell = centerWaterWell; }

    public List<String> getConnectedOilWells() { return connectedOilWells; }
    public void setConnectedOilWells(List<String> connectedOilWells) { this.connectedOilWells = connectedOilWells; }

    public Double getTotalInjectVol() { return totalInjectVol; }
    public void setTotalInjectVol(Double totalInjectVol) { this.totalInjectVol = totalInjectVol; }

    public Double getTotalLiquidVol() { return totalLiquidVol; }
    public void setTotalLiquidVol(Double totalLiquidVol) { this.totalLiquidVol = totalLiquidVol; }

    public Double getTotalOilVol() { return totalOilVol; }
    public void setTotalOilVol(Double totalOilVol) { this.totalOilVol = totalOilVol; }

    public Double getInjectionProductionRatio() { return injectionProductionRatio; }
    public void setInjectionProductionRatio(Double injectionProductionRatio) { this.injectionProductionRatio = injectionProductionRatio; }

    public Double getComprehensiveWaterCut() { return comprehensiveWaterCut; }
    public void setComprehensiveWaterCut(Double comprehensiveWaterCut) { this.comprehensiveWaterCut = comprehensiveWaterCut; }
}