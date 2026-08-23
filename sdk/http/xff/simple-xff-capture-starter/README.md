# Simple XFF Capture Starter

面向 Spring MVC 应用的 XFF 事实采集 Starter。它依赖 `simple-xff-capture-core`，自动通过 Servlet Filter 读取应用实际看到的 `X-Forwarded-For` 和固定入口转发上下文，形成 Core 不可变快照并通过 Spring 进程内事件总线发布 `XffCaptureEvent`，不判断真实客户端 IP。

Starter 当前版本为 1.1.2，传递依赖 `simple-xff-capture-core:1.1.1`，调用方无需重复声明 Core。请求数据采集能力已纳入 1.1.0，1.1.1 增加默认关闭的 XFF 入口诊断日志，1.1.2 补充 Forwarded Header 部署语义说明。

## 接入

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-starter:1.1.2'
}
```

### 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
```

最小配置会采集所有首次外部 `REQUEST`。不设置 `excluded-path-patterns` 时，默认空清单，与 1.0.1 的路径覆盖行为一致。

### 完整配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
              # 可选，默认值为 Ordered.LOWEST_PRECEDENCE - 100
              order: 2147483547
              # 可选，默认空清单，支持 Ant 路径模式
              excluded-path-patterns:
                - /actuator/prometheus
                - /actuator/**
```

`order` 用于协调 XFF Filter 与应用自身的 Filter（例如 Spring Security）。省略时使用 `Ordered.LOWEST_PRECEDENCE - 100`，为真正最低优先级的 Filter 保留空间；只有明确掌握应用容器与安全链的实际顺序时才覆盖该值。Capture 不通过降低审计、静默忽略异常或 `remoteAddr` 回填 XFF 来规避顺序问题。

`excluded-path-patterns` 仅作用于自动 Filter 采集。模式按不含 query string、去除 Servlet context path 后的应用内部路径进行 Ant 匹配；命中时请求仍正常进入后续 Filter 和 Controller，但不会自动采集或发布 `XffCaptureEvent`。例如部署 context path 为 `/gateway` 时，`/gateway/actuator/prometheus` 可由 `/actuator/prometheus` 排除。业务代码显式调用 `XffCaptureService.capture(request)` 的语义不受该清单影响。

开启后无需修改 Controller 或 Service。Filter 对首次外部 REQUEST 自动采集；异步、错误、转发等二次 dispatch 不重复发布。

## 请求数据采集（1.1.0）

请求数据采集默认全部关闭，业务方必须按维度和 URI 规则显式开启。Query、Form、Body 三个维度相互独立；没有白名单命中时不采集，黑名单优先于白名单。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              request-data:
                query-parameters:
                  enabled: true
                form-parameters:
                  enabled: true
                body:
                  enabled: true
                  max-bytes: 65536
                  allowed-content-types:
                    - application/json
                    - application/*+json
                    - application/x-www-form-urlencoded
                whitelist:
                  - method: GET
                    path-pattern: /api/orders/**
                  - method: POST
                    path-pattern: /api/orders/**
                  - method: PUT
                    path-pattern: /api/orders/**
                  - method: PATCH
                    path-pattern: /api/orders/**
                  - method: DELETE
                    path-pattern: /api/orders/**
                  - method: ALL
                    path-pattern: /health/**
                blacklist:
                  - method: GET
                    path-pattern: /api/orders/xxx
```

规则使用去除 Servlet `contextPath` 后的应用内部 URI 匹配，Query string 不参与匹配。`/api/orders/**` 可覆盖资源集合，`/api/orders/xxx` 黑名单可以精确排除；黑名单命中时三个维度均记录 `RULE_NOT_MATCHED`。支持 `GET`、`POST`、`PUT`、`PATCH`、`DELETE` 和 `ALL`。

### 采集边界

- Query 参数保留重复值；Form 仅在媒体类型精确为 `application/x-www-form-urlencoded`（允许附带媒体类型参数）时采集参数，不把相似前缀当作 Form。Capture 不读取 Cookie、Authorization、JWT、Token 等独立请求来源，也不读取 multipart 字段；Form 字段和值是否敏感无法由 SDK 推断，业务必须通过 URI 白名单避免采集密码、私钥等敏感字段。
- Body 只读取允许的 Content-Type；不允许的类型记录 `CONTENT_TYPE_SKIPPED`，不会为了采集而读取或破坏业务 Body。允许的 Body 仍可能包含密码、令牌或私钥，业务必须按 URI 和 Content-Type 选择安全范围。
- `max-bytes` 只限制事件快照保留的 UTF-8 前缀；请求仍会回放完整 Body 给 Controller，超限记录 `TRUNCATED`，不会拒绝请求。
- Body 状态还包括 `DISABLED`、`RULE_NOT_MATCHED`、`NO_BODY`、`CAPTURED` 和 `READ_FAILED`；参数状态包括 `DISABLED`、`RULE_NOT_MATCHED`、`ABSENT`、`CAPTURED`、`TRUNCATED` 和 `READ_FAILED`。
- 请求参数和 Body 只进入 `XffCaptureEvent.requestData` 的不可变快照，不自动进入 Audit、Elasticsearch、Listener、Provider、Route 或 Persistence。
- 请求数据及 Body 文本不出现在事件 `toString()` 中；生产环境仍应避免直接打印原始事件字段。

请求数据能力不会改变 XFF 事实边界：`X-Forwarded-For` 只来自请求 Header，`applicationRawRemoteAddress` 只记录 `request.getRemoteAddr()`，两者不会互相回填。

### Spring Boot Forwarded Header 策略

Capture 只记录 Filter 执行时 Servlet 容器暴露的视图，不自动修改 `server.forward-headers-strategy`。以下表格是本 Starter 使用随机端口启动真实 Tomcat、通过真实 HTTP 请求发送 XFF 后，对 Controller 和 Capture Event 同时观察得到的确定结论。表中的“上游连接地址”是当前 Servlet 请求在 Filter 执行时看到的连接地址；它不是 Capture 对生产网络拓扑的推断。

| Spring Boot 基线 | `none` | `native` | `framework` |
| --- | --- | --- | --- |
| `2.2.13.RELEASE` | `remoteAddr` 为上游连接地址；XFF 保留 | `remoteAddr` 被容器归一化；XFF 不在 Filter 视图中 | `remoteAddr` 为上游连接地址；XFF 保留 |
| `2.3.12.RELEASE` | `remoteAddr` 为上游连接地址；XFF 保留 | `remoteAddr` 被容器归一化；XFF 不在 Filter 视图中 | `remoteAddr` 为上游连接地址；XFF 保留 |
| `2.4.5` | `remoteAddr` 为上游连接地址；XFF 保留 | `remoteAddr` 被容器归一化；XFF 不在 Filter 视图中 | Spring 处理请求视图；XFF 不在 Filter 视图中 |
| `2.7.9` | `remoteAddr` 为上游连接地址；XFF 保留 | `remoteAddr` 被容器归一化；XFF 不在 Filter 视图中 | Spring 处理请求视图；XFF 不在 Filter 视图中 |

具体含义如下：

- `none`：容器不处理 Forwarded Header。Controller 和 Capture Filter 都能读取 XFF；`applicationRawRemoteAddress` 记录上游连接地址。
- `native`：由 Servlet 容器处理 Forwarded Header。Tomcat `RemoteIpValve` 会按其配置归一化 `remoteAddr`，并在 Filter 执行前移除已处理的 XFF；Capture 只能如实记录“XFF 不存在”的视图，不能从 `remoteAddr` 恢复 XFF。
- `framework`：由 Spring Framework 处理请求视图。上述矩阵中，2.2.13 和 2.3.12 的 Filter 仍能读取 XFF；2.4.5 和 2.7.9 的 Filter 看不到 XFF。因而不能把 `framework` 在不同 Spring Boot 基线下写成统一的“保留”或“移除”结论。

如果业务需要 Capture 稳定观察入口 XFF，应显式配置 `server.forward-headers-strategy=none`，并确认部署链路没有在应用之前消费 Header。未显式配置时的默认策略属于 Spring Boot、运行环境和容器组合行为；Kubernetes 不是 Capture 的运行前提，不能用 Kubernetes 默认行为替代应用自身的显式配置。

## XFF 入口诊断（1.1.1）

当需要定位 XFF 在入口、请求包装或事件发布前哪个阶段不可见时，临时开启 Starter 主包 DEBUG：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.http.xff: DEBUG
```

一次自动采集会依次输出 `filter-enter`、`capture-before`、`event-publish` 三段日志：前两段展示原始 Servlet Request 与请求数据准备后实际传入 Capture 的 Request 视图，最后一段展示可按 `eventId` 关联下游审计文档的最终 XFF 快照。

日志只记录 HTTP 方法、不含 query 的 URI、`remoteAddr`、请求对象类型与 identity，以及 XFF 原始 Header 值和拆分链；不会记录 Query、Body、Cookie、Authorization、JWT、Token、全量 Header 或请求数据快照。DEBUG 未开启时不会枚举 XFF Header，不改变既有采集和请求体回放行为。

## 业务可选读取

业务确实需要自行选择链中地址时，可以注入 `XffCaptureService`：

```java
XffChain chain = xffCaptureService.capture(request);
List<String> rawList = chain.getRawList();
```

Filter 已采集过的请求会直接返回请求内快照，不重复发事件。

`XffAddressHelper` 提供 `isIpLiteral`、`isPrivateIp` 和 `isPublicIp`。地址判断不参与采集，链中值也不代表可信客户端 IP。

公网分类使用 2025-10-09 更新的 IANA IPv4/IPv6 Special-Purpose Address Registry 固定快照：IPv4 未命中特殊用途表的合法单播默认视为公网；IPv6 以 `2000::/3` 为基础 Global Unicast 范围，并处理 IANA 明确的公网例外和非公网子段。IPv4-mapped IPv6 按内嵌 IPv4 分类。该判断不联网、不解析域名；IANA 快照变化只在新版本中显式评审，不会让同一 SDK 版本的结果动态变化。

维护者在发版前可使用固定 Python 3 工具链执行 `../simple-xff-capture-core/scripts/check_iana_registry_snapshot.py`，对比官方快照是否更新。脚本只生成待人工审查的候选文件，不自动覆盖已发布分类规则。

## Event 数据

`XffCaptureEvent` 只统计请求进入应用时已经存在的入口事实。事件内部以完整 `snapshot` 聚合 XFF 与转发上下文，并为消费者提供下列便利 getter：

| 顶层字段 | 内容 |
| --- | --- |
| `eventId` | 首次采集生成的唯一事件标识 |
| `occurredAt` | 首次采集时间 |
| `requestMethod` | HTTP 方法 |
| `requestUri` | 不包含 query string 的请求 URI |
| `applicationRawRemoteAddress` | 采集调用时应用可见的原始远端地址，不属于 XFF 链 |
| `xffChain` | 由完整 `snapshot` 委托暴露；包含 XFF 是否存在、原始 Header 多值、有序拆分链 |
| `forwardedContext` | 由完整 `snapshot` 委托暴露；包含 `Host`、`X-Real-IP`、`X-Forwarded-Host`、`X-Forwarded-Port`、`X-Forwarded-Proto` 各自的 `present + rawValueList` |

Event 不统计响应状态、耗时、用户、设备、租户、业务动作、业务结果或真实客户端 IP。这些信息由对应业务事件、HTTP Access Event 或 Listener Provider 在其所有权边界内补充，并通过 requestId/traceId 等关联。Listener Provider 只能同步读取已经验证的当前上下文并形成自己的不可变审计文档，不能修改 Capture Snapshot，也不能反向调用业务 Service 查库。

## 事实边界

- 同时保留 Servlet 容器暴露的原始 Header 值列表与按逗号拆分的有序链。
- 只移除元素两侧 SP / HTAB。
- 不过滤空值、`unknown`、非法值、公网或私网地址。
- 除 XFF 外，固定原样采集 `Host`、`X-Real-IP`、`X-Forwarded-Host`、`X-Forwarded-Port`、`X-Forwarded-Proto` 的 `present + rawValueList`。
- Header 名按大小写不敏感规则读取；五项不做校验、合并、fallback 或可信解释。
- 不读取 RFC 7239 `Forwarded` 或其他任意 Header，不允许配置任意 Header Map。
- 不把 `X-Real-IP`、`remoteAddr` 或固定五项中的任何值拼入 XFF 链。
- 不直接写日志、数据库、Elasticsearch 或消息队列。

审计落 Elasticsearch 请使用 `simple-xff-capture-audit-listener-starter`。

## 兼容版本

- Java 8+
- Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5`、`2.7.9`

Starter 1.1.2 使用 `simple-xff-capture-core:1.1.1`，兼容以上四个 Spring Boot 基线；Forwarded Header 策略的真实 Servlet 视图由四个基线的集成测试分别验证。

## 版本记录

- [CHANGELOG.1.1.2.md](CHANGELOG.1.1.2.md)：Forwarded Header 策略下的 Filter 可见事实语义与部署说明。
- [CHANGELOG.1.1.1.md](CHANGELOG.1.1.1.md)：XFF 入口、请求包装与事件快照的 DEBUG 诊断日志。
- [CHANGELOG.1.1.0.md](CHANGELOG.1.1.0.md)：请求数据采集能力与兼容性边界。
- [CHANGELOG.1.0.1.md](CHANGELOG.1.0.1.md)：已发布的 Filter 顺序配置能力。
