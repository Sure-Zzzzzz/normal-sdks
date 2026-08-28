# simple-application-authorization-core 1.0.1 变更记录

## 时效判定增加默认时钟容差

- `DefaultApplicationAuthorizationEvaluator` 的时效下界判定由零容差改为默认 2 秒时钟容差：判定时刻最多允许早于 `issuedAt` 2 秒，到期上界保持零容差不放宽。
- 修复缺陷：签发端写入 token 的 `iat` 按秒四舍五入可能超前真实签发时刻（实测 314.72s → iat=315），零容差下界导致签发后约 1 秒内发起的 API 请求被误判 `DENY`（HTTP 403）。服务身份"取 token 立即调用"为主路径，问题表现为间歇 403。`iat` 按 RFC 7519 不是有效性下界，判定器不应严于 token 自身的 `nbf` 语义。
- 新增构造器 `DefaultApplicationAuthorizationEvaluator(Clock clock, Duration clockSkew)`；`clockSkew` 为 null 或负数时按模块校验风格抛 `ApplicationAuthorizationException`，`Duration.ZERO` 恢复 1.0.0 零容差严格模式。
- 存量构造器语义不变（默认 `systemUTC` + 默认容差）。

## 兼容性

向后兼容的增量版本：`ApplicationAuthorizationEvaluator` SPI 接口不变，自定义实现不受影响；模块保持 Java 8 纯 Core、零 Spring 依赖不变。默认行为仅放宽时效下界 2 秒，需要恢复严格判定或自定义容差的调用方使用三参构造器。签发端 `iat` 取整偏差为独立缺陷（AKSK Server，另行修复），与本修复各自独立成立。
