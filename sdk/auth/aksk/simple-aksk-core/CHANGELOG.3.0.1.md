# CHANGELOG - simple-aksk-core 3.0.1

## 发布信息

- 版本：`3.0.1`
- 类型：Patch / 向后兼容协议契约扩展
- 基线版本：`3.0.0`

## 版本定位

支撑 AKSK Server 3.2.0 的 OWNER_INHERITED AKU 协作：授权模式与投影 Claim 是 AKSK Server 写入令牌、资源服务识别三权结论的协议基座，本模块只补齐枚举与常量，不含签发和校验实现。

## 主要变更

- 新增 AKSK 授权模式枚举 `AkskAuthorizationMode`（`STATIC_LEGACY` / `OWNER_INHERITED`），按 SDK 规范提供 code/description、`fromCode()`、`isValid()`、`getAllCodes()`；code 与常量名同形，令牌 Claim 与既有 `name()` 比较语义保持稳定。
- 新增 JWT 授权投影相关 Claim 常量（授权模式、所属人来源、目标应用与四类授权纪元），供资源服务统一识别令牌中的授权结论。

## 依赖变更

无。不引入新的运行时依赖，继续保持 Java 8 编译目标。

## 新增或扩展测试

新增 `AkskAuthorizationModeTest`：覆盖 `fromCode` 忽略大小写与未知值失败关闭、`isValid`/`getAllCodes`、code 与常量名同形的协议稳定性断言。

## 向后兼容性

- 不移除或修改既有公开类型、常量和模型；新增内容均为向后兼容扩展。
- 枚举新增字段与静态方法不影响既有 `name()`/`valueOf()` 用法；`toString()` 返回与常量名同形的 code，字符串形态不变。

## 升级指南

将依赖升级为 `io.github.sure-zzzzzz:simple-aksk-core:3.0.1`。仅使用既有协议字段的调用方无需代码调整。
