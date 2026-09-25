# CHANGELOG 2.0.0

- 发布日期：2026-09-25
- 类型：Breaking Refactor（重构，对外协议零改动）

## 依赖变化

| 依赖 | 口径 | 说明 |
| --- | --- | --- |
| `org.apache.httpcomponents:httpclient:4.5.13` | runtimeOnly | HttpComponents 工厂运行期实现件（编译期零引用） |
| `com.fasterxml.jackson.core:jackson-databind:2.18.6` | implementation | SDK 自维护 ObjectMapper 需运行期在场（非 web 宿主保险） |
| `spring-web` / `spring-context` / `spring-boot-autoconfigure` / `configuration-processor` | compileOnly | 宿主需提供 Spring Boot web 环境 |
| `commons-lang3` | 移除 | 重构后无使用 |

## 变更内容

**问题背景（1.x 线无历史 CHANGELOG，问题基线记录于本文，1.x 文档快照见 README.1.x.md）**：`throws Exception` 无异常体系、加解密失败返回 null 吞异常、PKCS7 分支缺 BouncyCastle 依赖实际不可用、日志打印完整报文（含手机号明文）、URL 魔法分支、字段注入与容器 ObjectMapper、无 enable 开关。

**修复与重构**：

1. **失败语义两分**：SDK 故障（配置/加密/通信）抛 `SmsException`（errorCode+message+cause）；平台业务结果进 `SmsSendResult`（success/resultCode/message/customSmsId，零框架类型），调用方判断——彻底消灭 `throws Exception`；
2. **API 重设计（消费者驱动）**：`sendSingleSms(phone, content[, customSmsId])`、`sendTemplateSms(templateId, phone, variables[, customSmsId])`，customSmsId 可选重载（默认随机，调用方可传业务追踪 ID 做跨层日志关联）；`TemplateSmsIdAndMobile[]` 组装收进内部；响应解析按入口各自处理，删 URL 魔法分支；**回执形态按入口分（平台实测 2026-09-25）：模板接口=回执数组、单条接口=单个回执对象**（1.x 从未解析过单条响应）；
3. **加密/压缩 Helper 化**（support 包，Helper 后缀）：失败抛异常不返回 null；**AES 密钥长度启动校验（16/24/32 字节）**；PKCS7 缺 BouncyCastle 显式抛配置异常（使用方按 README 自引依赖）；
4. **装配规范化**：`SmsProperties` 单一 `@Data`+`enable`（默认 false，`@ConditionalOnProperty`）+CONFIG_PREFIX 常量+默认值全部引用 `SmsConstant`；`SmsComponent` 移 annotation 包；SmsClient 由自动配置 `@Bean` 注册，**构造期完成启动校验**（必填/密钥长度/编码），不合法直接启动失败；
5. **隔离与注入**：自维护 `ObjectMapper`（禁注入容器）；构造注入（RestTemplate/Properties）；
6. **日志边界**：不打手机号（跨层追踪用 customSmsId）；入口 DEBUG（模板 ID+customSmsId）、HTTP DEBUG（状态码+耗时）、解析失败 WARN——三态失败可区分（平台拒绝/SDK 异常/解析失败）；**零审计事件**（审计是业务层概念）；
7. **HTTP 超时**（1.x 无超时的挂死风险修复）：连接/读取超时可配（默认 5000/10000ms，`connect-timeout-ms`/`read-timeout-ms`），smsRestTemplate 工厂统一设置。

**不范围**：B2M 平台对外协议（http 端点、AES/ECB、报文格式）零改动；不加重试/批量等新能力。

## 新增测试

| 测试类 | 说明 |
| --- | --- |
| `SmsCryptoGzipHelperTest` | 加解密 roundtrip（16/24/32 字节密钥全覆盖）/压缩解压 roundtrip、密钥长度校验、PKCS7 缺 Provider 显式失败 |
| `SmsClientPipelineTest` | mock RestTemplate+真实加解密，全链路不出网：成功三态/HTTP 错误/网络失败/非法响应体/构造期校验/请求组装断言（捕获报文解密核对 templateId、手机号、变量、customSmsId、有效期）/gzip=false 分支 |
| `B2mManualSendTest`（@Disabled 手跑） | 平台真实发送联调，凭据走 application-local.yml |

## 向后兼容性

**不兼容**（主版本升级）：方法签名（返回 `SmsSendResult` 替代 `SendTemplateSmsResponse`/void+throws）、异常体系（`SmsException` 替代 `Exception`）、装配条件（`enable` 默认 false——**1.x 无此开关，升级后必须显式 `enable: true` 才装配**）、`SmsComponent` 包位置。

## 升级指南

1. 配置增加 `io.github.surezzzzzz.sdk.b2m.sms.enable: true`（默认 false，不配置则 SmsClient 不装配）；
2. `sendTemplateSms` 改传 `(templateId, phone, variables)`，`TemplateSmsIdAndMobile[]` 不再对外；
3. 返回值从 `SendTemplateSmsResponse`（含 `ResponseEntity`）改为 `SmsSendResult`，判断 `isSuccess()` 与 `getResultCode()`；
4. `throws Exception` 捕获改为 `SmsException`（`getErrorCode()` 定位，码表见 ErrorCode 常量）；
5. 需要跨层日志关联时把业务追踪 ID 传入 customSmsId 重载。
