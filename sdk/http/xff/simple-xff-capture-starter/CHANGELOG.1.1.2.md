# simple-xff-capture-starter 1.1.2 Changelog

## 发布信息

- 版本：`1.1.2`
- 类型：维护 / Forwarded Header 部署语义澄清
- 基线版本：`1.1.1`

## 主要变更

- 传递依赖升级为 `simple-xff-capture-core:1.1.1`。
- 补充 `server.forward-headers-strategy` 对 Capture Filter 可见 Servlet 请求视图的影响说明。
- 明确 `none`、`native`、`framework` 是部署侧策略选择：Capture 只记录 Filter 实际可见的 `remoteAddr` 与 Header 事实，不自动改写策略，也不从 `remoteAddr` 恢复已被容器处理的 XFF。
- 增加 `none`、`native`、`framework` 三种显式策略的真实 HTTP 集成测试，并纳入 Spring Boot 2.7.9、2.4.5、2.3.12.RELEASE、2.2.13.RELEASE 完整模块测试矩阵。

## 未变更边界

- 不改变 Filter 顺序、XFF 采集、请求数据准备、事件缓存、事件发布或 DEBUG 日志行为。
- 不模拟或依赖 Kubernetes，不新增 Tomcat 专用能力、代理信任策略或生产配置。
