# Simple Resource Server Starter

为业务资源服务提供单一 Bearer 认证入口、来源路由与 API 权限拦截。

- 不校验 IAM、AKSK 或其他 Provider 的令牌。
- 每个 Provider 仅注册自己的 `ResourceAuthenticationAdapter`。
- 不自动执行 SQL、JPA、MyBatis 或 Elasticsearch 的数据过滤。

## 最小接入

引入依赖：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.0.0'
}
```

配置至少一个受保护路径：

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
                permit-all-paths:
                  - /public/**
```

`enabled` 默认是 `true`。显式设置为 `false`，或没有配置 `protected-paths` 时，Starter 不创建资源认证引擎与 `SecurityFilterChain`，不会接管宿主其他端点。

## API 权限配置

推荐在配置中直接声明“路径模式 + 精确 HTTP 方法 → 精确 API 权限”，业务 Controller 无需添加注解：

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
                api-permission-rules:
                  - path-pattern: /api/orders/**
                    method: GET
                    api-permission: order:read
                  - path-pattern: /api/orders/**
                    method: POST
                    api-permission: order:create
```

| 规则 | 约束 |
| --- | --- |
| HTTP 方法 | 必须是一个标准且精确的方法；不支持 `ANY`、`*` 或方法列表。 |
| 路径 | 使用 Spring Ant 语法：`*`、`**`、`?`。 |
| API 权限 | 必须是显式、精确、非空的权限值。 |
| 同一请求 | 至多命中一条规则；同一 HTTP 方法下可能交叠的规则会使应用启动失败。 |
| 默认规则 | `api-permission-rules` 默认为空，不提供内置权限或隐式推导。 |

配置规则命中时优先于 `@RequireApiPermission`，不会与方法或类上的注解拼接。

### 路径与请求参数

规则仅匹配 **Servlet application path** 与精确 HTTP 方法：

- 默认 `context-path-aware: true`。例如 `server.servlet.context-path: /gateway` 时，请求 `/gateway/api/orders` 应配置为 `/api/orders`。
- 关闭 `context-path-aware` 后，配置路径不得包含 context path。
- Query、fragment、header、Cookie、path variable 与 body 不参与 API 权限选择。
- `GET /api/orders?tenantId=tenant-a` 与 `GET /api/orders?tenantId=tenant-b` 使用同一 API 权限。
- `tenantId`、`departmentId`、分页与筛选参数只能作为业务输入，不能选择、拼接或覆盖 API 权限。
- `?` 在 `path-pattern` 中是 Ant 单字符通配符，不是 URL query 分隔符；路径配置不得包含 fragment。

## Provider 认证适配

在 Provider Starter 或业务应用中注册来源中立的认证适配器。`sourceId()` 必须与该 Provider 签发令牌外层 JWE protected header 的 `kid` 命名空间一致：

```java
@Bean
public ResourceAuthenticationAdapter authenticationAdapter() {
    return new ResourceAuthenticationAdapter() {
        @Override
        public ResourceAuthenticationSourceId sourceId() {
            return new ResourceAuthenticationSourceId("provider");
        }

        @Override
        public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
            // 由 Provider 完成令牌、签发方、受众、时效、撤销与授权快照校验。
            return ResourceAuthenticationResult.rejected(
                    ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
    };
}
```

Provider 成功认证时，必须返回同一来源主体的 `VerifiedResourcePrincipal` 与 `ApplicationAuthorizationContext`。公共层会拒绝主体来源与所选适配器不一致的结果。

## 未使用配置规则时

规则未命中时，仍可使用 `@RequireApiPermission`。方法注解优先，类注解作为回退：

```java
@RestController
@RequestMapping("/api/orders")
@RequireApiPermission("order:read")
public class OrderController {

    @GetMapping
    public List<OrderResponse> list() {
        return Collections.emptyList();
    }
}
```

受保护 MVC Handler 可以不写注解，但必须被一条精确 method 规则覆盖。规则与注解都未提供权限时，请求返回 403。

每个接口只对应一个稳定且精确的 API 权限。需要多个角色或主体访问时，应由 Provider 在授权快照中向这些主体授予同一个 API 权限；不从 `roles`、PAGE 权限、OAuth scope、URL、HTTP 方法或 Controller 名称推导权限。

## 认证与授权边界

### 认证路由

首发仅支持单一 Bearer 凭据。公共层只有限长读取紧凑令牌首段的外层 protected header，并从唯一 `kid` 选择 Provider：

```text
kid = <source-id>/<key-id>
```

`kid` 只用于路由，不能证明令牌可信。被选择的 Provider 必须独立完成令牌、签发方、受众、时效、撤销与授权快照校验。公共层不会解密令牌、读取 payload、基于未验证 claim 推断来源，也不会在认证失败后尝试其他 Provider。

以下请求不能进入 Provider，统一返回 401：

- 缺少或畸形 Bearer；
- 多个 `Authorization` header；
- 任意 Cookie 载体；
- 缺少、重复或未知 `kid`；
- Provider 校验失败。

Cookie 与 Session 认证不在 1.0.0 范围内；它们需要先独立定义传输、CSRF 与多载体规则。

### API 与 DATA 权限

固定顺序如下：

```text
Bearer / Provider 认证（401）
→ 应用准入与 API 权限（403）
→ DATA 访问计划评估与完整范围执行（403）
→ 业务领域约束
```

Resource Server 只负责认证、应用准入和 API 权限。业务服务必须自行消费完整 `DataAccessPlan` 并执行数据范围：同一 grant 内的 `tenantId`、`departmentId` 等约束保持 AND，不同 grant 保持 OR。DATA 权限与领域规则只能收紧访问范围；无法完整执行时必须拒绝，不能退化为全量访问。

## HTTP 结果

| 场景 | 结果 |
| --- | --- |
| 受保护路径缺少或无法验证凭据 | 401 |
| Provider 认证成功，但应用准入、API 权限或 DATA 规则不满足 | 403 |
| 显式公开路径 | 不读取、不解析、不校验 Bearer |
| 受保护 MVC Handler 没有配置规则且未声明 `@RequireApiPermission` | 403 |

以下配置问题会在启动时失败，避免安全策略降级：公开路径与受保护路径交叠；API 规则字段为空或 method 非法；未配置受保护路径却配置 API 规则；规则未覆盖受保护路径；规则与公开路径交叠；同一 method 下规则路径可能交叠；或未注册认证适配器。此类异常为带 `CONFIG_001` 的 `ResourceServerConfigurationException`。

## 运行边界

- Starter 仅在配置的 public/protected 路径范围内建立资源安全链。
- Spring Security principal 只保留已验证上下文，不保存原始 Bearer token。
- Provider Starter 不应创建竞争的最终业务 `SecurityFilterChain`，也不应覆盖公共层的 401/403、路径或 CSRF 策略。
- 本模块使用 SDK 私有 JSON 解析器，不复用宿主 Spring `ObjectMapper`。

## 许可证

Apache License 2.0
