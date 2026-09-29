# simple-iam-aksk-collaboration-starter

IAM -> AKSK Owner Authorization 的可选适配器 Starter。

本模块运行在实际部署 AKSK 的业务应用进程中，负责把 IAM 的版本化 owner authorization HTTP 契约适配为中性的 `simple-owner-authorization-collaboration-core` SPI。它不是 `simple-aksk-server-starter` 的传递依赖，也不属于 `iam-resource`。

## 组合方式

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:3.2.1'
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-collaboration-starter:1.0.0'
}
```

AKSK Server 的通用投影策略仍配置在 `io.github.surezzzzzz.sdk.auth.aksk.server.owner-authorization`；本适配器只增加身份源连接配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          iam:
            adapter:
              aksk:
                collaboration:
                  enabled: true
                  token-uri: https://iam.example/oauth2/token
                  base-uri: https://iam.example
                  client-id: ${IAM_AKSK_CLIENT_ID}
                  client-secret: ${IAM_AKSK_CLIENT_SECRET}
```

同时开启 AKSK 的通用投影配置。`owner-source-id` 是部署命名空间；`ownerSubjectId` 始终使用 IAM 的稳定 `subjectId`，不能使用可变的展示名或外部目录标识：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              owner-authorization:
                enabled: true
                owner-source-id: local-iam
```

IAM 返回的 `ownerUsername` 仅作为 AKU 归属展示快照；AKSK binding、投影键和授权 claim 一律使用 `ownerSourceId + ownerSubjectId`。两项标识各司其职，用户名展示变更不会改变凭证归属。

通用投影配置与适配器连接配置必须同时开启；连接失败只返回失败关闭结果，不把旧投影提升为新的授权事实。端点协议由部署方按网络形态选择，http 与 https 均受支持；端点必须是无用户信息、无片段的绝对 HTTP 地址。`ownerSourceId` 和 `ownerSubjectId` 均按 IAM wire 契约限制为 64 字符，超限时不发出请求并失败关闭。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `connect-timeout-millis` | `1000` | IAM 连接超时，单位毫秒。 |
| `read-timeout-millis` | `2000` | IAM 响应超时，单位毫秒。 |
| `max-attempts` | `2` | resolve、候选目录和变更拉取的单次最大尝试次数。Bearer 返回 401 时丢弃对应 scope 的内存 token 后重新获取；其他失败不保存旧授权。 |

变更拉取只接受连续递增的 `sourceSequence`，并保留 IAM 响应中的 `serverTime` 作为投影租约事实；缺失、畸形或过期的响应统一返回不可用页。

业务应用不引入本模块时，AKSK 仍提供本地 AKP 和 `STATIC_LEGACY` AKU；`OWNER_INHERITED` 能力没有 provider 时必须禁用或失败关闭，不能降级成 AKSK 本地授权。

## 边界

- 适配器可以认识 IAM 的 endpoint、service token 和协作权限。
- 适配器不得读取 IAM 数据库、实体、Repository 或内部 Java 包。
- AKSK Server 不反向依赖本模块。
- IAM Server 可以独立启动，不需要本模块；IAM 只负责提供协作 HTTP 契约和权威授权投影。
