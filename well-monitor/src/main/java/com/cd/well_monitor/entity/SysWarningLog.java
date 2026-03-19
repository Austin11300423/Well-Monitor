package com.cd.well_monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

@TableName("SYS_WARNING_LOG")
public class SysWarningLog {

    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    // 🌟 核心修复：告诉 Spring Boot，把这个 Long 类型的 ID 转成 String 再发给前端
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @TableField("WELL_ID")
    private String wellId;

    @TableField("WARNING_LEVEL")
    private String warningLevel;

    @TableField("WARNING_CONTENT")
    private String warningContent;

    @TableField("CREATE_TIME")
    private Date createTime;

    @TableField("STATUS")
    private String status;

    // --- 快捷 Getter 和 Setter 保持不变 ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWellId() { return wellId; }
    public void setWellId(String wellId) { this.wellId = wellId; }
    public String getWarningLevel() { return warningLevel; }
    public void setWarningLevel(String warningLevel) { this.warningLevel = warningLevel; }
    public String getWarningContent() { return warningContent; }
    public void setWarningContent(String warningContent) { this.warningContent = warningContent; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}