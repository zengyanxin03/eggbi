import {
  UploadOutlined,
} from '@ant-design/icons';

import {Button, Card, Col, Divider, Form, message, Row, Select, Space, Spin, Upload} from 'antd';
import React, { useState } from 'react';

import TextArea from "antd/es/input/TextArea";
import {genChartByAiAsyncMqUsingPost, genChartByAiUsingPost} from "@/services/eggbi/chartController";
import ReactECharts from 'echarts-for-react';

/**
 * 添加图表页面
 * @constructor
 */
const AddChart: React.FC = () => {

  // 搞一个响应式变量记录返回的数据
  const [chart, setChart] = useState<API.BiResponse>();

  const [option, setOption] = useState<any>();

  const [submitting, setSubmitting] = useState<boolean>(false);

  const onFinish = async (values: any) => {
    // 避免重复提交
    if(submitting) return;
    setSubmitting(true);
    setOption(undefined);
    setChart(undefined);
    // console.log('表单内容: ', values);
    // console.log(values.file)
    const params = {
      ...values,
      file: undefined,
    };
    try {
      const res = await genChartByAiUsingPost(params,{},values.file.file.originFileObj);
      console.log(res);
      if(!res.data){
        message.error("分析失败");
      }else{
        message.success("分析成功");

        const match = (res.data.genChart?? '').match(/var\s+option\s*=\s*(\{[\s\S]*\});?/);
        console.log(match);
        // json字符串转成js对象
        const chartOption =  JSON.parse(res.data.genChart ?? '');
        if(!chartOption){
          throw new Error('图表代码解析错误');
        }else{
          setChart(res.data);
          setOption(chartOption);
        }
      }
    }catch (e: any){
      message.error("分析失败," + e.message);
    }
    setSubmitting(false);
  };

  return (
    <div className="add-chart">
      <Row gutter={24}>
        <Col span={12}>
          <Card title="智能分析">
            <Form
              name="addChart"
              onFinish={onFinish}
              initialValues={{
                'input-number': 3,
                'checkbox-group': ['A', 'B'],
                rate: 3.5,
                'color-picker': null,
              }}
              labelAlign={'left'}
              labelCol={{span: 6}}
            >
              <Form.Item
                name="goal"
                label="分析目标"
                rules={[{ required: true, message: '请输入分析需求' }]}
              >
                <TextArea placeholder="请输入你的分析需求，比如：分析网站用户的增长情况"/>
              </Form.Item>

              <Form.Item name="name" label="图表名称">
                <TextArea placeholder="请输入你的图表名称"/>
              </Form.Item>

              <Form.Item
                name="chartType"
                label="图表类型"
                hasFeedback
              >
                <Select
                  placeholder="Please select a TYPR"
                  options={[
                    { label: '折线图', value: '折线图' },
                    { label: '柱状图', value: '柱状图' },
                    { label: '堆叠图', value: '堆叠图' },
                    { label: '饼图', value: '饼图' },
                    { label: '雷达图', value: '雷达图' },
                  ]}
                />
              </Form.Item>

              <Form.Item
                name="file"
                label="原始数据"
                extra="只支持xlsx格式"
              >
                <Upload name="file" maxCount={1}>
                  <Button icon={<UploadOutlined />}>上传 CSV 文件</Button>
                </Upload>
              </Form.Item>

              <Form.Item wrapperCol={{span: 12 ,offset: 6}}>
                <Space>
                  <Button type="primary" htmlType="submit" loading={submitting} disabled={submitting}>
                    提交
                  </Button>
                  <Button htmlType="reset">重置</Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col span={12}>
          <Card title="分析结论">
            {chart?.genResult ?? <div>请先在左侧进行提交</div>}
            <Spin spinning={submitting}></Spin>
          </Card>
          <Divider type="vertical" />
          <Card title="可视化图表">
            {
              option ? <ReactECharts option={option} /> : <div>请先在左侧提交</div>
            }
            <Spin spinning={submitting}></Spin>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
export default AddChart;
