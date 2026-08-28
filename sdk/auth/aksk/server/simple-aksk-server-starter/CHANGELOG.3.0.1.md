# CHANGELOG - simple-aksk-server-starter 3.0.1

## 发布日期

2026-08-28

## 版本类型

Patch Release - 内省时间 claim 整秒截断 + Illegal 异常清理 + 授权 core 升级、旧 E2E Server 移除与 Admin 导航调整

## 变更概述

本版本包含两项修复与三项维护：

1. 内省时间 claim 整秒截断。签发时刻在存储中携带亚秒（实测 314.72s），此前组装 `iat` / `exp` / `nbf` 等 Instant 型 claim 时按秒输出被四舍五入为 315，得到超前真实签发时刻的 `iat`；下游零容差时效判定（`now < issuedAt` 即拒绝）在签发后约 1 秒内使用令牌时会按秒内分数概率间歇失败（表现为间歇 403）。修复方式：`AkskIntrospectionResponseHandler` 在组装响应 claim 前统一 `truncatedTo(ChronoUnit.SECONDS)` 截断到整秒，保证 `iat` 永不晚于真实签发时刻；`nbf` 仅统一秒表示，`nbf = iat - 1` 取值逻辑保持不变。
2. 按《SDK 开发规范》第 6 章异常体系清理 main 源码中直接抛出的 JDK Illegal 系异常，统一收敛到 `SimpleAkskServerException` 体系。
3. `simple-application-authorization-core` 依赖 1.0.0 升级到 1.0.1：该版本为授权时效判定引入默认 2 秒时钟容差，与内省截断共同消除签发后立即使用令牌的间歇拒绝。
4. 移除模块内旧的协作 E2E Server 机制（`e2eServer` source set 与 `e2eServerBootJar` 任务）。
5. Admin 侧边栏导航调整：应用授权归位为 Client 级功能入口。

## 变更详情

### 修复：内省时间 claim 截断到整秒

- 顶层 `iat` / `exp` 输出前截断亚秒；四舍五入带来的未来秒不再出现。
- `nbf` 若存在，同样截断亚秒，取值逻辑不变。
- `aksk_authorization` 快照的 `issued_at` / `expires_at` 与顶层 `iat` / `exp` 保持一致：授权快照组装改用截断后的整秒值。
- Token 签发、授权投影与撤销逻辑不变。

### 清理：Illegal 系异常收敛到模块异常体系

- 新增 `ValidationException`（`VALIDATION_001`），承接参数与数据校验失败。
- `AdminController` DATA 授权 / 约束校验 4 处 `IllegalArgumentException` 改为 `ValidationException`；Admin 保存流程按异常 message 前缀映射错误提示，行为不变。
- `AkskApplicationAuthorizationJsonCodec` 的数据无效工厂改为 `ValidationException`，对应单元测试断言同步更新。
- `TokenManagementServiceImpl` 撤销事件审计数据缺失改抛 `SimpleAkskServerException`（`TOKEN_OPERATION_FAILED`）。
- `ManagementApiAuthorizationHelper` 授权计划缺失改抛 `ConfigurationException`（`CONFIG_VALIDATION_FAILED`）。

### 升级：simple-application-authorization-core 1.0.1

- 授权时效判定引入默认 2 秒时钟容差（仅放宽 `issuedAt` 下界，`expiresAt` 上界零容差），可在授权 core 侧配置关闭恢复严格模式。
- 与内省整秒截断叠加，签发后立即使用令牌不再间歇拒绝。

### 移除：协作 E2E Server 机制

- 删除 `e2eServer` source set、`e2eServerBootJar` 任务及对应源码与配置；IAM / AKSK 协作验收由协作 demo 仓库自管，不再随本模块发布验证 Server。
- 正常接入与模块测试不涉及该机制，行为不变。

### 调整：Admin 侧边栏应用授权入口归位

- 侧边栏移除"应用授权"平级导航项及其在无 Client 上下文页面上的禁用态展示。
- 应用授权是 Client 级功能，统一从 Client 详情页"管理应用授权"进入；授权页侧边栏高亮 Client 管理域，页头保留"返回 Client 详情"链接。
- 授权页功能与保存行为不变。

## 兼容性

- 内省响应的 `iat` / `exp` / `nbf` 数值语义整体不早于（等于或早于）原值最多 1 秒内的亚秒差；对按秒比较的消费方兼容。
- 下游零容差时效判定在签发后立即使用令牌的场景不再间歇拒绝。
- 上述异常均为 `RuntimeException` 子类，捕获 `RuntimeException` / `Exception` 的调用方行为不变；仅精确捕获 JDK Illegal 异常类型的调用方需同步调整。
- 依赖 `simple-aksk-e2e-server.jar` 做协作验证的使用方需改用协作 demo 仓库自管的方式。
