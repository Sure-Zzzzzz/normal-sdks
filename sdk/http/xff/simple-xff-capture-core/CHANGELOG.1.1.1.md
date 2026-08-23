# simple-xff-capture-core 1.1.1 Changelog

## 发布信息

- 版本：`1.1.1`
- 类型：维护 / 公开字段语义澄清
- 基线版本：`1.1.0`

## 主要变更

- 明确事件语义同时适用于上层自动 Filter 采集和业务显式采集调用：事件只记录采集调用时 Servlet 容器暴露的请求事实。
- 维护 `XffCaptureEvent.applicationRawRemoteAddress` 的公开语义：该字段只表示采集执行时 `HttpServletRequest.getRemoteAddr()` 返回的原始字符串；Servlet 容器可在采集前依据 Forwarded Header 策略改写该值。
- 明确该字段不保证代表 TCP 对端、最终客户端或请求者出口地址，且不属于 XFF 链。
- 明确 `XffChain.present`、`rawHeaderList` 与 `rawList` 只记录采集时可见的 Servlet Header 事实；不恢复更早容器组件已消费的入口 Header。
- 明确 `ForwardedContext` 的 Host、X-Real-IP、X-Forwarded-Host、X-Forwarded-Port、X-Forwarded-Proto 是互不替代的独立原始观察值。
- 将公开说明统一为“采集调用时可见事实”，不把 Core 契约绑定到特定 Filter 实现。

## 未变更边界

- 不改变事件模型、构造器、Header 读取、XFF 拆分、IP 分类或请求数据行为。
- 不新增 Servlet、Spring、Tomcat、代理信任或外部运行时依赖。
- 不从 `remoteAddr` 推导或恢复 XFF。
