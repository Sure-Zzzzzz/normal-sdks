# Simple XFF Capture Core

`simple-xff-capture-core` 提供 XFF 采集事件的稳定纯 JDK 契约，以及严格的 IP 字面量解析和地址分类能力。

它适合由 HTTP 入口采集模块发布事件、由审计或其他 Listener 消费事件的场景；自身不采集 Servlet 请求、不启动事件总线，也不负责从 XFF 推导可信客户端 IP。

## 引入依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-core:1.0.0'
}
```

生产运行时仅依赖 JDK 8。

## IP 地址分类

使用 `XffAddressHelper.classify` 获取完整分类结果：

```java
XffAddressInfo addressInfo = XffAddressHelper.classify("::ffff:192.168.1.1");

boolean ipLiteral = addressInfo.isIpLiteral();
String normalizedIp = addressInfo.getNormalizedIp();
XffIpVersion ipVersion = addressInfo.getIpVersion();
XffIpScope scope = addressInfo.getScope();
```

`scope` 的含义如下：

| 值 | 含义 |
| --- | --- |
| `PUBLIC` | 全局公网单播地址 |
| `PRIVATE` | RFC 1918 IPv4 私网或 RFC 4193 IPv6 ULA |
| `SPECIAL` | 合法但不属于公网或私网的特殊用途地址，例如回环、链路本地、文档和组播地址 |
| `INVALID` | 不是合法 IP 字面量 |

仅需判断时，可使用以下便利方法：

```java
boolean ipLiteral = XffAddressHelper.isIpLiteral("2001:4860:4860::8888");
boolean privateIp = XffAddressHelper.isPrivateIp("10.0.0.1");
boolean publicIp = XffAddressHelper.isPublicIp("8.8.8.8");
```

### 解析与分类规则

- 只接受纯 IPv4 / IPv6 字面量，不接受域名、端口、方括号、zone id 或 IPv4 前导零；
- IPv6 支持带 `::` 的压缩形式和不带 `::` 的完整形式；两种形式都允许在最后一段嵌入 IPv4，非末段嵌入 IPv4 会被拒绝；
- IPv6 规范化为小写并使用最长零段压缩；
- IPv4-mapped IPv6 按内嵌 IPv4 规范化和分类，例如 `::ffff:192.168.1.1` 的结果为 IPv4 私网；
- 不发起 DNS 查询；
- 公网判断基于 2025-10-09 的 IANA 特殊用途地址注册表固定快照，同一 SDK patch 版本不会静默改变分类规则。

## 事件契约

HTTP 采集模块应创建并发布 `XffCaptureEvent`。事件保存采集事实，不保存 Servlet 生命周期对象或业务结果：

| 字段 | 含义 |
| --- | --- |
| `eventId` | 唯一事件标识 |
| `occurredAt` | 采集时间 |
| `requestMethod` | HTTP 方法 |
| `requestUri` | 不包含查询参数的请求 URI |
| `applicationRawRemoteAddress` | Filter 执行时应用通过 `getRemoteAddr()` 看到的原始远端地址，不属于 XFF 链 |
| `snapshot` | XFF 链与固定转发 Header 的不可变快照 |

`snapshot` 包含：

- `XffChain`：XFF Header 是否存在、Servlet 容器暴露的原始 Header 值，以及按逗号机械拆分并去除两侧 HTTP 可选空白后的有序值链；
- `ForwardedContext`：`Host`、`X-Real-IP`、`X-Forwarded-Host`、`X-Forwarded-Port`、`X-Forwarded-Proto` 的原始 Header 快照。

所有快照模型都会校验状态并对集合进行防御性复制。原始 Header、请求 URI 和远端地址不会出现在模型的 `toString()` 中，但调用方仍应避免将原始采集内容直接输出到不受控日志。

## 信任边界

XFF 与转发 Header 都是请求携带的事实，不等于可信客户端身份。本模块不会：

- 根据网络拓扑挑选客户端 IP；
- 判断代理是否可信；
- 合并、纠正或覆盖 Header；
- 将 XFF、`X-Real-IP` 或 `getRemoteAddr()` 认定为认证主体。

需要可信源地址时，应由部署侧或专门的信任链策略在明确代理边界后处理；Core 事件可作为该策略的原始输入。

## 兼容性

`1.0.x` 中，`XffCaptureEvent` 的字段和构造器、快照模型字段、`XffIpVersion` / `XffIpScope` 枚举、规范化格式与地址分类行为均为稳定契约。新增入口协议或破坏性语义调整应使用新事件类型或新 minor 版本，不能直接扩展既有构造器。

`simple-xff-capture-starter` 负责 HTTP 采集和 Spring Event 发布；可选 Listener 负责消费 Core 事件并进行外部投影。它们在 Core 发布后按各自版本独立发布。

生产代码仅使用 JDK 8 API；完整测试已在 Spring Boot 2.7.9、2.4.5、2.3.12.RELEASE 和 2.2.13.RELEASE 基线通过。
