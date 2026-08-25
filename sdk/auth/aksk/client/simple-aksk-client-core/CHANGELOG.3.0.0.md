# CHANGELOG - simple-aksk-client-core 3.0.0

## 版本类型

Dependency Update - 跟随 AKSK 3.0 架构升级

## 变更概述

升级 `simple-aksk-core` 至 3.0.0，适配 AKSK Server 3.0 的应用授权自闭环和资源服务侧认证源路由。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-core` | 2.0.0 | 3.0.0 |
| `task-retry-starter` | 1.0.1 | 2.0.0 |

## 兼容性说明

- 除下方"修复"项的重试延迟纠正和无效 Token 响应校验外，本模块无其他行为变更，仅跟随上游协议升级并清理死代码。
- `simple-aksk-core` 3.0.0 移除了 `HeaderConstant` 和 `SecurityContextHelper`，client-core 未依赖这两个类，无影响。
- `task-retry-starter` 2.0.0 公共延迟单位统一为毫秒；本次升级依赖坐标时发现 `TokenRefreshExecutor` 的调用参数仍是 1.0.1 秒语义下的数值，见下方"修复"。
- 无效 Token 响应现在统一返回 `HTTP_RESPONSE_INVALID`；调用方需要确保 OAuth2 Server 返回非空 `access_token` 和正数 `expires_in`。
- 与 `simple-aksk-redis-token-manager:3.0.0` 及 Redis Client Starters 3.0.0 配套使用。

## 修复

- `TokenRefreshExecutor` 升级到 `task-retry-starter:2.0.0` 后，`executeWithRetry` 参数单位从秒变毫秒但数值未换算，导致原本"初始延迟1秒、最大延迟5秒"的重试策略变成"1毫秒/5毫秒"几乎零延迟。现将参数改为 `1000L` 和 `5000L` 毫秒，并提取到常量 `SimpleAkskClientCoreConstant.TOKEN_REFRESH_*` 避免散落魔法数字。
- `TokenRefreshExecutor` 对 OAuth2 Token 响应增加 `access_token` 和 `expires_in` 校验；响应为空、Token 为空或 `expires_in` 缺失/非正数时返回 `HTTP_RESPONSE_INVALID`，且不会执行缓存回调。

## 清理

- 删除 `TokenParseException` 及 `ClientErrorCode.TOKEN_PARSE_FAILED`、`ClientErrorMessage.TOKEN_PARSE_FAILED`：2.0.0 移除 JWT 解析能力后这三个符号不再被使用，确认全仓库零引用后删除。

## 测试

- 新增 `TokenRefreshRetryParametersTest`：写死数值断言验证 `TOKEN_REFRESH_*` 常量的毫秒级取值，并用测试专用 `TestRetrySleeper` 捕获真实重试延迟序列 `[1000ms, 1500ms]`，防止未来再次出现秒/毫秒单位换算错误。
- 补充真实本地 HTTP 响应边界测试：`expires_in` 缺失时返回 `HTTP_RESPONSE_INVALID`，不调用缓存回调。

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-client-core:3.0.0'
```

无需修改业务代码。
