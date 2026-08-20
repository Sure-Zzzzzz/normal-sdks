# Simple XFF Capture Starter

面向 Spring MVC 应用的 XFF 事实采集 Starter。它依赖 `simple-xff-capture-core`，自动通过 Servlet Filter 读取应用实际看到的 `X-Forwarded-For` 和固定入口转发上下文，形成 Core 不可变快照并通过 Spring 进程内事件总线发布 `XffCaptureEvent`，不判断真实客户端 IP。

Starter 1.0.1 传递依赖 `simple-xff-capture-core:1.0.0`，调用方无需重复声明 Core。未配置 Filter 顺序时，行为与 1.0.0 保持一致。

## 接入

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-starter:1.0.1'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
              # 可选，默认值为 Ordered.HIGHEST_PRECEDENCE + 10
              order: -2147483638
```

`order` 用于协调 XFF Filter 与应用自身的 Filter（例如 Spring Security）。省略时使用 `Ordered.HIGHEST_PRECEDENCE + 10`；只有明确掌握应用容器与安全链的实际顺序时才覆盖该值。

开启后无需修改 Controller 或 Service。Filter 对首次外部 REQUEST 自动采集；异步、错误、转发等二次 dispatch 不重复发布。

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
| `applicationRawRemoteAddress` | Capture Filter 执行时应用可见的原始远端地址，不属于 XFF 链 |
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

Starter 1.0.1 继续使用已发布的 `simple-xff-capture-core:1.0.0`，已完成以上四个 Spring Boot 基线的完整测试。
