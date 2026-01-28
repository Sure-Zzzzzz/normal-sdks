# Simple AKSK Resource Server Starter

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

资源服务器端 JWT 验证器，直接验证 JWT token 并提供便捷的 API 访问用户信息（适用于无网关场景）。

## 核心能力

### 1. JWT Token 验证

- **Spring Security OAuth2 Resource Server** - JWT 验证
  - 使用 RSA 公钥验证 JWT 签名
  - 支持从配置字符串或文件加载公钥
  - 自动验证 token 有效期和签名

### 2. 自动提取 JWT Claims

- **AkskJwtAuthenticationConverter** - JWT 转换器
  - 自动提取 JWT claims 并转换为 camelCase 格式
  - 存储到 Request Attribute（线程池安全）
  - 支持标准 claims 和自定义 claims
  - 自动清理，无内存泄漏风险

### 3. 便捷的静态 API

- **SimpleAkskSecurityContextHelper** - 安全上下文 API
  - 类似 Shiro 的便捷 API 风格
  - 基于 Request Attribute 实现
  - 支持获取预定义字段（userId、username、clientId 等）
  - 支持获取任意自定义字段

### 4. Provider 实现

- **AkskJwtContextProvider** - 上下文提供者实现
  - 实现 `SimpleAkskSecurityContextProvider` 接口（来自 resource-core）
  - 适配 SimpleAkskSecurityContextHelper 的静态方法
  - 用于 SimpleAkskSecurityAspect 的依赖注入
  - 从 JWT Claims 获取上下文数据

### 5. 权限注解

- **@RequireContext** - 要求存在安全上下文
- **@RequireField** - 要求存在指定字段
- **@RequireFieldValue** - 要求字段值匹配
- **@RequireExpression** - 要求 SpEL 表达式为 true

### 6. 路径安全配置

- **保护路径** - 需要 JWT 认证的路径
- **白名单路径** - 不需要认证的路径
- **灵活配置** - 支持 Ant 风格路径匹配

## 架构设计

### Provider 模式

本模块采用 Provider 模式实现上下文提供：

```
simple-aksk-resource-core (定义接口)
    ↓
SimpleAkskSecurityContextProvider (接口)
    ↓
AkskJwtContextProvider (实现类)
    ↓
SimpleAkskSecurityContextHelper (静态 API)
```

**工作原理**：
1. **Spring Security** 验证 JWT Token 的签名和有效期
2. **AkskJwtAuthenticationConverter** 提取 JWT claims，存储到 Request Attribute
3. **SimpleAkskSecurityContextHelper** 提供静态 API，从 Request Attribute 读取数据
4. **AkskJwtContextProvider** 实现 `SimpleAkskSecurityContextProvider` 接口，适配 Helper 的静态方法
5. **SimpleAkskSecurityAspect** 通过 Provider 接口获取上下文，执行权限校验

这种设计的优势：
- ✅ **解耦**：切面不直接依赖 Helper，而是依赖抽象接口
- ✅ **可测试**：可以轻松 mock Provider 进行单元测试
- ✅ **可扩展**：未来可以替换不同的 Provider 实现

## 依赖说明

本模块依赖：
- simple-aksk-resource-core - 资源保护核心
- Spring Boot - 自动配置
- Spring Security - JWT 验证
- Spring Security OAuth2 Resource Server - OAuth2 资源服务器
- Spring Security OAuth2 JOSE - JWT 处理
- Lombok - 简化代码

