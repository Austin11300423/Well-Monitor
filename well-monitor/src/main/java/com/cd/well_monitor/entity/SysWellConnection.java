package com.cd.well_monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

@TableName("SYS_WELL_CONNECTION")
public class SysWellConnection {

    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    // 🌟 恢复为你原本真实的字段：水井 ID
    @TableField("WATER_WELL_ID")
    private String waterWellId;

    // 🌟 恢复为你原本真实的字段：油井 ID
    @TableField("OIL_WELL_ID")
    private String oilWellId;

    // 之前新增的字段：井组名称
    @TableField("GROUP_NAME")
    private String groupName;

    // --- Getter 和 Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWaterWellId() { return waterWellId; }
    public void setWaterWellId(String waterWellId) { this.waterWellId = waterWellId; }

    public String getOilWellId() { return oilWellId; }
    public void setOilWellId(String oilWellId) { this.oilWellId = oilWellId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}