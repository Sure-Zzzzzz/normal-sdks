# Simple AKSK Security Context Starter

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

资源服务器端安全上下文解析器，从 HTTP Header 自动提取用户信息并提供便捷的 API 访问。

## 核心能力

### 1. 自动提取 HTTP Header

- **AkskSecurityContextFilter** - 安全上下文过滤器
  - 自动提取以指定前缀开头的 HTTP Header
  - 移除前缀并转换为 camelCase 格式
  - 存储到 Request Attribute（线程池安全）
  - 自动清理，无内存泄漏风险

### 2. 便捷的静态 API

- **SimpleAkskSecurityContextHelper** - 用户上下文 API
  - 类似 Shiro 的便捷 API 风格
  - 基于 Request Attribute 实现
  - 支持获取预定义字段（userId、username、roles 等）
  - 支持获取任意自定义字段
  - 支持数组字段自动提取

### 3. Provider 实现

- **AkskUserContextProvider** - 上下文提供者实现
  - 实现 `SimpleAkskSecurityContextProvider` 接口（来自 resource-core）
  - 适配 SimpleAkskSecurityContextHelper 的静态方法
  - 用于 SimpleAkskSecurityAspect 的依赖注入
  - 从 HTTP Headers 获取上下文数据

### 4. 权限注解

- **@RequireContext** - 要求存在安全上下文
- **@RequireField** - 要求存在指定字段
- **@RequireFieldValue** - 要求字段值匹配
- **@RequireExpression** - 要求 SpEL 表达式为 true

### 5. AOP 权限校验

- **SimpleAkskSecurityAspect** - 安全切面
  - 自动拦截带权限注解的方法
  - 校验失败抛出 `SimpleAkskSecurityException`
  - 支持方法级别和类级别注解
  - 支持自定义错误消息

### 6. Header 名称转换

- **HeaderNameConverter** - 名称转换工具
  - 移除前缀（如 `x-sure-auth-aksk-`）
  - 转换为 camelCase 格式
  - 示例：`x-sure-auth-aksk-user-id` → `userId`

## 架构设计

### Provider 模式

本模块采用 Provider 模式实现上下文提供：

```
simple-aksk-resource-core (定义接口)
    ↓
SimpleAkskSecurityContextProvider (接口)
    ↓
AkskUserContextProvider (实现类)
    ↓
SimpleAkskSecurityContextHelper (静态 API)
```

**工作原理**：
1. **AkskSecurityContextFilter** 从 HTTP Headers 提取用户信息，存储到 Request Attribute
2. **SimpleAkskSecurityContextHelper** 提供静态 API，从 Request Attribute 读取数据
3. **AkskUserContextProvider** 实现 `SimpleAkskSecurityContextProvider` 接口，适配 Helper 的静态方法
4. **SimpleAkskSecurityAspect** 通过 Provider 接口获取上下文，执行权限校验

这种设计的优势：
- ✅ **解耦**：切面不直接依赖 Helper，而是依赖抽象接口
- ✅ **可测试**：可以轻松 mock Provider 进行单元测试
- ✅ **可扩展**：未来可以替换不同的 Provider 实现

## 依赖说明

本模块依赖：
- simple-aksk-resource-core - 资源保护核心
- Spring Boot - 自动配置
- Spring Web - Servlet Filter
- Spring AOP - 权限校验
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
              security-context:
                enable: true  # 是否启用（默认：true）
                header-prefix: x-sure-auth-aksk-  # Header 前缀（默认：x-sure-auth-aksk-）
```

## 使用方式

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-security-context-starter:1.0.1'
}
```

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Web（Servlet Filter）
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Spring AOP（权限注解）
    implementation 'org.springframework.boot:spring-boot-starter-aop'
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

        // 获取角色列表
        List<String> roles = SimpleAkskSecurityContextHelper.getRoles();

        return new UserInfo(userId, username, roles);
    }
}
```

#### 获取自定义字段

```java
// 获取任意字段
String tenantId = SimpleAkskSecurityContextHelper.get("tenantId");
String orgId = SimpleAkskSecurityContextHelper.get("orgId");

// 获取数组字段（自动提取 permissions0, permissions1, ...）
List<String> permissions = SimpleAkskSecurityContextHelper.getList("permissions");

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
@RequireFieldValue(field = "role", value = "admin")  // 要求 role 字段值为 "admin"
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

### 4. securityContext 字段使用场景

`securityContext` 是一个特殊字段，用于传递原始的安全上下文数据（如 JWT token、加密字符串、结构化数据等）。通过 `@RequireExpression` 注解，您可以灵活地解析和验证 securityContext 中的内容。

#### 场景 1：JWT Token 格式验证

```java
@GetMapping("/api/secure/data")
@RequireExpression("#context['securityContext'] != null && #context['securityContext'].startsWith('eyJ')")
public SecureData getSecureData() {
    // 确保 securityContext 是 JWT 格式（以 "eyJ" 开头）
    String jwtToken = SimpleAkskSecurityContextHelper.getSecurityContext();
    return secureService.getData(jwtToken);
}
```

#### 场景 2：简单格式解析（key:value）

```java
@GetMapping("/admin/dashboard")
@RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('role:admin')")
public Dashboard getAdminDashboard() {
    // securityContext 格式: "role:admin,level:5,status:active"
    // 验证是否包含 "role:admin"
    return dashboardService.getAdminDashboard();
}
```

