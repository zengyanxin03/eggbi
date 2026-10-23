// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** add POST /api/queue/add */
export async function addUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.addUsingPOSTParams,
  options?: { [key: string]: any }
) {
  return request<any>("/api/queue/add", {
    method: "POST",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** get POST /api/queue/get */
export async function getUsingPost(options?: { [key: string]: any }) {
  return request<string>("/api/queue/get", {
    method: "POST",
    ...(options || {}),
  });
}
