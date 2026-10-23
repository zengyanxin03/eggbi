import React, {useEffect, useState} from 'react';
import {listMyChartByPageUsingPost} from "@/services/eggbi/chartController";
import {Avatar, Card, List, message, Result} from "antd";
import ReactECharts from "echarts-for-react";
import {useModel} from "@@/exports";
import Search from "antd/es/input/Search";

const MyChartPage: React.FC = () => {
  const initSearchParams = {
    current: 1,
    pageSize: 4,
    sortField: "createTime",
    sortOrder: "desc",
  };

  // 发送给后端查询的条件
  const [searchParams, setSearchParams] = useState<API.ChartQueryRequest>({...initSearchParams});
  // 需要定义一个变量，把查询出来的数据进行存储
  const [chartList, setChartList] = useState<API.Chart[]>();
  // 拿到值是一个分页对象，还需要有一个总页数
  const [total, setTotal] = useState<number>(0);
  // 图表展示调用接口的时候会比较慢，加上一个待响应的图表比较好看
  const [loading, setLoading] = useState<boolean>(false);
  // 获取登录用户信息
  const {initialState } = useModel('@@initialState');
  console.log(initialState);
  const { currentUser } = initialState ?? {};

  const loadData = async () => {
    setLoading(true);
    try {
      const res = await listMyChartByPageUsingPost(searchParams);
      // 等后端返回结果之后才会执行后面的代码
      if(res.data){
        setChartList(res.data.records ?? [] );
        setTotal(res.data.total ?? 0);
        // 隐藏图表的title
        if(res.data.records) {
          res.data.records.forEach(data =>{
            if(data.status === 'succeed'){
              const chartOption = JSON.parse(data.genChart ?? '{}');
              chartOption.title = undefined;
              data.genChart = JSON.stringify(chartOption);
            }
          })
        }
      }else{
        message.error('获取我的图表失败');
      }
    }catch (e:any) {
      message.error('获取我的图表失败,'+ e.message);
    }
    setLoading(false);
  }

  // 首次页面加载的时候，需要触发加载数据函数的调用
  // 钩子函数：在页面首次渲染的时候，以及这个数组的一些变量发生变化的时候会执行这里面的方法
  // 加个依赖：如果搜索条件发生改变，就会触发该loadData函数的调用
  useEffect(() => {
    loadData();
  }, [searchParams]);

  return (
    <div className="my-chart-page">
      <div>
        <Search placeholder="请输入图表名称" enterButton loading={loading} onSearch={(value)=>{
          // 当用户搜索的词发生改变的时候，我们要把搜索词设置到搜索条件里面(原始的搜索条件+用户输入的搜索词)
          // onSearch方法，当用户点击搜索按钮的时候才触发
          // 设置搜索条件
          setSearchParams({
            ...initSearchParams,
            name: value,
          });
        }} />
      </div>
      <div style={{marginBottom: 16}} />
      {/*<div className="margin-16" />*/}
      <List
        itemLayout="vertical"
        pagination={{
          onChange: (page,pageSize) => {
            setSearchParams({
              // 当前搜索条件的基础上
              ...searchParams,
              current: page,
              pageSize,
            })
          },
          pageSize: searchParams.pageSize,
          current: searchParams.current,
          total: total,
        }}
        loading={loading}
        dataSource={chartList}
        grid={{
          gutter: 16,
          xs: 1,
          sm: 1,
          md: 1,
          lg: 2,
          xl: 2,
          xxl: 2,
        }}
        renderItem={(item) => (
          <List.Item
            key={item.id}
          >
            <Card>
              <List.Item.Meta
                avatar={<Avatar src={currentUser?.userAvatar} />}
                title={item.name}
                description={item.chartType ? ('图表类型:' + item.chartType) : undefined }
              />
              <>
                {
                  item.status === 'wait' && <>
                    <Result
                      status="warning"
                      title="待生成"
                      subTitle={item.execMessage ?? '当前图表生成队列繁忙，请耐心等候'}
                    />
                  </>
                }
                {
                  item.status === 'running' && <>
                    <Result
                      status="info"
                      title="图表生成中"
                      subTitle={item.execMessage}
                    />
                  </>
                }
                {
                  item.status === 'succeed' && <>
                    <div style={{marginBottom: 16}} />
                    {'分析目标:' + item.goal}
                    <div style={{marginBottom: 16}} />
                    <ReactECharts option={ item.genChart && JSON.parse(item.genChart)} />
                  </>
                }
                {
                  item.status === 'failed' && <>
                    <Result
                      status="error"
                      title="图表生成失败"
                      subTitle={item.execMessage}
                    />
                  </>
                }
              </>
            </Card>
          </List.Item>
        )}
      />
      总数：{total}
    </div>
  );
};
export default MyChartPage;
