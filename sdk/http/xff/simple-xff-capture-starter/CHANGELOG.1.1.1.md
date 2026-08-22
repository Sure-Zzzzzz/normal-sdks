# simple-xff-capture-starter 1.1.1 Changelog

## 版本信息

- 版本：`1.1.1`
- 类型：优化 / 排障可观测性增强
- 基线版本：`1.1.0`

## 版本定位

`1.1.1` 不改变 XFF 采集、请求数据快照或事件契约，只为定位入口链路中的 XFF 丢失、请求包装差异和最终事件快照增加默认关闭的生产 `DEBUG` 日志。

## 主要变更

### 三段 XFF 诊断日志

将 `io.github.surezzzzzz.sdk.http.xff` logger 调整为 `DEBUG` 后，Starter 在一次自动采集中记录：

1. Filter 进入时的原始 Servlet Request 视图；
2. 请求数据准备结束、调用 Capture 前实际传入的 Request 视图；
3. 事件发布前的最终 XFF 快照及 `eventId`。

前两段用于区分入口没有 XFF 与请求包装后 Header 视图变化；第三段可按 `eventId` 与下游 Listener、Provider 和审计文档关联。

### 日志边界

日志仅包含方法、无 query 的 URI、`remoteAddr`、请求对象类型与 identity，以及 XFF 原始 Header 值/拆分链和最终 `present` 状态。不会输出 Query、Body、Cookie、Authorization、JWT、Token、全量 Header 或请求数据快照。

`DEBUG` 未开启时不枚举 XFF Header，不影响既有采集、缓存、事件发布和请求体回放行为。

## 未变更边界

本版本不修改：

- `simple-xff-capture-core` 及其 XFF 事实定义；
- Audit Core、Listener、Persistence Provider、Route 或 Elasticsearch 投影；
- Capture Filter 顺序、路径排除、请求数据配置和 Body 回放语义；
- `XffCaptureService.capture(request)` 同请求快照缓存和最多一次事件发布语义。

## 测试方式

测试配置开启 Starter 主包 DEBUG，真实 HTTP Filter 集成测试和既有服务/请求包装用例通过生产日志观察三个阶段，同时继续以事件与请求行为断言验收；不新增测试专用日志框架或日志捕获逻辑。

## 向后兼容性

- 无新增配置项或依赖。
- DEBUG 默认关闭，未调整 logger 的调用方行为和日志量不变。
- 依旧传递依赖 `io.github.sure-zzzzzz:simple-xff-capture-core:1.1.0`。
