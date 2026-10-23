package com.eggbi.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eggbi.annotation.AuthCheck;
import com.eggbi.bizmq.BiMessageProducer;
import com.eggbi.common.BaseResponse;
import com.eggbi.common.DeleteRequest;
import com.eggbi.common.ErrorCode;
import com.eggbi.common.ResultUtils;
import com.eggbi.constant.CommonConstant;
import com.eggbi.constant.UserConstant;
import com.eggbi.exception.BusinessException;
import com.eggbi.exception.ThrowUtils;
import com.eggbi.manager.AiManager;
import com.eggbi.manager.RedisLimiterManager;
import com.eggbi.model.dto.chart.*;
import com.eggbi.model.entity.Chart;
import com.eggbi.model.entity.User;
import com.eggbi.model.vo.BiResponse;
import com.eggbi.service.ChartService;
import com.eggbi.service.UserService;
import com.eggbi.utils.ExcelUtils;
import com.eggbi.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 *
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;
    @Autowired
    private BiMessageProducer biMessageProducer;

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/user/list/page")
    public BaseResponse<Page<Chart>> listUserChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());

        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();

        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    public QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        Long userId = chartQueryRequest.getUserId();
        String goal = chartQueryRequest.getGoal();
        String name = chartQueryRequest.getName();
        String chartType = chartQueryRequest.getChartType();
        String sortOrder = chartQueryRequest.getSortOrder();
        String sortField = chartQueryRequest.getSortField();

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.like(StringUtils.isNotBlank(goal),"goal",goal);
        queryWrapper.like(StringUtils.isNotBlank(name),"name",name);
        queryWrapper.eq(StringUtils.isNotBlank(chartType),"chartType",chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId),"userId",userId);
        queryWrapper.eq("isDelete",false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    /**
     * 智能分析(同步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                             GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal),ErrorCode.PARAMS_ERROR,"目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100,ErrorCode.PARAMS_ERROR,"名称过长");

        // 文件大小
        long size = multipartFile.getSize();
        // 文件名
        String filename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1 * 1024 * 1024;
        // 校验文件大小
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR,"文件大小超过1MB");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(filename);
//        final List<String> validFileSuffixList = Arrays.asList("png","jpg","svg","webp","jpeg");
//        ThrowUtils.throwIf(validFileSuffixList.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀非法");
        final List<String> validFileSuffixList = Arrays.asList("xlsx","xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀非法");

        //登录后才可以使用 调AI
        User loginUser = userService.getLoginUser(request);

        // 在所有的判断条件后，添加限流
        // 可以拿方法名作为限流器的区分，这样每个方法在使用限流器时不会冲突(限流器是全局的)
        redisLimiterManager.doRateLimit("genChartByAi_" + String.valueOf(loginUser.getId()));

        //读取用户                                                                                          上传的excel文件,进行处理
        //1.用户输入
        StringBuilder userInput = new StringBuilder();
        //用户输入系统预设
        userInput.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
                "分析需求：\n" +
                "{数据分析的需求或者目标}\n" +
                "原始数据：\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
                "【【【【【\n" +
                "{前端Echarts V5的option配置对象json代码,合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "【【【【【\n" +
                "{明确的数据分析结论、越详细越好，不要生成多余的注释}\n");

        //用户输入分析目标
        userInput.append("我提供的内容：").append("\n")
                .append("分析需求:").append("\n");
//                .append(goal).append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)){
            userGoal += ",请使用"+ chartType;
        }
        userInput.append(userGoal).append("\n");

        //用户输入数据:把excel文件转成csv格式
        String result = ExcelUtils.excelToCsv(multipartFile);
        userInput.append("原始数据:").append("\n")
                .append(result);


        // 获取AI的回答
        String content = aiManager.doChat(userInput.toString());
        // 解析回答内容
        String[] splits = content.split("【【【【【");
        if(splits.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        // 添加到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult,ErrorCode.SYSTEM_ERROR,"图表保存失败");

        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     *
     * 智能分析(异步)
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal),ErrorCode.PARAMS_ERROR,"目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100,ErrorCode.PARAMS_ERROR,"名称过长");

        // 文件大小
        long size = multipartFile.getSize();
        // 文件名
        String filename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1 * 1024 * 1024;
        // 校验文件大小
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR,"文件大小超过1MB");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(filename);
//        final List<String> validFileSuffixList = Arrays.asList("png","jpg","svg","webp","jpeg");
//        ThrowUtils.throwIf(validFileSuffixList.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀非法");
        final List<String> validFileSuffixList = Arrays.asList("xlsx","xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀非法");
        // TODO 校验文件的内容
        // TODO 校验文件的合规性(建议用第三方的审核功能)

        //登录后才可以使用 调AI
        User loginUser = userService.getLoginUser(request);

        // 在所有的判断条件后，添加限流
        // 可以拿方法名作为限流器的区分，这样每个方法在使用限流器时不会冲突(限流器是全局的)
        redisLimiterManager.doRateLimit("genChartByAi_" + String.valueOf(loginUser.getId()));

        //读取用户上传的excel文件,进行处理
        //1.用户输入
        StringBuilder userInput = new StringBuilder();
        //用户输入系统预设
        userInput.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
                "分析需求：\n" +
                "{数据分析的需求或者目标}\n" +
                "原始数据：\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
                "【【【【【\n" +
                "{前端Echarts V5的option配置对象json代码,合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "【【【【【\n" +
                "{明确的数据分析结论、越详细越好，不要生成多余的注释}\n");

        //用户输入分析目标
        userInput.append("我提供的内容：").append("\n")
                .append("分析需求:").append("\n");
//                .append(goal).append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)){
            userGoal += ",请使用"+ chartType;
        }
        userInput.append(userGoal).append("\n");

        //用户输入数据:把excel文件转成csv格式
        String result = ExcelUtils.excelToCsv(multipartFile);
        userInput.append("原始数据:").append("\n")
                .append(result).append("\n");


        // 添加到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult,ErrorCode.SYSTEM_ERROR,"图表保存失败");


        // todo 建议处理任务队列满了后，抛异常的情况
        CompletableFuture.runAsync(()->{
            // 先修改图表为 “执行中”,等执行成功后，修改为"已完成",保存执行结果,执行失败后，修改状态为“失败”,记录任务失败信息
            // 一般是为了防止同一个任务被多次执行，只要任务处于"执行中"，就不会再变成"wait",防止重复执行，除非有超时时间，超时时间一到还是"执行中"，恢复成"wait"状态
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            if(!b){
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
            }
            ThrowUtils.throwIf(!b,ErrorCode.OPERATION_ERROR,"更新图表执行中状态失败");

            // 获取AI的回答
            String content = aiManager.doChat(userInput.toString());
            // 解析回答内容
            String[] splits = content.split("【【【【【");
            if(splits.length < 3) {
                handleChartUpdateError(chart.getId(), "AI 生成错误");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 生成错误");
            }
            String genChart = splits[1].trim();
            String genResult = splits[2].trim();

            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            // todo 建议状态为枚举值
            updateChartResult.setStatus("succeed");
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            boolean updateResult = chartService.updateById(updateChartResult);
            if(!updateResult){
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }
            ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR,"图表状态更改失败");
        },threadPoolExecutor);


        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }


    /**
     *
     * 智能分析(异步消息队列)
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal),ErrorCode.PARAMS_ERROR,"目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100,ErrorCode.PARAMS_ERROR,"名称过长");

        // 文件大小
        long size = multipartFile.getSize();
        // 文件名
        String filename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1 * 1024 * 1024;
        // 校验文件大小
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR,"文件大小超过1MB");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(filename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx","xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀非法");
        // TODO 校验文件的内容
        // TODO 校验文件的合规性(建议用第三方的审核功能)

        //登录后才可以使用 调AI
        User loginUser = userService.getLoginUser(request);

        // 在所有的判断条件后，添加限流
        // 可以拿方法名作为限流器的区分，这样每个方法在使用限流器时不会冲突(限流器是全局的)
        redisLimiterManager.doRateLimit("genChartByAi_" + String.valueOf(loginUser.getId()));

        String result = ExcelUtils.excelToCsv(multipartFile);

        // 添加到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult,ErrorCode.SYSTEM_ERROR,"图表保存失败");

        Long newChartId = chart.getId();

        // todo 建议处理任务队列满了后，抛异常的情况
        biMessageProducer.sendMessage(String.valueOf(newChartId));


        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }



    private void handleChartUpdateError(long chartId, String execMessage){
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if(!updateResult){
            log.error("更新图表为失败状态出现错误"+chartId+","+execMessage);
        }
    }



}
