package com.cd.well_monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("SYS_WELL_INFO")
public class SysWellInfo {

    // 🌟 核心修复：明确指定这个字段是主键，且是由前端手动输入的！
    @TableId(value = "WELL_ID", type = IdType.INPUT)
    private String wellId;

    @TableField("WELL_TYPE")
    private String wellType;

    @TableField("STATUS")
    private String status;

    @TableField("LONGITUDE")
    private String longitude;

    @TableField("LATITUDE")
    private String latitude;

    // --- Getter 和 Setter ---
    public String getWellId() { return wellId; }
    public void setWellId(String wellId) { this.wellId = wellId; }

    public String getWellType() { return wellType; }
    public void setWellType(String wellType) { this.wellType = wellType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }
}