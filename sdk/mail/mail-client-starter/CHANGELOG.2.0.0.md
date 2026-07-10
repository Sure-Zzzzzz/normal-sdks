# mail-client-starter 2.0.0

## 版本定位

`2.0.0` 是一次破坏性重构版本，统一邮件发送和读取入口，移除 1.x 旧 API，按 normal-sdks 规范补齐模块结构、配置、异常、常量和测试闭环。

## 主要变化

- 新增 `MailSendEngine` 作为邮件发送入口。
- 新增 `MailReadEngine` 作为邮件读取入口。
- 新增普通 SMTP Provider SPI：`MailSenderProvider`。
- 新增 `MailMessageIdGenerator`、`MailMessageParser`、`MailAttachmentStorage` 扩展点。
- 新增单一 `MailProperties` 配置模型，支持 JavaMail 扁平 key 和 YAML 嵌套配置。
- 新增 `MailContentType`、`MailProviderType`、`MailFlag` 枚举。
- 新增 `ErrorCode`、`ErrorMessage` 和模块异常体系。
- 移除 1.x `MailSender`、`MailReader`、`api.endpoint.schema` 旧入口。
- 移除 SendCloud 相关实现和配置，仅保留普通 SMTP / SMTPS。
- 封存 1.x 文档为 `README.1.x.md`，当前 `README.md` 面向 2.0.0。

## 测试验证

- 覆盖配置转换、邮件头解析、消息构建、发送引擎、消息解析、分页默认值等单元测试。
- 覆盖本地 SMTP 发送并通过 IMAPS 从收件箱读回的端到端测试。

## 兼容性说明

- 业务侧需要从 1.x 的 `MailSender` / `MailReader` 迁移到 `MailSendEngine` / `MailReadEngine`。
- 业务侧如果依赖 SendCloud 配置，需要迁移为普通 SMTP / SMTPS 配置。
- 仍保持 Spring Boot 2.x / `javax.mail` 时代兼容，依赖使用 `com.sun.mail:jakarta.mail:1.6.7`。
