# Simple AKSK Resource Core

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK 资源保护核心模块，提供统一的安全上下文抽象和权限注解支持。

## 概述

`simple-aksk-resource-core` 是 AKSK 资源保护体系的核心抽象层，它定义了通用的接口、注解、切面和常量，供不同的资源保护实现使用。

### 设计目标

1. **统一抽象**：通过 `SecurityContextProvider` 接口统一上下文获取方式
2. **代码复用**：注解、切面、常量只维护一份，避免重复
3. **灵活部署**：支持有网关和无网关两种场景
4. **易于扩展**：未来可以轻松添加新的上下文来源

### 架构设计

```
simple-aksk-resource-core (核心抽象)
    ↑                           ↑
    |                           |
security-context-starter    resource-server-starter
(从 HTTP Headers 获取)       (从 JWT Claims 获取)
```

## 核心组件

### 1. SimpleAkskSecurityContextProvider 接口

定义如何获取安全上下文的抽象接口。

```java
public interface SimpleAkskSecurityContextProvider {
    Map<String, String> getAll();
    String get(String key);

    // 便捷方法
    default String getUserId() { ... }
    default String getUsername() { ... }
    default String getClientId() { ... }
    default String getSecurityContext() { ... }
}
```

**实现类**：
- `AkskUserContextProvider` (security-context-starter) - 从 HTTP Headers 获取
- `AkskJwtContextProvider` (resource-server-starter) - 从 JWT Claims 获取

### 2. 权限注解

提供 4 种权限校验注解：

#### @RequireContext
要求存在安全上下文。

```java
@RequireContext
public class SecureController {
    // 所有方法都要求存在安全上下文
}
```

#### @RequireField
要求存在指定字段。

```java
@RequireField("userId")
public UserProfile getUserProfile() {
    // 要求存在 userId 字段
}
```

#### @RequireFieldValue
要求字段值匹配。

```java
@RequireFieldValue(field = "role", value = "admin")
public List<User> listUsers() {
    // 要求 role 字段值为 "admin"
}
```

#### @RequireExpression
要求 SpEL 表达式为 true。

```java
@RequireExpression("#context['tenantId'] != null && #context['tenantId'].startsWith('tenant-')")
public TenantData getTenantData() {
    // 要求 tenantId 字段不为空且以 "tenant-" 开头
}
```

### 3. SimpleAkskSecurityAspect 切面

实现权限注解的 AOP 校验逻辑。

**特点**：
- 通过构造函数注入 `SimpleAkskSecurityContextProvider`
- 支持方法级别和类级别注解
- 自动处理注解优先级（方法 > 类）
- 校验失败抛出 `SimpleAkskSecurityException`

**工作原理**：

```java
@Aspect
public class SimpleAkskSecurityAspect {
    private final SimpleAkskSecurityContextProvider contextProvider;

    public SimpleAkskSecurityAspect(SimpleAkskSecurityContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Before("@annotation(...RequireContext) || @within(...RequireContext)")
    public void checkRequireContext(JoinPoint joinPoint) {
        Map<String, String> context = contextProvider.getAll();
        if (context == null || context.isEmpty()) {
            throw new SimpleAkskSecurityException("Security context is required");
        }
    }

    // ... 其他注解处理方法
}
```

### 4. SimpleAkskResourceConstant 常量类

定义统一的命名规范和映射关系。

**包含内容**：
- **字段名称**（camelCase）- 用作上下文 Map 的 key
- **JWT Claim 名称**（snake_case）- 用于 JWT token
- **HTTP Header 名称**（kebab-case）- 用于 HTTP 请求
- **映射关系** - 字段名 ↔ JWT Claim 的双向映射

**示例**：

```java
public class SimpleAkskResourceConstant {
    // 字段名称
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USERNAME = "username";

    // JWT Claim 名称
    public static final String JWT_CLAIM_USER_ID = "user_id";
    public static final String JWT_CLAIM_USERNAME = "username";

    // HTTP Header 名称
    public static final String HEADER_USER_ID = "x-sure-auth-aksk-user-id";
    public static final String HEADER_USERNAME = "x-sure-auth-aksk-username";

    // 映射关系
    public static final Map<String, String> FIELD_TO_JWT_CLAIM = Map.of(
        FIELD_USER_ID, JWT_CLAIM_USER_ID,
        FIELD_USERNAME, JWT_CLAIM_USERNAME
    );

    public static final Map<String, String> JWT_CLAIM_TO_FIELD = Map.of(
        JWT_CLAIM_USER_ID, FIELD_USER_ID,
        JWT_CLAIM_USERNAME, FIELD_USERNAME
    );
}
```

### 5. SimpleAkskSecurityException 异常

权限校验失败时抛出的运行时异常。

