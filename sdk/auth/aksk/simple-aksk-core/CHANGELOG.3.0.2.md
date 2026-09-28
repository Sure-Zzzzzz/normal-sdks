# CHANGELOG - simple-aksk-core 3.0.2

## 发布信息

- 版本：`3.0.2`
- 类型：Patch / 文档与展示文案中性化
- 基线版本：`3.0.1`

## 版本定位

聚散原则收官：AKSK 协议常量不绑定特定身份厂商。3.0.1 引入 OWNER_INHERITED 协议面时，枚举描述与注释沿用了"IAM"措辞；本版统一为协作 SPI 的正式术语"身份源"（owner source / identity source，见 `simple-owner-authorization-collaboration-core`），与已发布的协作 SPI 1.0.0 对齐。

## 主要变更

- `AkskAuthorizationMode.OWNER_INHERITED` 枚举描述："IAM 所属人授权投影" → "身份源所属人授权投影"；类与枚举值 javadoc 同步中性化。
- `JwtClaimConstant.OWNER_SOURCE_ID` 注释："IAM 稳定所属人来源" → "身份源稳定所属人来源"。
- 测试断言随描述同步更新（`AkskAuthorizationModeTest`）。
- README 版本历史与依赖坐标对齐 3.0.2。

## 兼容性

- 协议零变化：枚举集合、`code` 值（`STATIC_LEGACY` / `OWNER_INHERITED`）、JWT Claim 名全部不变；`description` 仅用于展示，不进入令牌 Claim、introspection 或日志协议值。
- 二进制与源码兼容：无 API 签名变化，3.0.1 消费方可直接升级，亦可继续使用 3.0.1。

## 依赖变更

无。不引入新的运行时依赖，继续保持 Java 8 编译目标。
