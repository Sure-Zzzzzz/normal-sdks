# simple-xff-capture-core 1.1.0 Changelog

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / 向后兼容契约扩展
- 基线版本：`1.0.0`

## 版本定位

`1.1.0` 为 `XffCaptureEvent` 增加可选请求数据快照能力。Core 只提供稳定的不可变模型和状态契约，不读取 Servlet 请求、不启动 Web 组件，也不扩大 HTTP 采集范围。

## 主要变更

### 1. 请求数据快照契约

新增：

- `RequestDataCaptureStatus`
- `RequestBodyCaptureStatus`
- `RequestParameterSnapshot`
- `RequestBodySnapshot`
- `RequestDataSnapshot`

`XffCaptureEvent` 新增 `requestData`，用于承载 Query、Form 和 Body 三个独立维度的受控快照。参数快照保留重复值和来源维度；Body 快照保存 Content-Type、声明长度、实际保留字节数、UTF-8 文本和明确状态。

状态区分未启用、规则未命中、无数据、已采集、截断、内容类型跳过和读取失败等事实，不用空字符串替代状态。Core 不保存 Servlet 请求、输入流或 Reader。

### 2. 不可变性与日志边界

- 参数 Map 及嵌套 List 在构造时防御性复制并不可变包装。
- 旧构造器继续保留，未提供请求数据时得到全维度 `DISABLED` 快照。
- 参数值和 Body 文本排除在相关模型及事件 `toString()` 之外。
- 请求数据只保留在 `XffCaptureEvent.requestData`，不会自动投影到 Audit、Elasticsearch、Listener、Provider、Route 或 Persistence。

后续如需持久化请求数据，必须另行定义脱敏、字段白名单、访问控制、保留周期和存储映射；Core 1.1.0 不提供隐式持久化协议。

## 依赖变更

未引入 Spring Boot、Spring Web、Servlet、Redis、JDBC 或 Elasticsearch 运行时依赖。生产代码继续以 JDK 8 API 为目标。

## 新增或扩展测试

- Query、Form、Body 完整事件契约与状态边界。
- 重复参数、集合深度不可变和旧构造器兼容。
- 空值、非法状态、长度边界和模块错误码。
- 参数值、Body 文本不出现在 `toString()` 的安全断言。

## 验证结果

本版本已完成 Core 模块完整测试：

- 26 个测试。
- 0 skipped、0 failures、0 errors。
- 主验证基线：Spring Boot `2.7.9`、Gradle `8.5`、Java `11`。
- 生产源代码保持 Java 8 兼容目标。

## 向后兼容性

- 保留 `XffCaptureEvent` 原有构造器和既有 XFF/转发上下文字段。
- 旧构造路径的请求数据状态固定为全维度 `DISABLED`。
- 不改变 XFF Header 与 `applicationRawRemoteAddress` 的独立含义。
- 不改变现有 IP 字面量解析、规范化和地址分类契约。

## 模块协作

Starter 在当前仓库构建中通过 Gradle `project(':sdk:http:xff:simple-xff-capture-core')` 使用 Core 源码，确保请求数据事件契约与 Starter 采集实现同步验证。外部依赖坐标由 Starter 的独立版本变更维护。