```java
public class SimpleAkskSecurityException extends RuntimeException {
    public SimpleAkskSecurityException(String message) {
        super(message);
    }

    public SimpleAkskSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## 依赖说明

本模块依赖：
- Spring Boot - 自动配置
- Spring Web - Servlet 支持
- Spring AOP - 切面支持
- Spring Expression - SpEL 表达式
- AspectJ - AOP 注解
- Lombok - 简化代码

## 使用方式

### 1. 添加依赖

通常情况下，您不需要直接依赖 `resource-core`，而是使用以下 starter 之一：

- **simple-aksk-security-context-starter** - 适用于有网关场景
- **simple-aksk-resource-server-starter** - 适用于无网关场景

如果您需要自定义实现，可以直接依赖 `resource-core`：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-core:1.0.0'
}
```

### 2. 自定义实现

如果您需要从其他数据源获取安全上下文（如 Redis、数据库等），可以实现 `SimpleAkskSecurityContextProvider` 接口：

```java
public class CustomSecurityContextProvider implements SimpleAkskSecurityContextProvider {

    @Override
    public Map<String, String> getAll() {
        // 从自定义数据源获取上下文
        return customDataSource.getContext();
    }

    @Override
    public String get(String key) {
        return getAll().get(key);
    }
}
```

然后注册 `SimpleAkskSecurityAspect` Bean：

```java
@Configuration
public class CustomSecurityConfiguration {

    @Bean
    public SimpleAkskSecurityAspect akskSecurityAspect() {
        return new SimpleAkskSecurityAspect(new CustomSecurityContextProvider());
    }
}
```

## 命名规范

### 字段名称（camelCase）

用作上下文 Map 的 key，遵循 Java 命名规范。

| 字段名 | 说明 |
|--------|------|
| `clientId` | Client ID |
| `clientType` | Client Type |
| `userId` | User ID |
| `username` | Username |
| `securityContext` | Security Context |
| `roles` | Roles |
| `scope` | Scope |

### JWT Claim 名称（snake_case）

用于 JWT token，遵循 OAuth2 规范。

| Claim 名称 | 说明 |
|-----------|------|
| `client_id` | Client ID |
| `client_type` | Client Type |
| `user_id` | User ID |
| `username` | Username |
| `security_context` | Security Context |
| `scope` | Scope |

### HTTP Header 名称（kebab-case）

用于 HTTP 请求，遵循 HTTP Header 命名规范。

| Header 名称 | 说明 |
|------------|------|
| `x-sure-auth-aksk-client-id` | Client ID |
| `x-sure-auth-aksk-client-type` | Client Type |
| `x-sure-auth-aksk-user-id` | User ID |
| `x-sure-auth-aksk-username` | Username |
| `x-sure-auth-aksk-security-context` | Security Context |

## 设计原则

### 1. 开闭原则

- **对扩展开放**：可以轻松添加新的 `SimpleAkskSecurityContextProvider` 实现
- **对修改关闭**：核心接口和注解保持稳定，不需要修改

### 2. 依赖倒置原则

- **高层模块**（SimpleAkskSecurityAspect）依赖抽象（SimpleAkskSecurityContextProvider）
- **低层模块**（AkskUserContextProvider、AkskJwtContextProvider）实现抽象

### 3. 单一职责原则

- **SimpleAkskSecurityContextProvider**：只负责提供上下文数据
- **SimpleAkskSecurityAspect**：只负责权限校验逻辑
- **注解**：只负责声明权限要求

### 4. 接口隔离原则

- `SimpleAkskSecurityContextProvider` 接口简洁明确
- 提供默认方法减少实现负担

## 扩展性

### 支持的扩展场景

1. **新的上下文来源**
   - 从 Redis 获取上下文
   - 从数据库获取上下文
   - 从消息队列获取上下文

2. **新的权限注解**
   - 自定义注解（如 `@RequireRole`）
   - 扩展 `SimpleAkskSecurityAspect` 添加新的切面方法

3. **新的字段映射**
   - 扩展 `SimpleAkskResourceConstant` 添加新的字段映射
   - 支持自定义命名规范

## 版本历史

### 1.0.0 (2026-01-28)

初始版本发布：
- ✅ 定义 SimpleAkskSecurityContextProvider 接口
- ✅ 实现 4 种权限注解
- ✅ 实现 SimpleAkskSecurityAspect 切面
- ✅ 定义 SimpleAkskResourceConstant 常量
- ✅ 实现 SimpleAkskSecurityException 异常
- ✅ 支持方法级别和类级别注解
- ✅ 支持 SpEL 表达式

## 相关模块

- **simple-aksk-security-context-starter** - 从 HTTP Headers 获取上下文（有网关场景）
- **simple-aksk-resource-server-starter** - 从 JWT Claims 获取上下文（无网关场景）

## 许可证

Apache License 2.0
