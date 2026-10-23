package com.eggbi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.eggbi.model.entity.Chart;
import com.eggbi.service.ChartService;
import com.eggbi.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author betty
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2026-04-26 19:07:19
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




