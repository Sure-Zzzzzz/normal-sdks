# CHANGELOG - simple-aksk-server-core 3.0.3

## 发布信息

- 版本：`3.0.3`
- 类型：Patch / 向后兼容服务端契约扩展
- 基线版本：`3.0.2`

## 版本定位

支撑 AKSK Server 3.2.0 的 IAM 所属人授权协作：本模块补齐内部读取、增量同步与缓存控制的配置契约，AKU 生命周期与三权管理的共享常量和事件类型，以及令牌缓存键脱敏；签发、同步执行与存储实现都在 Starter。

## 主要变更

- 新增 IAM 所属人授权内部读取配置契约（`IamOwnerAuthorizationReaderConfig`）：默认关闭，含 token/base URI、固定 SERVICE 凭据、超时、重试次数、同步模式与租约参数；密钥仅允许环境变量或密钥系统注入。
- 新增授权同步模式枚举 `AkskOwnerAuthorizationSynchronizationMode` 与 AKU 生命周期动作枚举 `AkskClientLifecycleEventType`，均按 SDK 规范提供 code/description、`fromCode()`、`isValid()`、`getAllCodes()`；code 与常量名同形，配置值与审计值保持稳定。
- 新增 OWNER_INHERITED AKU 的脱敏生命周期事件（`AkskClientLifecycleEvent`）与三权管理、授权投影 Claim 等共享常量；Claim 键统一引用 `simple-aksk-core` 单一事实源。
- Token 缓存键由明文改为 SHA-256 摘要：SmartCache 的锁、失效消息与 DEBUG 日志不再出现 bearer 原文；摘要算法不可用时失败关闭，不退化为明文键。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
| --- | --- | --- |
| `simple-aksk-core` | `3.0.0` | `3.0.1` |

## 新增或扩展测试

- 新增 `RedisKeyHelperTest`：断言 token 缓存键只含稳定摘要、无 bearer 明文。
- 新增 `AkskClientLifecycleEventTypeTest`、`AkskOwnerAuthorizationSynchronizationModeTest`：覆盖 `fromCode` 忽略大小写与未知值失败关闭、code 与常量名同形的稳定性断言。
- 扩展 `SimpleAkskServerPropertiesTest`：覆盖 reader 配置默认值。

## 向后兼容性

- 新增配置均有默认值，不改变未启用 IAM 授权投影同步时的既有行为。
- 不移除或修改既有公开 API；两个新枚举的 code 与常量名同形，`name()`/`valueOf()` 用法不受影响。
- **Token 缓存键摘要化的升级影响**：升级后旧实例写入的明文键全部失配，等效一次性缓存未命中（校验回源数据库，正确性不受影响）；新旧实例混跑的滚动窗口内，跨版本的缓存失效消息互相删不中，建议缩短滚动窗口或升级完成后清理 token 缓存键空间。

## 升级指南

1. 将依赖升级为 `io.github.sure-zzzzzz:simple-aksk-server-core:3.0.3`（同时需要 `simple-aksk-core:3.0.1`）。
2. 实际启用 IAM 协作能力时，由 AKSK Server Starter 配置 IAM 内部读取端点和凭据；默认配置保持关闭，不影响存量部署。
