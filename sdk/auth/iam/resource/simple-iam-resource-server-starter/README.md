# Simple IAM Resource Server Starter

为采用 `simple-resource-server-starter` 的业务资源服务提供 IAM 人员身份适配器。它通过受控 HTTP 验证 IAM Access Token，再把已验证人员主体与应用授权快照交给公共资源安全链。

Starter 不接受浏览器 Session、Cookie 或前端传递的客户端密钥；不本地解密或解析 Bearer Token；不缓存验证结果；IAM 不可用、响应不符合协议或认证失败时均不降级放行。

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'
}
```

`simple-resource-server-starter` 负责公共 Bearer 入口、Provider 来源路由、精确 API 权限和 DATA 授权边界。本 Starter 只注册 IAM Provider 适配器。

## 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          resource:
            server:
              security:
                protected-paths:
                  - /api/**
        iam:
          resource:
            server:
              verification-endpoint: https://iam.example/iam/resource/tokens/verify
              client-id: 从 IAM 管理面一次性取得的资源验证客户端标识
              client-secret: 仅部署环境注入
              connect-timeout-millis: 3000
              read-timeout-millis: 5000
```

`verification-endpoint`、`client-id` 与 `client-secret` 缺失，或任一超时不为正数时，客户端会失败，不会创建可用的认证结果。

资源验证客户端密钥仅用于服务端到 IAM Server 的 HTTP Basic 认证。它必须通过部署平台的密钥注入机制提供，不能进入浏览器、前端配置、代码仓库、日志、异常正文、数据库展示字段或响应缓存。IAM Server 只在创建时允许显示一次密钥；既有密钥不得重新展示。

## 受控验证语义

每个 IAM Bearer 请求按以下顺序处理：

```text
公共 Resource Server 从受控 kid 选择 IAM Provider
→ Starter 调 IAM 受控验证端点并使用独立 Basic 客户端认证
→ IAM 校验 Token、OAuth Client、应用绑定、IAM 会话、用户状态与授权快照
→ 公共层校验应用准入、精确 API 权限与 DATA 授权
```

| IAM 验证结果 | 资源适配器结果 | 最终请求结果 |
| --- | --- | --- |
| `200` 且恰好包含 `sub`、`iam_authorization` | 已认证 IAM 人员主体 | 继续公共授权判断 |
| `401` | Token 不活跃 | `401` |
| 非 `200`/`401` 或成功体不符合固定双字段协议 | 授权协议无效 | `401` |
| 连接、读取、TLS 或序列化不可用 | Provider 不可用 | `401` |

验证端点契约见 [`simple-iam-resource-token-verification.openapi.yaml`](../../server/contract/openapi/simple-iam-resource-token-verification.openapi.yaml)（位于 `simple-iam-server-starter` 契约目录）。调用方不得对 `401`、协议异常或网络异常尝试其他 Provider 或历史缓存。

## 异常体系

全部运行时异常挂 `SimpleIamResourceServerException` 基类（携带 `errorCode`）：

| 异常 | ErrorCode | 语义 |
| --- | --- | --- |
| `ValidationException` | `VALIDATION_001` | 入参或客户端构造参数无效 |
| `ConfigurationException` | `CONFIG_001` | 验证端点配置缺失或超时非正数 |
| `IamResourceVerificationProtocolException` | `VERIFICATION_001` | 验证响应不符合 `sub` + `iam_authorization` 双字段协议 |
| `IamResourceVerificationUnavailableException` | `VERIFICATION_002` | 网络、TLS 或序列化不可用 |

异常在适配器内转为对应拒绝分类（协议无效→授权无效、不可用→Provider 不可用），不向业务层抛出。

## 日志

生产诊断日志不输出 Token、凭据或响应原文：

| 级别 | 内容 |
| --- | --- |
| `INFO` | 装配期：验证客户端创建（含端点地址）、适配器注册 |
| `DEBUG` | 验证成功（打主体标识）、令牌不活跃、凭据类型不匹配 |
| `WARN` | 协议不符、服务不可用、快照映射失败（只打异常类名，不打栈） |

`DEBUG` 关闭时不改变任何行为。

## 兼容性

`1.0.x` 兼容：

- IAM Server 与 IAM Contract `1.0.x`
- IAM Resource Core `1.0.x`
- Resource Server Starter `1.1.1`
- Spring Boot `2.2.13.RELEASE` / `2.3.12.RELEASE` / `2.4.5` / `2.7.9`、Spring Security 5、`javax.servlet`

业务资源服务仍应以 `simple-resource-server-starter` 声明的兼容矩阵为准。当前 Starter 不支持 Spring Boot 3、Spring Security 6 或 `jakarta.servlet`。

## 许可证

Apache License 2.0
