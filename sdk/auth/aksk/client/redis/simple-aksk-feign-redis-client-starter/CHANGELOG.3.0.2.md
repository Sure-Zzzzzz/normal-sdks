# Changelog - simple-aksk-feign-redis-client-starter 3.0.2

## 变更概述

升级运行时传递的 Redis Token Manager 到 `3.0.2`，使仅引入本 Starter 的调用方默认获得最新 Token 缓存修复。公开 API、配置键和 Feign 拦截器行为不变，属 Patch Release。

## 依赖升级

- `simple-aksk-redis-token-manager` 由 `3.0.1` 升级至 `3.0.2`。
- Token 缓存提前失效、Redis 故障失败关闭及固定分片本地锁由上游 Token Manager 提供；本模块不新增认证头、重试或权限推断行为。

## 维护

- 收紧拦截器日志：仅记录 HTTP 方法、Token 长度和 Authorization 头覆盖标记，不再记录完整 URL 或 URL Query。
- 测试中的 Mockito 初始化改为直接创建 mock，消除废弃 API 使用；不影响调用方运行时行为。

## 兼容性

- `@AkskClientFeignClient`、`AkskFeignConfiguration`、`AkskFeignRequestInterceptor` 与全部配置键不变。
- 调用方无需修改代码或配置；直接使用 `TokenManager` 或 client-core 公共类型时，仍需自行声明对应依赖。
- 完成 Token 获取与受保护接口调用回归验证（14 项测试，零失败）。
