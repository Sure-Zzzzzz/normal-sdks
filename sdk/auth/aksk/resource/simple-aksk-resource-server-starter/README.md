# Simple AKSK Resource Server Starter

AKSK Resource Server Provider Starter 3.0.1 为公共 Resource Server Starter 提供 AKSK 服务身份认证适配器。业务资源服务需要显式并列引入公共 Resource Server Starter 和本模块；本模块不会反向传递公共资源安全链。

## 接入依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.0'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.0.1'
}
```

公共 Resource Starter 负责唯一 Bearer 入口、`kid` 来源路由、认证编排、统一 `401/403`、精确 API permission、DATA 授权和公共访问事件。本模块只注册 AKSK `ResourceAuthenticationAdapter`，不创建自己的 Bearer Filter、最终业务 `SecurityFilterChain`、路径权限链、AOP 权限链、Header/request context 或 AKSK 私有访问事件。

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
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: https://aksk.example.com/oauth2/introspect
                  client-id: ${AKSK_INTROSPECT_CLIENT_ID}
                  client-secret: ${AKSK_INTROSPECT_CLIENT_SECRET}
                  local-cache:
                    enabled: false
                    fallback:
                      enabled: false
```

启用本模块时，`introspect.endpoint`、`client-id` 和 `client-secret` 必须配置；缺少任一项会在自动配置阶段抛出 `SimpleAkskResourceServerConfigurationException`。内省客户端必须由 AKSK Server 明确授权，凭据只能通过部署平台的受保护配置或密钥管理注入，不能写入源码、文档、日志、响应或前端。

## 认证边界

公共资源层从 Bearer 凭据的外层 JOSE protected header 读取 `kid`，使用以下格式选择 Provider：

```text
kid = aksk/<key-id>
```

`kid` 仅用于来源路由，不能证明令牌可信。AKSK Provider 仍通过 AKSK Server 完整校验令牌的有效状态、签发方、受众、时效、撤销状态和当前应用授权快照。

Provider 只接受公共 Core 的 `BearerResourceCredential`，并要求：

- `sourceId` 为 `aksk`；
- introspection 结果存在且 `active=true`；
- `client_id` 为字符串；
- 应用授权主体类型为 `SERVICE`；
- `client_id` 与授权主体 ID 一致；
- 应用授权快照和 `DataGrantDocument` 能够完整恢复。

已选 AKSK Provider 认证失败后不会回退到其他 Provider。认证失败由公共安全链返回 `401`；已认证但应用未准入、缺少精确 API permission 或无法完整执行 DATA 范围时返回 `403`。

```text
Bearer / Provider 认证（401）
→ 应用准入与精确 API permission（403）
→ DATA 访问计划评估与完整范围执行（403）
→ 业务领域约束
```

API/DATA 权限不从 OAuth scope、角色、PAGE 权限、URL、HTTP method、Controller 名称或未验证 `security_context` 推导。业务接口使用公共资源层的路径规则或 `@RequireApiPermission` 声明精确 API permission。

## 本地缓存与故障降级

主 introspection 缓存默认关闭。只有主缓存显式开启后，`fallback.enabled=true` 才会初始化 stale fallback 缓存。

- 主缓存 TTL 由 `local-cache.expire-seconds` 控制；
- fallback TTL 为主缓存 TTL 乘以 `stale-ttl-multiplier`；
- fallback 默认关闭，开启后是明确的可用性与撤销传播时效权衡；
- 只有 `active=true` 条目允许在 introspection 端点不可用时通过 fallback；
- `active=false` 条目会写入缓存用于传播撤销状态，但不会通过 fallback 放行；
- fallback 未命中、条目失效或端点不可用时，认证失败并由公共链返回 `401`。

fallback 不能替代 AKSK Server 的实时授权校验；生产环境应根据撤销容忍窗口审慎设置 TTL 和 multiplier，并通过受控配置管理这些值。

## 安全日志与配置

日志只记录状态、数量、缓存统计或非敏感标识。禁止输出 Authorization、Cookie、Token、JWT、client secret、完整 introspection response、完整授权响应、本机路径和本地凭据。测试 fixture 使用匿名样例，真实地址和凭据只放在 Git 忽略的 `application-local.yml`。

## 相关模块

- [公共 Resource Server Starter](../../../resource/simple-resource-server-starter/README.md)
- [AKSK Resource Core](../simple-aksk-resource-core/README.md)
- [AKSK Server Starter](../../server/simple-aksk-server-starter/README.md)
- [历史 2.x 文档快照](README.2.x.md)

## 许可证

Apache License 2.0
