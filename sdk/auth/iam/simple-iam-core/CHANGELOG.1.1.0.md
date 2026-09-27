# CHANGELOG - simple-iam-core 1.1.0

## 发布信息

- 版本：`1.1.0`
- 类型：Minor / 短信投递与主体 ID 生成 SPI
- 基线版本：`1.0.0`

## 版本定位

为 IAM 1.3.0（手机号与 subjectId 线）提供纯接口层契约：本模块保持零实现、零 Spring 强依赖（既有依赖边界测试约束不变），能力由适配器装配带来。

## 主要变更

- 新增 `SmsDeliveryProvider`（短信验证码投递 SPI）：`deliver(phone, code, purpose, expiresInSeconds)` 携带有效期秒数（服务端配置唯一事实源，话术以 `{minutes}` 占位消费）；`supportedRegions()` 自述支持区号（E.164 带 `+`），入口校验与前端区号列表统一消费该声明。投递失败以 `RuntimeException` 抛出、IAM 不自动重试；验证码明文仅投递链内可见，禁入日志。
- 新增 `SubjectIdGenerator`（对外用户主体 ID 生成 SPI）：契约仅返回值非空且长度不超过 `SUBJECT_ID_MAX_LENGTH`；定长 / 前缀 / 字符集等形态约束由接入文档约定，与用户名、手机号、显示名、自增 ID 无推导关系。
- `SimpleIamCoreConstant` 新增 `SUBJECT_ID_MAX_LENGTH=64` 与短信用途契约常量 `SMS_PURPOSE_LOGIN` / `SMS_PURPOSE_BIND` / `SMS_PURPOSE_FORGOT_PASSWORD`（SPI 与 Server 共用，单一事实源）。

## 依赖变更

无（compileOnly 与测试依赖维持 1.0.0 不变）。

## 新增或扩展测试

- 本模块为纯接口层，无新增行为逻辑；依赖边界测试（禁止 Spring / Jackson / server / aksk / resource import）继续覆盖新增源文件，SPI 消费侧行为由 server-starter 1.3.0 的默认实现与短信链路测试承载。

## 向后兼容性

- 纯增量发布：仅新增接口与常量，无既有 API 变更；1.0.0 消费方（captcha adapter、已发版 server 系列按坐标引用）不受影响。

## 升级指南

- 直接升级坐标即可；需要短信或自定义主体 ID 能力时，由部署方装配对应适配器（`simple-iam-sms-b2m-adapter-starter` 等）。
