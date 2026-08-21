# simple-xff-capture-starter 1.1.0 Changelog

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / 向后兼容能力扩展
- 基线版本：`1.0.1`

## 版本定位

`1.1.0` 在不改变既有 XFF 事实边界的前提下，为业务提供默认关闭、按维度和 URI 规则受控的 Query、Form、Body 请求数据采集能力。请求数据只进入 `XffCaptureEvent.requestData`，本版本不扩展 Audit、Elasticsearch 或其他 Listener/Persistence 模块。

## 主要变更

### 1. 独立请求数据开关

新增 `capture.request-data` 配置：

- `query-parameters.enabled`
- `form-parameters.enabled`
- `body.enabled`
- `body.max-bytes`
- `body.allowed-content-types`

三个维度相互独立，默认全部关闭。启用维度后仍必须命中非空白名单；没有规则命中时不采集请求数据。

### 2. URI 与 HTTP 方法规则

白名单和黑名单均支持 `GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`ALL`。规则：

- 基于去除 Servlet `contextPath` 后的应用内部 URI 匹配；
- 不使用 query string 参与匹配；
- 黑名单优先于白名单；
- 支持用 `/api/orders/**` 覆盖集合，再用 `/api/orders/xxx` 精确排除资源；
- 不将既有 `excluded-path-patterns` 与请求数据规则混为一体。

### 3. 有界 Body 快照与完整回放

- 只读取配置允许的 Content-Type；不允许的类型记录 `CONTENT_TYPE_SKIPPED`，不读取请求体。Form 仅接受精确的 `application/x-www-form-urlencoded` 媒体类型（允许附带媒体类型参数），不会按相似前缀误判。
- `max-bytes` 只限制事件中保留的 UTF-8 前缀，不限制 Controller 实际收到的完整请求体。
- 对需要同时读取 urlencoded Form 和 Body 的请求，使用临时文件保存完整请求体并在下游回放；Filter 链结束后清理临时文件。
- 超过上限记录 `TRUNCATED`，读取失败记录 `READ_FAILED`，不会以拒绝请求或静默吞掉异常来规避问题。
- Form 保留重复参数，并维持 Query 与 Form 同名参数的下游可读性。

### 4. 请求数据安全边界

Capture 不读取 Cookie、Authorization、JWT、Token 等独立请求来源，也不读取 multipart 字段。SDK 无法从任意 Form 字段或 Body 文本中可靠推断密码、令牌、私钥等业务敏感内容；因此业务必须使用 URI 白名单和允许的 Content-Type 选择安全采集范围，不应把敏感请求端点加入白名单。

请求参数和 Body 文本排除在事件 `toString()` 之外。`X-Forwarded-For` 只来自请求 Header，`applicationRawRemoteAddress` 只记录 `request.getRemoteAddr()`，两者不会互相回填。

### 5. Filter 默认顺序

Capture Filter 默认顺序为 `Ordered.LOWEST_PRECEDENCE - 100`，即 `2147483547`；显式 `capture.order` 仍可覆盖。调整顺序不会改变 XFF 事实边界，也不会通过关闭审计、静默忽略异常或回填 `remoteAddr` 规避链路问题。

## 未变更边界

本版本不修改：

- `simple-xff-capture-audit-core` 或 Audit Document；
- `XffCaptureAuditDocumentFactory`、Elasticsearch template 或 Persistence；
- Listener、Provider、Route 的数据投影契约；
- `XffCaptureService.capture(request)` 的显式调用语义；
- XFF Header、转发 Header 与 `applicationRawRemoteAddress` 的事实定义。

## 新增或扩展测试

- 真实随机端口 HTTP E2E 覆盖 Query 重复参数、Form 重复参数、JSON Body 和 `@RequestParam` / `@RequestBody`。
- 真实 HTTP 覆盖 GET、POST、PUT、PATCH、DELETE 规则及黑名单优先级。
- 覆盖 Body 截断后 Controller 仍收到完整请求体、Form 与 Body 同时读取、Query/Form 同名参数、空 Form 和临时文件清理。
- Security 真实 HTTP 集成仅在 Spring Boot `2.7.9` 兼容基线执行；2.2.13、2.3.12.RELEASE、2.4.5 的 Security 测试类在 Spring 上下文装配前按 JUnit 执行条件 skipped，普通 Starter 能力仍完整执行。

## 验证结果

本版本已完成四个 Spring Boot 基线的 Starter 完整测试：

| Spring Boot | Gradle / Java | 普通能力 | Security 集成 | failures | errors |
|---|---|---:|---:|---:|---:|
| `2.7.9` | `8.5 / 11` | 通过 | 执行并通过 | 0 | 0 |
| `2.4.5` | `7.6 / 8` | 通过 | 9 个测试 skipped | 0 | 0 |
| `2.3.12.RELEASE` | `7.6 / 8` | 通过 | 9 个测试 skipped | 0 | 0 |
| `2.2.13.RELEASE` | `7.6 / 8` | 通过 | 9 个测试 skipped | 0 | 0 |

每个矩阵均执行完整模块 `test`，旧版本 Security skipped 不代表普通请求数据、Filter、配置或真实 HTTP 能力跳过。

## 向后兼容性

- 请求数据默认关闭，不改变未配置请求数据采集的既有行为。
- 既有 XFF 配置、路径排除、显式 Capture 服务调用和事件原有字段保持兼容。
- Core 旧构造器和旧事件消费路径继续可用；新增 `requestData` 为加法字段。
- 业务无需为不使用请求数据的既有接口修改 Controller。

## 依赖协作

Starter 通过精确坐标 `io.github.sure-zzzzzz:simple-xff-capture-core:1.1.0` 使用已发布的 Core 契约，调用方通过 Starter 的传递依赖获得 Core。
