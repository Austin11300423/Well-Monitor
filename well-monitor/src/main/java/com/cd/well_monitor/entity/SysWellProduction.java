package com.cd.well_monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat; // 🌟 核心：必须导入这个包
import java.util.Date;

@TableName("SYS_WELL_PRODUCTION")
public class SysWellProduction {

    // 🌟 核心修复：把 19 位数字伪装成字符串发给前端，彻底防止 JS 精度丢失！
    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @TableField("WELL_ID")
    private String wellId;

    @TableField("RECORD_DATE")
    private Date recordDate;

    @TableField("LIQUID_VOL")
    private Double liquidVol;

    @TableField("OIL_VOL")
    private Double oilVol;

    @TableField("WATER_CUT")
    private Double waterCut;

    @TableField("INJECT_VOL")
    private Double injectVol;

    @TableField("PRESSURE")
    private Double pressure;

    // --- Getter 和 Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWellId() { return wellId; }
    public void setWellId(String wellId) { this.wellId = wellId; }
    public Date getRecordDate() { return recordDate; }
    public void setRecordDate(Date recordDate) { this.recordDate = recordDate; }
    public Double getLiquidVol() { return liquidVol; }
    public void setLiquidVol(Double liquidVol) { this.liquidVol = liquidVol; }
    public Double getOilVol() { return oilVol; }
    public void setOilVol(Double oilVol) { this.oilVol = oilVol; }
    public Double getWaterCut() { return waterCut; }
    public void setWaterCut(Double waterCut) { this.waterCut = waterCut; }
    public Double getInjectVol() { return injectVol; }
    public void setInjectVol(Double injectVol) { this.injectVol = injectVol; }
    public Double getPressure() { return pressure; }
    public void setPressure(Double pressure) { this.pressure = pressure; }
}