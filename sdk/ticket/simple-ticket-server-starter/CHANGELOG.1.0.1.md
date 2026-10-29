# CHANGELOG - simple-ticket-server-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Patch / HTTP 错误契约与附件配置校验
- 基线版本：`1.0.0`

## 变更明细

- 请求体、请求参数、请求头解析失败统一返回 HTTP 400 与安全的 `{message,time}` 响应体，不再落到框架默认错误响应。
- 控制器未预期异常统一返回 HTTP 500 与固定安全文案；响应不回显请求正文、令牌或底层异常消息。
- 异常处理器仅作用于 Ticket 自有 Controller，不改变宿主其他接口的全局异常处理。
- 启用 S3 附件时启动即校验 `attachment.bucket` 非空，避免错误配置延迟到首个附件请求才失败。
- 存量库迁移门禁补齐旧幂等列、附件历史重复对象键和唯一索引状态检查；不满足前置条件时停止执行。

## 兼容性

- 领域模型、公开端点、权限码、配置键和数据库结构保持不变。
- `simple-ticket-server-core` 继续使用 `1.0.0`；调用方只需将 `simple-ticket-server-starter` 升级到 `1.0.1`。

## 验证

- 在 Gradle 8.5 / Zulu 11 构建进程下，Ticket 测试 JVM 固定 Java 8。
- `simple-ticket-server-core:test`、`simple-ticket-server-starter:test` 与 `simple-ticket-iam-adaptor-starter:test` 串行全量通过。
