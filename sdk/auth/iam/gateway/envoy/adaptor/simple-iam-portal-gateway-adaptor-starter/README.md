# simple-iam-portal-gateway-adaptor-starter

这是 Spring Boot 2.7.9 / Java 8 的 IAM Portal Gateway adaptor 装配件。它使用 IAM Portal 可信应用路由投影，调用 `simple-iam-portal-gateway-adaptor-core` 校验目录并生成完整路由快照，再在本进程内提供 Envoy xDS（动态配置协议）v3 控制面。Envoy 负责实际 HTTP 转发；本模块不代理业务请求。

## 解决什么问题

引用本模块的运行宿主可以：

- 以 AKP（应用密钥对）调用 IAM Portal 机器目录接口；
- 在同一份全量目录上校验路径、目标地址、HTTP-only 策略和请求头策略；
- 通过 ADS/SotW（Envoy 的全量资源订阅方式）发布 LDS、RDS、CDS、EDS 四类 xDS 资源；
- 在目录分页完整且 Envoy 同一 ADS stream 对最新版本全部确认后，提供就绪状态和脱敏事件。

模块不访问 IAM 数据库、不保存用户会话或 Token、不创建业务权限、不拆分业务 Web 与 API 上游，也不替代业务应用自己的 Nginx。

## 最小接入

```gradle
implementation 'io.github.sure-zzzzzz:simple-iam-portal-gateway-adaptor-starter:1.0.0'
```

模块内部使用以下固定版本：

| 依赖 | 版本 | 作用 |
| --- | --- | --- |
| `simple-iam-portal-gateway-adaptor-core` | 1.0.0 | 纯 Java 路由契约、地址策略和快照构建 |
| `simple-iam-client-core` | 1.0.4 | Portal client 接口和 wire 模型 |
| `simple-iam-aksk-resttemplate-client-starter` | 1.0.3 | 通过 `akskClientRestTemplate` 访问 IAM Portal API |
| Envoy Java Control Plane API/server | 1.0.52 | xDS v3 资源和 ADS/SotW 服务 |
| gRPC Netty | 1.62.2 | xDS gRPC 监听 |

宿主必须提供 AKSK 底座创建的 `akskClientRestTemplate` Bean，并启用内部携带的 IAM RestTemplate client：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        iam:
          client:
            enable: true
            base-url: http://iam.example.test
        portal:
          gateway:
            adaptor:
              sync-interval-millis: 30000
              page-size: 50
              max-stale-millis: 0
              allowed-ports: [80]
              allow-loopback: false
              allow-private-network: false
              allow-metadata: false
              xds-bind-address: 127.0.0.1
              xds-port: 18000
              listener-bind-address: 0.0.0.0
              listener-port: 8080
```

`base-url` 只表示 IAM 服务 origin，实际 AKP client id、client secret、Token 和连接池配置由宿主及 AKSK 底座提供。机密值不得写入仓库、日志、事件或 xDS 资源。Spring Boot 2 使用 `META-INF/spring.factories` 自动配置；没有 IAM client、AKSK Bean 或必要运行依赖时，宿主启动应报告配置错误，不伪造目录数据。

## 路由与安全边界

- IAM Nginx 继续自闭环平台根路径、Portal 壳、IAM/AKSK 页面、OAuth 和平台固定 API；Adaptor 只处理不覆盖平台保留前缀的业务可信应用路由。
- IAM Portal 投影为每个应用提供 `routePrefix`、`entry`、`apiBase` 和一个 `targetOrigin`。页面和 API 共用该业务 Nginx origin，不在 adaptor 内拆分上游或改写业务路径。
- 当前版本只接受 HTTP upstream。外层 TLS 可在防火墙、硬负载或 IAM Nginx 终止；内部业务 upstream、xDS 和健康检查不挂载证书或 CA。
- 目标先由 core 校验，再由注入的 DNS resolver 返回固定 IP，Envoy 通过 EDS（端点发现资源）连接 IP；不使用运行期域名解析。
- `REQUEST` 策略保留调用方 `Authorization` 请求头，`NONE` 策略移除它。Cookie 始终移除；非空 Cookie 白名单在当前版本拒绝发布，避免把完整 Cookie 泄露到业务上游。
- 平台保留前缀、非 HTTP scheme、未允许端口、危险地址、非法路径、重复目录和 DNS 结果不一致，均拒绝整份候选快照；不会发布部分目录。

## 同步与就绪语义

启动时先读取一次完整 Portal 目录，随后按同步周期重试。读取过程校验 `catalogRevision`、页码、总量、分页大小和应用编码唯一性；所有页面与 API 路由都通过 core 后才生成候选快照。空目录是合法撤路结果，会发布不含业务路由的快照。

目录或目标依赖失败时保留上一份已应用快照并记录原因。`max-stale-millis: 0` 表示依赖失败立即未就绪；大于零时仅在窗口内允许继续就绪。xDS 候选快照写入控制面后，Envoy 各资源类型分别确认；同一个 ADS stream 对最新版本的 CDS、EDS、RDS、LDS 全部 ACK 后才产生 `SNAPSHOT_APPLIED`。该状态不代表其他 Envoy 副本已经收敛。真实 Envoy 数据面、业务 Nginx 和页面/API 转发仍需在部署运行时单独验收。

控制面自身拒绝候选快照时记录 `SNAPSHOT_RETAINED`，只有收到 Envoy 的真实 NACK 才记录 `XDS_NACK`。旧版本 ACK、已取消 stream 和已关闭 stream 都不会覆盖新版本状态。

## 健康与观测

`GatewayHealthEndpoint` 提供给宿主映射 HTTP health/readiness：

- `isAlive()` 只表示 adaptor 进程仍可提供查询；
- `isReady()` 要求 xDS 正在监听、存在已应用快照，且依赖失败没有超过允许窗口；
- `currentSnapshot()` 和 `failureReason()` 只返回当前内存状态，不主动访问业务首页。

默认关闭的 DEBUG 日志只记录目录版本、快照版本、路由数量、失败分类和耗时。事件包含 schema 版本、事件编号、关联编号、版本、数量、摘要和原因码，不包含 URL、Query、Header、Body、Cookie、Token、密码或私钥。

## 兼容性

| 项目 | 基线 |
| --- | --- |
| Java 制品 | Java 8 字节码 |
| Spring Boot | 2.7.9 |
| IAM Server | 提供 Portal 路由投影契约的版本 |
| Envoy | 由部署运行时固定版本和架构，不由本模块引入 |

本模块不声明多副本真实 Envoy 已全部收敛，也不把单元测试当作业务页面/API 联调结果。发布时使用精确 Maven 坐标；本地源码组合只用于开发验证，不进入发布 POM。
