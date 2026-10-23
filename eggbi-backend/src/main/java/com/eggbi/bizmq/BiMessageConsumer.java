package com.eggbi.bizmq;

import com.eggbi.model.entity.Chart;
import com.rabbitmq.client.Channel;
import com.eggbi.common.ErrorCode;
import com.eggbi.exception.BusinessException;
import com.eggbi.exception.ThrowUtils;
import com.eggbi.manager.AiManager;
import com.eggbi.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


@Slf4j
@Component
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;

    /**
     * RabbitListener：只要指定了RabbitListener注解，就会自动的给你这个方法填充消息message,channel和deliveryTag这样一个对象，
     * 并指定监听哪个消息队列queue和确认方式ackMode 手动manual
     * @param message 消息
     * @param channel rabbitmq客户端,负责和rabbitmq通信的，引入客户端，可以使用channel的ack方法来手动确认和拒绝这条消息
     * @param deliveryTag 在接受消息或拒绝消息的时候，必须要指定要接受或拒绝哪条消息，并且要指定header
     */
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL" )
    @SneakyThrows // 可以把异常给消除掉，但一般不建议这样做，这里是为了测试
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        if (StringUtils.isBlank(message)){
            // multiple: false 每次只确定一条
            // requeue: 该任务失败后是否需要重新放到消息队列中
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if(chart == null){
            // multiple: false 每次只确定一条
            // requeue: 该任务失败后是否需要重新放到消息队列中
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"图表为空");
        }
        // 先修改图表为 “执行中”,等执行成功后，修改为"已完成",保存执行结果,执行失败后，修改状态为“失败”,记录任务失败信息
        // 一般是为了防止同一个任务被多次执行，只要任务处于"执行中"，就不会再变成"wait",防止重复执行，除非有超时时间，超时时间一到还是"执行中"，恢复成"wait"状态
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("running");
        boolean b = chartService.updateById(updateChart);
        if(!b){
            // multiple: false 每次只确定一条
            // requeue: 该任务失败后是否需要重新放到消息队列中
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag,false,false);
            handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
        }
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR,"更新图表执行中状态失败");

        // 获取AI的回答
        String content = aiManager.doChat(buildUserInput(chart));
        // 解析回答内容
        String[] splits = content.split("【【【【【");
        if(splits.length < 3) {
            // multiple: false 每次只确定一条
            // requeue: 该任务失败后是否需要重新放到消息队列中
            // 如果失败，消息拒绝
            handleChartUpdateError(chart.getId(), "AI 生成错误");
            channel.basicNack(deliveryTag,false,false);
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
            // multiple: false 每次只确定一条
            // requeue: 该任务失败后是否需要重新放到消息队列中
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag,false,false);
            handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
        }
        ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR,"图表状态更改失败");

        // multiple: false 每次只确定一条
        // 消息确认
        channel.basicAck(deliveryTag,false);
    }

    private String buildUserInput(Chart chart){
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String chartData = chart.getChartData();


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

        // 拼接分析目标
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)){
            userGoal += ",请使用"+ chartType;
        }
        userInput.append(userGoal).append("\n");

        //用户输入数据:把excel文件转成csv格式
        userInput.append("原始数据:").append("\n")
                .append(chartData).append("\n");

        return userInput.toString();
    }

    private void handleChartUpdateError(long chartId, String execMessage){
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        updateChartResult.setIsDelete(1);
        boolean updateResult = chartService.updateById(updateChartResult);
        if(!updateResult){
            log.error("更新图表为失败状态出现错误"+chartId+","+execMessage);
        }
    }
}
