# Simple Ticket Iam Adaptor Starter

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

工单平台的 IAM 聚形态适配：通过 IAM 客户端契约提供站内信通知和人员姓名解析。适配件不选择 HTTP 传输或出站认证实现。

## 快速上手

```gradle
implementation 'io.github.sure-zzzzzz:simple-ticket-iam-adaptor-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-iam-aksk-resttemplate-client-starter:1.0.3'
```

- **前提**：宿主自行装配 IAM openapi 客户端与 AKSK 出站认证链，提供 `IamMessageClient`、`IamUserClient` 两个 Bean；上例采用 RestTemplate 传输件。
- **装配边界**：引用本适配即注册通知与姓名解析；缺任一客户端 Bean 时启动失败。无需 IAM 集成时不引用本适配，工单 server 使用默认 Noop 实现。
- **AKP 授权**：出站凭据授 `iam:message:api` 与 `iam:user:api`，按 IAM 用户 DATA 范围限定姓名查询；不给 PAGE。

## 适配内容

| SPI | 实现 | IAM openapi 接口 |
| --- | --- | --- |
| `NotificationProvider` | `IamInboxNotificationProvider` | `IamMessageClient.sendMessage`（收件人 subjectIds/title/content） |
| `SubmitterProfileProvider` | `IamUserProfileProvider` | `IamUserClient.getUser`（displayName 为空时回落 subjectId，上游调用失败透传） |

通知失败重试 3 次后日志告警，不阻塞工单主流程；投递结果仅 DEBUG 输出批次与命中数。

## 版本对齐

| 本适配 | IAM 客户端契约 | 示例传输件 | IAM Server |
| --- | --- | --- | --- |
| 1.0.0 | `simple-iam-client-core:1.0.4` | `simple-iam-aksk-resttemplate-client-starter:1.0.3` | ≥ 1.3.8 |

## 验证口径

适配器加载有一次 INFO 日志；以两个 SPI 实现 Bean 均在场且 Noop Bean 缺席作为装配判据，真实投递以 IAM 站内信收件为准。分页形态注意：用户族/应用目录为 Spring Page 形态（页码 0 起），工单未使用该族分页接口。
