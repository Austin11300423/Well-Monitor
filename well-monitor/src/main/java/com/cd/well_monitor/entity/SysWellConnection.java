package com.cd.well_monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("SYS_WELL_CONNECTION")
public class SysWellConnection {

    // 🌟 开启雪花算法自动生成ID，完美解决 Oracle 没有自增主键的问题
    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    private Long id;

    // 🌟 强制映射 Oracle 的大写字段，保证传给前端的是标准的 waterWellId
    @TableField("WATER_WELL_ID")
    private String waterWellId;

    @TableField("OIL_WELL_ID")
    private String oilWellId;

    // --- Getter 和 Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWaterWellId() { return waterWellId; }
    public void setWaterWellId(String waterWellId) { this.waterWellId = waterWellId; }
    public String getOilWellId() { return oilWellId; }
    public void setOilWellId(String oilWellId) { this.oilWellId = oilWellId; }
}