## 配置示例

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                enabled: true  # 是否启用（默认：true）

                jwt:
                  # JWT 验证配置（三选一）

                  # 方式1: 使用 issuer-uri（推荐，自动获取 JWKS）
                  issuer-uri: http://localhost:8080

                  # 方式2: 直接配置公钥字符串
                  # public-key: |
                  #   -----BEGIN PUBLIC KEY-----
                  #   MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
                  #   -----END PUBLIC KEY-----

                  # 方式3: 从文件加载公钥
                  # public-key-location: classpath:jwt-public-key.pem

                security:
                  # 需要保护的路径（需要 JWT 认证）
                  protected-paths:
                    - /api/**
                    - /secure/**

                  # 白名单路径（不需要认证）
                  permit-all-paths:
                    - /api/health
                    - /api/public/**
```

**配置说明**：

### JWT 验证方式

**方式1: issuer-uri（推荐）**
- 自动从授权服务器的 `/.well-known/oauth-authorization-server` 或 `/.well-known/openid-configuration` 端点获取 JWKS
- 适用于标准的 OAuth2/OIDC 授权服务器（如 Keycloak、Auth0）
- 配置最简单，自动处理密钥轮换
- 示例：`http://localhost:8080`（授权服务器地址）

**方式2: public-key**
- 直接在配置文件中提供 PEM 格式的公钥字符串
- 适用于测试环境或公钥固定的场景
- 需要手动管理密钥更新

**方式3: public-key-location**
- 从文件系统或 classpath 加载公钥文件
- 支持 `classpath:` 和 `file:` 前缀
- 适用于离线验证或测试环境
- 示例：
  - `classpath:jwt-public-key.pem`
  - `file:/etc/aksk/jwt-public-key.pem`

**配置优先级**：issuer-uri > public-key > public-key-location

## 使用方式

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.0'
}
```

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Security（JWT 验证）
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.security:spring-security-oauth2-resource-server'
    implementation 'org.springframework.security:spring-security-oauth2-jose'

    // Spring Web（REST API）
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

**版本说明**：
- 建议使用 Spring Boot 2.7.x 版本
- 需要 Servlet 环境（不支持 WebFlux）

### 2. 基本使用

#### 获取用户信息

```java
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/user/info")
    public UserInfo getUserInfo() {
        // 获取用户 ID
        String userId = SimpleAkskSecurityContextHelper.getUserId();

        // 获取用户名
        String username = SimpleAkskSecurityContextHelper.getUsername();

        // 获取 Client ID
        String clientId = SimpleAkskSecurityContextHelper.getClientId();

        return new UserInfo(userId, username, clientId);
    }
}
```

#### 获取自定义字段

```java
// 获取任意字段
String tenantId = SimpleAkskSecurityContextHelper.get("tenantId");
String orgId = SimpleAkskSecurityContextHelper.get("orgId");

// 获取所有字段
Map<String, String> allContext = SimpleAkskSecurityContextHelper.getAll();
```

### 3. 使用权限注解

#### @RequireContext - 要求存在安全上下文

```java
@RestController
@RequireContext  // 类级别：所有方法都要求存在安全上下文
public class SecureController {

    @GetMapping("/data")
    public Data getData() {
        // 此方法要求存在安全上下文
        return ...;
    }
}
```

#### @RequireField - 要求存在指定字段

```java
@GetMapping("/user/profile")
@RequireField("userId")  // 要求存在 userId 字段
public UserProfile getUserProfile() {
    String userId = SimpleAkskSecurityContextHelper.getUserId();
    return userService.getProfile(userId);
}
```

#### @RequireFieldValue - 要求字段值匹配

```java
@GetMapping("/admin/users")
@RequireFieldValue(field = "clientType", value = "user")  // 要求 clientType 字段值为 "user"
public List<User> listUsers() {
    return userService.listAllUsers();
}
```

#### @RequireExpression - 要求 SpEL 表达式为 true

```java
@GetMapping("/tenant/data")
@RequireExpression("#context['tenantId'] != null && #context['tenantId'].startsWith('tenant-')")
public TenantData getTenantData() {
    String tenantId = SimpleAkskSecurityContextHelper.get("tenantId");
    return tenantService.getData(tenantId);
}
```

### 4. JWT Token 格式

本 starter 期望的 JWT token 格式：

```json
{
  "client_id": "test-client",
  "client_type": "user",
  "user_id": "user-123",
  "username": "testuser",
  "security_context": "role:admin,level:5",
  "scope": "read write",
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "api-resource",
  "exp": 1735689600,
  "iat": 1735686000
}
```

**Claims 映射规则**：

| JWT Claim (snake_case) | 字段名 (camelCase) |
|------------------------|-------------------|
| `client_id` | `clientId` |
| `client_type` | `clientType` |
| `user_id` | `userId` |
| `username` | `username` |
| `security_context` | `securityContext` |
| `scope` | `scope` |

### 5. 异常处理

权限校验失败时会抛出 `SimpleAkskSecurityException`，您可以自定义全局异常处理器：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SimpleAkskSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAkskSecurityException(SimpleAkskSecurityException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Forbidden");
        result.put("message", e.getMessage());
        result.put("status", 403);
        return result;
    }
}
```

## API 参考

### SimpleAkskSecurityContextHelper 静态方法

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getUserId()` | `String` | 获取用户 ID |
| `getUsername()` | `String` | 获取用户名 |
| `getClientId()` | `String` | 获取 Client ID |
| `getClientType()` | `String` | 获取 Client Type |
| `getSecurityContext()` | `String` | 获取原始 security_context |
| `get(String key)` | `String` | 获取指定字段 |
| `getAll()` | `Map<String, String>` | 获取所有字段 |

### 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `Boolean` | `true` | 是否启用 |
| `jwt.public-key` | `String` | - | JWT 公钥（PEM 格式字符串） |
| `jwt.public-key-location` | `String` | - | JWT 公钥文件路径 |
| `security.protected-paths` | `List<String>` | `["/api/**"]` | 需要保护的路径 |
| `security.permit-all-paths` | `List<String>` | `[]` | 白名单路径 |

## 工作原理

### 完整流程

```
HTTP Request (with JWT Token in Authorization Header)
    ↓
Spring Security Filter Chain
    ↓
JWT Decoder (验证签名和有效期)
    ↓
AkskJwtAuthenticationConverter (提取 claims)
    ↓
Request Attribute (存储上下文)
    ↓
SimpleAkskSecurityContextHelper (静态 API 访问)
    ↓
AkskJwtContextProvider (适配器，实现 SimpleAkskSecurityContextProvider)
    ↓
SimpleAkskSecurityAspect (权限校验)
    ↓
Controller Method (业务逻辑)
```

### 线程安全

- ✅ 基于 Request Attribute 实现（不使用 ThreadLocal）
- ✅ 线程池环境下安全可靠
- ✅ 自动清理，无内存泄漏风险
- ✅ 每个请求独立隔离

## 使用场景

### 场景 1：微服务直接验证 JWT

```
客户端
    ↓ JWT Token
后端服务（无网关）
    ↓ simple-aksk-resource-server-starter
自动验证 JWT 并提取用户信息
```

### 场景 2：数据权限控制

```java
@GetMapping("/orders")
@RequireField("tenantId")  // 要求存在租户 ID
public List<Order> getOrders() {
    String tenantId = SimpleAkskSecurityContextHelper.get("tenantId");
    // 只返回当前租户的订单
    return orderService.getOrdersByTenant(tenantId);
}
```

### 场景 3：角色权限控制

```java
@DeleteMapping("/users/{id}")
@RequireExpression("#context['clientType'] == 'user' && #context['securityContext'].contains('role:admin')")
public void deleteUser(@PathVariable String id) {
    // 只有用户级 AKSK 且角色为 admin 可以删除
    userService.delete(id);
}
```

## 与 security-context-starter 的区别

| 特性 | security-context-starter | resource-server-starter |
|------|-------------------------|------------------------|
| **适用场景** | 有 APISIX 网关 | 无网关（直接验证 JWT） |
| **数据来源** | HTTP Headers | JWT Claims |
| **验证方式** | 网关已验证 | 本地验证 JWT 签名 |
| **上下文提供者** | AkskUserContextProvider | AkskJwtContextProvider |
| **依赖** | Spring Web + AOP | Spring Security + OAuth2 |

## 版本历史

### 1.0.0 (2026-01-28)

初始版本发布：
- ✅ 实现 JWT Token 验证
- ✅ 自动提取 JWT claims 到上下文
- ✅ 提供便捷的静态 API（类似 Shiro）
- ✅ 支持 4 种权限注解
- ✅ 基于 Request Attribute（线程池安全）
- ✅ 支持路径安全配置
- ✅ 自定义异常（SimpleAkskSecurityException）

## 许可证

Apache License 2.0