#### 场景 3：JSON 格式解析

```java
@GetMapping("/premium/features")
@RequireExpression("#context['securityContext'] != null && " +
                   "#context['securityContext'].contains('\"userType\":\"premium\"') && " +
                   "#context['securityContext'].contains('\"status\":\"active\"')")
public List<Feature> getPremiumFeatures() {
    // securityContext 格式: {"userType":"premium","status":"active","level":5}
    // 验证 JSON 中包含 userType=premium 和 status=active
    return featureService.getPremiumFeatures();
}
```

#### 场景 4：多租户 + 角色组合验证

```java
@GetMapping("/tenant/{tenantId}/admin/settings")
@RequireExpression("#context['securityContext'] != null && " +
                   "#context['securityContext'].contains('tenantId:tenant-123') && " +
                   "(#context['securityContext'].contains('role:admin') || " +
                   "#context['securityContext'].contains('role:owner'))")
public TenantSettings getTenantSettings(@PathVariable String tenantId) {
    // securityContext 格式: "tenantId:tenant-123,role:admin,permissions:read,write"
    // 验证租户 ID 匹配且角色为 admin 或 owner
    return settingsService.getSettings(tenantId);
}
```

#### 更多示例

更多 securityContext 使用场景（权限列表验证、地域限制、设备信任度、订阅状态等），请参考单元测试：
- `AnnotationIntegrationTest.java` - 包含 40+ 个 securityContext 解析测试用例
- `TestController.java` - 包含完整的 Controller 示例代码

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
| `getRoles()` | `List<String>` | 获取角色列表 |
| `getScope()` | `List<String>` | 获取 Scope 列表 |
| `getSecurityContext()` | `String` | 获取原始 security_context |
| `get(String key)` | `String` | 获取指定字段 |
| `getList(String prefix)` | `List<String>` | 获取数组字段 |
| `getAll()` | `Map<String, String>` | 获取所有字段 |

### Header 映射规则

| HTTP Header | 字段名（camelCase） |
|-------------|-------------------|
| `x-sure-auth-aksk-user-id` | `userId` |
| `x-sure-auth-aksk-username` | `username` |
| `x-sure-auth-aksk-client-id` | `clientId` |
| `x-sure-auth-aksk-tenant-id` | `tenantId` |
| `x-sure-auth-aksk-roles0` | `roles0` |
| `x-sure-auth-aksk-roles1` | `roles1` |
| `x-sure-auth-aksk-security-context` | `securityContext` |

### 数组字段提取

数组字段使用数字后缀（0, 1, 2, ...），`getList()` 方法会自动提取：

```
HTTP Headers:
  x-sure-auth-aksk-roles0: admin
  x-sure-auth-aksk-roles1: operator
  x-sure-auth-aksk-roles2: viewer

Java Code:
  List<String> roles = SimpleAkskSecurityContextHelper.getRoles();
  // 结果: ["admin", "operator", "viewer"]
```

## 工作原理

### 完整流程

```
HTTP Request (with Headers)
    ↓
AkskSecurityContextFilter (提取 Header)
    ↓
Request Attribute (存储上下文)
    ↓
SimpleAkskSecurityContextHelper (静态 API 访问)
    ↓
AkskUserContextProvider (适配器，实现 SimpleAkskSecurityContextProvider)
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

## 测试覆盖

✅ **单元测试**（HeaderNameConverterTest）
- Header 名称转换（单词、多词、空值、null）
- 前缀处理（有前缀、无前缀、null 前缀）

✅ **集成测试**（SecurityContextIntegrationTest）
- 基本字段提取（单个、多个）
- Header 名称转换（camelCase）
- 数组字段提取（roles、scope）
- 自定义字段提取
- securityContext 字段提取
- 边界情况（无 Header、空值、非前缀 Header）

✅ **权限注解测试**（AnnotationIntegrationTest）
- @RequireContext（有/无上下文）
- @RequireField（字段存在/不存在）
- @RequireFieldValue（字段值匹配/不匹配）
- @RequireExpression（基本 EL 表达式、字符串操作、复杂逻辑）
- securityContext 字段解析（40+ 个测试用例）
  - 简单格式解析（key:value）
  - JSON 格式解析
  - JWT token 验证
  - 业务场景（权限、租户、订阅、地域、设备等）

**总计：132 个测试，100% 通过**

## 使用场景

### 场景 1：微服务间调用

```
Shiro 项目（调用方）
    ↓ AKSK 签名 + security_context
资源服务（被调用方）
    ↓ simple-aksk-security-context-starter
自动提取用户信息
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
@RequireFieldValue(field = "role", value = "admin")  // 只有管理员可以删除
public void deleteUser(@PathVariable String id) {
    userService.delete(id);
}
```

## 版本历史

### 1.0.1 (2026-01-28)

依赖优化：
- ✅ 移除未使用的 simple-aksk-core 依赖
- ✅ 优化模块依赖结构

### 1.0.0 (2026-01-27)

初始版本发布：
- ✅ 实现 HTTP Header 自动提取
- ✅ 提供便捷的静态 API（类似 Shiro）
- ✅ 支持 4 种权限注解
- ✅ 基于 Request Attribute（线程池安全）
- ✅ 支持数组字段自动提取
- ✅ 自定义异常（SimpleAkskSecurityException）
- ✅ 完整的测试覆盖

## 许可证

Apache License 2.0
