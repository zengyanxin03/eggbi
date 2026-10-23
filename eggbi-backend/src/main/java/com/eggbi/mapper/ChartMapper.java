package com.eggbi.mapper;

import com.eggbi.model.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
* @author betty
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2026-04-26 19:07:19
* @Entity com.yupi.eggbi.model.entity.Chart
*/

public interface ChartMapper extends BaseMapper<Chart> {

//    List<Map<String,Object>> queryChartData(@Param("chartId") String chartId);
    List<Map<String,Object>> queryChartData(String querySql);
}




