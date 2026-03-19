package com.cd.well_monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cd.well_monitor.entity.SysWellProduction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface SysWellProductionMapper extends BaseMapper<SysWellProduction> {

    /**
     * 🌟 聚合查询：按天取平均值，并支持动态时间段筛选
     * 使用了 MyBatis 的 <script> 标签实现动态 SQL 拼接
     */
    @Select("<script>" +
            "SELECT " +
            "  WELL_ID as wellId, " +
            "  TRUNC(RECORD_DATE) as recordDate, " +
            "  AVG(LIQUID_VOL) as liquidVol, " +
            "  AVG(WATER_CUT) as waterCut, " +
            "  AVG(LIQUID_VOL * (1 - WATER_CUT / 100)) as oilVol, " + // 实时计算日平均产油量
            "  AVG(INJECT_VOL) as injectVol, " +
            "  AVG(PRESSURE) as pressure " +
            "FROM SYS_WELL_PRODUCTION " +
            "WHERE WELL_ID = #{wellId} " +

            // 🌟 核心修复：把丢失的时间区间判断加回来了！
            // 使用 &gt;= 代表 >=，使用 &lt;= 代表 <= （防止 XML 解析报错）
            "<if test='startDate != null'> AND RECORD_DATE &gt;= #{startDate} </if>" +
            "<if test='endDate != null'> AND RECORD_DATE &lt;= #{endDate} </if>" +

            "GROUP BY WELL_ID, TRUNC(RECORD_DATE) " +
            "ORDER BY recordDate ASC" +
            "</script>")
    List<SysWellProduction> selectDailyAnalysis(
            @Param("wellId") String wellId,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate);
}