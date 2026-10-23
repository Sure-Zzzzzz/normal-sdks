# simple-iam-portal-gateway-adaptor-core

`simple-iam-portal-gateway-adaptor-core` 是 IAM Portal Gateway adaptor 的 Java 8 纯 Java 契约模块。它把 IAM 返回的可信应用路由投影校验、归一化为不可变路由快照，供 starter 生成 Envoy xDS 资源。

## 坐标与边界

```gradle
implementation 'io.github.sure-zzzzzz:simple-iam-portal-gateway-adaptor-core:1.0.0'
```

- 不依赖 Spring、Spring Boot、HTTP 客户端、IAM Server、数据库或 Envoy；无运行时传输实现。
- 不执行 HTTP 转发，不生成或解析 Token、Authorization、Cookie 值，也不保存用户资料。
- 不对登记的主机名发起 DNS 或网络访问。`DnsResolver` 由上层提供解析结果；core 只校验其返回的 IP 字面量、Host、协议和端口。
- 预期的目录拒绝通过 `RouteSnapshotBuildResult` 返回，不使用通用参数异常作为业务控制流。

## 主要类型

| 类型 | 用途 |
| --- | --- |
| `PortalApplicationRoute` | IAM Portal 可信应用路由投影的中性输入 |
| `TargetUrlPolicy` | 固定 HTTP、端口、平台保留前缀和目标地址范围策略 |
| `DnsResolver` | 上层注入的解析端口 |
| `ResolvedTarget` | 解析后的固定 IP、登记 Host、协议和端口 |
| `RouteSnapshotBuilder` | 全量校验、规范化、排序并生成摘要 |
| `RouteSnapshot` | 可整体替换的不可变目录快照 |
| `GatewayRouteEvent` / `GatewayRouteEventPublisher` | 脱敏事件模型与事件发布端口 |

最小调用示例：

```java
RouteSnapshotBuildResult result = new RouteSnapshotBuilder().build(
        catalogRevision,
        TargetUrlPolicy.defaults(),
        portalRoutes,
        dnsResolver);

if (result.getValidation().isAccepted()) {
    RouteSnapshot snapshot = result.getSnapshot();
    // 交给 adaptor starter 转换为 Envoy CDS/EDS/RDS/LDS。
}
```

构建按全量目录处理：任意路由不通过时不返回部分快照；空目录会生成可用于清理旧路由的空快照。快照按规范化输入稳定排序并计算 SHA-256 版本，同一输入顺序变化不会改变版本。

## 路由契约

- `applicationCode` 只接受 `[A-Za-z0-9_-]{1,64}`；应用名必须为非空、无控制字符且不超过 128 个 Unicode 码点。
- `routePrefix` 是页面公开前缀，不能覆盖平台保留路径或与其他应用前缀重叠；`entry` 仅用于 Portal 打开初始文档，必须位于 `routePrefix` 下，不参与 Envoy 路径改写。
- `apiBase` 是非空公共 API 前缀，必须以 `/` 开头，不能覆盖平台保留路径。页面和 API 共用同一个 `targetOrigin`，公开路径与上游路径保持一致，core 不猜测或改写业务内部路径。
- `targetOrigin` 必须是无路径、无 query、无 fragment、无 userinfo 的绝对 HTTP origin。默认只允许 HTTP 80 端口；HTTPS upstream 会被拒绝。
- 路径拒绝 query、fragment、控制字符、空白、点段、反斜杠、重复斜杠、编码斜杠和重复编码。目标 IP 拒绝未指定、回环、链路本地、组播、广播、CGNAT、云元数据及默认禁止的私网/ULA 地址；本地策略放行必须由上层显式配置。
- `forwardAuthorization` 只接受 `REQUEST` 或 `NONE`。core 仅校验并保留策略；实际 Header 处理由上层 adaptor 按其传输契约执行，core 不生成或验证调用方凭据。
- `forwardCookieNames` 只校验 Cookie 名称语法并保留在快照中；是否支持白名单、是否移除 Cookie 由上层 adaptor 的版本契约决定。core 本身不转发 Cookie。
- core 不跟随重定向，也不提供重定向次数配置；目标地址必须在目录同步时一次性通过上述策略校验。

平台保留的根路径、IAM/Portal 壳、AKSK 固定页面和 API、OAuth、登录、健康检查、Actuator、xDS、管理路径不进入动态路由快照。

## 事件与日志

core 只定义 `GatewayRouteEvent` 和 `GatewayRouteEventPublisher` 端口，不负责实际投递。事件模型可包含 schema、事件/关联 ID、目录/快照版本、路由数量、脱敏目标摘要、原因码和时间；不得包含 URL、Authorization、Cookie、Token、密码、私钥、完整 Header、Query 或 Body。日志同样只记录版本、数量、耗时和结构化原因。

## 兼容性与验证

| 项目 | 约束 |
| --- | --- |
| Java | 8 |
| Spring / Spring Boot | 不依赖 |
| 传输与运行时 | 由 `simple-iam-portal-gateway-adaptor-starter` 提供 |
| 当前版本 | 1.0.0 |

本模块测试覆盖路由快照生成、稳定摘要、应用编码与名称、路径和保留前缀、HTTP/HTTPS、目标地址范围、DNS 解析结果一致性、转发策略、空目录和敏感字段边界。测试使用 fake `DnsResolver`，仅证明 core 契约，不代表真实 IAM、DNS、Envoy 或业务上游已经完成联调。
