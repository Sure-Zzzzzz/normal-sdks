# Simple AKSK Server Starter

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Authorization Server](https://img.shields.io/badge/Spring%20Authorization%20Server-0.4.0-brightgreen.svg)](https://spring.io/projects/spring-authorization-server)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 Spring Authorization Server 的 AKSK（Access Key / Secret Key）认证服务器 Starter，支持平台级和用户级 AKSK 管理，提供完整的 OAuth2 Client Credentials 授权流程。

## 特性

- ✅ **双层级 AKSK 管理**：支持平台级（AKP）和用户级（AKU）两种 AKSK 类型
- ✅ **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.0，完全符合 OAuth2 规范
- ✅ **JWT Token 签发**：使用 RSA 算法签发 JWT Token，支持自定义公私钥
- ✅ **Client 管理 API**：提供内网 REST API，支持 Client 的创建、查询（分页/批量）、删除、权限同步等操作
- ✅ **Token 管理 API**：提供内网 REST API，支持 Token 的查询、删除、清理过期 Token、统计等操作
- ✅ **Admin 管理界面**：提供 Web 管理界面，支持 AKSK 和 Token 的创建、查询、启用/禁用、删除等操作
- ✅ **Redis 缓存支持**：可选的 Redis 缓存，提升 Token 验证性能
- ✅ **灵活配置**：支持多种配置方式，可按需启用/禁用功能模块
- ✅ **安全上下文传递**：支持在 Token 中携带自定义安全上下文信息

## 快速开始

### 1. 添加依赖

#### 1.1 添加 AKSK Server Starter

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.0.1'
}
```

#### 1.2 添加必需的运行时依赖

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Boot Web（提供 REST API 和 Admin 管理界面）
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Spring Security（OAuth2 认证基础）
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // Spring Data JPA（数据库访问）
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // MySQL 驱动（根据您的数据库版本选择）
    runtimeOnly 'mysql:mysql-connector-java:8.0.33'
}
```

**可选依赖：**

```gradle
dependencies {
    // Redis 缓存（启用 redis.enabled=true 时需要）
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

**版本说明**：
- 建议使用 Spring Boot 2.7.x 版本
- MySQL 驱动版本请根据您的 MySQL 版本选择（5.7+ 使用 8.0.x，8.0+ 使用 8.0.x）

### 2. 初始化数据库

执行 SQL 脚本初始化数据库表结构：

```bash
# 1. 创建数据库
mysql -u root -p < docs/00_database.sql

# 2. 创建表结构
mysql -u root -p sure_auth_aksk < docs/01_schema.sql
```

详细说明请参考 [数据库初始化指南](docs/README.md)

### 3. 配置应用

在 `application.yml` 中添加配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sure_auth_aksk?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              # JWT 配置
              jwt:
                key-id: sure-auth-aksk-2026
                expires-in: 3600
                # 支持三种格式：
                # 1. PEM文件路径：classpath:keys/public.pem 或 file:/etc/keys/public.pem
                # 2. PEM内容：-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----
                # 3. Base64编码：MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
                public-key: classpath:keys/public.pem
                private-key: classpath:keys/private.pem

              # Redis 配置（可选）
              redis:
                enabled: false  # 是否启用 Redis 缓存
                token:
                  me: my-app  # 应用标识，用于区分多个实例

              # Admin 管理页面配置
              admin:
                enabled: true  # 是否启用 Admin 管理页面
                username: admin
                password: admin123
                session-timeout-minutes: 30
```

### 4. 启动应用

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 5. 访问 Admin 管理页面

启动后访问：`http://localhost:8080/admin`

使用配置的用户名密码登录，即可管理 AKSK。

## 使用指南

### 创建 AKSK

#### 方式1：通过 Admin 管理页面

1. 访问 `http://localhost:8080/admin`
2. 登录后点击"创建平台级AKSK"或"创建用户级AKSK"
3. 填写相关信息后提交
4. 保存生成的 Client ID 和 Client Secret（仅显示一次）

#### 方式2：通过 REST API

```java
@Autowired
private ClientManagementService clientManagementService;

// 创建平台级 AKSK
ClientInfo platformClient = clientManagementService.createPlatformClient("My Platform Client");
System.out.println("Client ID: " + platformClient.getClientId());      // AKP + 20位随机字符
System.out.println("Client Secret: " + platformClient.getClientSecret()); // SK + 40位随机字符

// 创建用户级 AKSK
ClientInfo userClient = clientManagementService.createUserClient(
    "user123",           // 用户ID
    "张三",              // 用户名
    "My User Client"    // AKSK名称
);
```

### 获取 Access Token

使用 AKSK 换取 Access Token：

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials&scope=read write"
```

响应示例：

```json
{
  "access_token": "eyJraWQiOiJzdXJlLWF1dGgtYWtzay0yMDI2IiwiYWxnIjoiUlMyNTYifQ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

### Token 换取流程与 Scope 说明

#### 换取 Token 流程

```
1. 客户端使用 AKSK 向 /oauth2/token 发送请求
   ↓
2. Server 验证 AKSK（client_id 和 client_secret）
   ↓
3. Server 签发 JWT Token（包含 client_id、scope、user_id 等信息）
   ↓
4. 客户端使用 Access Token 调用 API
   ↓
5. API 网关（如 APISIX）验证 JWT 签名和过期时间
   ↓
6. 验证通过，请求转发到业务服务
```

#### Scope 作用与使用

**什么是 Scope？**

Scope 是 OAuth2 标准的权限表示方式，用于限制 Token 的访问范围。

**Scope 命名规范**：`<资源>:<操作>`

```
api:read        - 读取 API
api:write       - 写入 API
api:admin       - API 管理权限
order:create    - 创建订单
order:delete    - 删除订单
user:admin      - 用户管理
```

**Scope 使用方式**：

| 请求方式 | 说明 | 示例 |
|---------|------|------|
| **不传 scope** | 使用 Client 创建时配置的所有 scopes（默认 `read,write`） | `-d "grant_type=client_credentials"` |
| **指定 scope** | 使用指定的 scope（必须在 Client 的授权范围内） | `-d "grant_type=client_credentials&scope=read"` |
| **指定多个 scope** | 使用空格分隔多个 scope | `-d "grant_type=client_credentials&scope=read write"` |
| **指定不存在的 scope** | 报错 `invalid_scope` | `-d "grant_type=client_credentials&scope=admin"` ❌ |

**示例**：

```bash
# 1. 不传 scope - 使用所有默认 scopes
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials"
# Token scope: read write

# 2. 指定 scope - 只使用 read 权限
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials&scope=read"
# Token scope: read

# 3. 指定多个 scope
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials&scope=read write"
# Token scope: read write

# 4. 指定不存在的 scope - 报错
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials&scope=admin"
# 响应: {"error":"invalid_scope","error_description":"The requested scope is invalid, unknown, or malformed"}
```

**重要提示**：
- ✅ Scope 必须在 Client 创建时配置的范围内
- ✅ 不传 scope 时，使用 Client 的所有默认 scopes
- ✅ 可以通过指定 scope 来缩小权限范围（最小权限原则）
- ❌ 不能通过指定 scope 来扩大权限范围

**详细使用指南**：请参考 [USER_GUIDE.md](../USER_GUIDE.md)

### 批量查询 Client 信息

使用获取的 Access Token 批量查询 Client 信息（内网接口）：

```bash
# 1. 先获取 Access Token
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials" | jq -r '.access_token')

# 2. 使用 Token 批量查询 Client 信息（最多100个ID）
curl -X GET "http://localhost:8080/api/client?clientIds=AKP111,AKP222,AKU333" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

响应示例：

```json
{
  "clients": {
    "AKP111": {
      "clientId": "AKP111",
      "clientName": "Platform Client 1",
      "clientType": 1,
      "clientTypeName": "平台级",
      "scopes": ["read", "write"],
      "issuedAt": "2026-01-22T10:00:00Z",
      "enabled": true
    },
    "AKP222": {
      "clientId": "AKP222",
      "clientName": "Platform Client 2",
      "clientType": 1,
      "clientTypeName": "平台级",
      "scopes": ["read"],
      "issuedAt": "2026-01-22T11:00:00Z",
      "enabled": true
    },
    "AKU333": {
      "clientId": "AKU333",
      "clientName": "User Client 1",
      "clientType": 2,
      "clientTypeName": "用户级",
      "ownerUserId": "user001",
      "ownerUsername": "张三",
      "scopes": ["read", "write"],
      "issuedAt": "2026-01-22T12:00:00Z",
      "enabled": true
    }
  }
}
```

**说明**:
- `clientIds` 参数支持最多100个ID,多个ID用逗号分隔
- 不存在的ID会被忽略,只返回存在的Client
- 返回结果为Map结构,key为clientId,value为Client详细信息

### 携带自定义安全上下文

在请求 Token 时可以传递自定义的安全上下文信息：

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials&scope=read write&security_context={\"tenant_id\":\"tenant123\",\"region\":\"cn-north\"}"
```

生成的 JWT Token 中会包含 `security_context` claim。

### 验证 Token

使用 Spring Security 验证 Token：

```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                )
            );
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // 配置 JWT 解码器，使用公钥验证签名
        return NimbusJwtDecoder.withPublicKey(publicKey()).build();
    }
}
```

## 配置说明

### JWT 配置

| 配置项 | 说明 | 默认值 | 必填 |
|--------|------|--------|------|
| `jwt.key-id` | JWT Key ID | sure-auth-aksk-2026 | 否 |
| `jwt.expires-in` | Token 过期时间（秒） | 3600 | 否 |
| `jwt.public-key` | RSA 公钥 | - | 是 |
| `jwt.private-key` | RSA 私钥 | - | 是 |
| `jwt.security-context-max-size` | Security Context 最大大小（字节） | 4096 | 否 |

**密钥格式支持**：
1. PEM 文件路径：`classpath:keys/public.pem` 或 `file:/etc/keys/public.pem`
2. PEM 内容：`-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----`
3. Base64 编码：`MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...`

### Redis 配置

| 配置项 | 说明 | 默认值 | 必填 |
|--------|------|--------|------|
| `redis.enabled` | 是否启用 Redis 缓存 | false | 否 |
| `redis.token.me` | 应用标识 | default | 否 |

启用 Redis 后，OAuth2 授权信息会同时缓存到 Redis，提升性能。

### Admin 配置

| 配置项 | 说明 | 默认值 | 必填 |
|--------|------|--------|------|
| `admin.enabled` | 是否启用 Admin 管理页面 | true | 否 |
| `admin.username` | Admin 用户名 | admin | 否 |
| `admin.password` | Admin 密码 | - | 是（启用时） |
| `admin.session-timeout-minutes` | Session 超时时间（分钟） | 30 | 否 |

设置 `admin.enabled=false` 可以完全禁用 Admin 管理页面。

## AKSK 类型说明

### 平台级 AKSK（AKP）

- **前缀**：`AKP`（Access Key Platform）
- **用途**：用于平台级服务调用，不关联具体用户
- **示例**：`AKP1a2b3c4d5e6f7g8h9i0`
- **适用场景**：
  - 服务间调用
  - 后台任务
  - 系统级操作

### 用户级 AKSK（AKU）

- **前缀**：`AKU`（Access Key User）
- **用途**：用于用户级服务调用，关联具体用户
- **示例**：`AKU1a2b3c4d5e6f7g8h9i0`
- **适用场景**：
  - 用户 API 调用
  - 移动端应用
  - 第三方集成

### Secret Key（SK）

- **前缀**：`SK`（Secret Key）
- **长度**：43 个字符（SK + 40 位随机字符）
- **示例**：`SK1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0`
- **安全性**：使用 BCrypt 加密存储，仅在创建时返回明文

## API 接口

### OAuth2 标准接口

| 端点 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/oauth2/token` | POST | 获取 Access Token | Basic Auth (AKSK) |
| `/.well-known/oauth-authorization-server` | GET | OAuth2 授权服务器元数据 | - |
| `/oauth2/jwks` | GET | JWKS 公钥端点 | - |

### Client 管理 API（内网接口）

| 端点 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/api/client` | POST | 创建 Client（支持平台级和用户级） | 内网访问 |
| `/api/client` | GET | 查询 Client 列表（支持分页、过滤和批量查询） | 内网访问 |
| `/api/client/{clientId}` | GET | 查询 Client 详情 | 内网访问 |
| `/api/client/{clientId}` | DELETE | 删除 Client | 内网访问 |
| `/api/client?owner_user_id={userId}` | PATCH | 批量同步用户权限 | 内网访问 |

**查询示例**:
```bash
# 分页查询
GET /api/client?type=platform&page=1&size=20

# 批量查询（最多100个ID）
GET /api/client?clientIds=id1,id2,id3

# 批量查询响应示例:
{
  "clients": {
    "AKP123...": {
      "clientId": "AKP123...",
      "clientName": "Client 1",
      ...
    },
    "AKP456...": {
      "clientId": "AKP456...",
      "clientName": "Client 2",
      ...
    }
  }
}

# 按用户查询
GET /api/client?ownerUserId=user123
```

### Token 管理 API（内网接口）

| 端点 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/api/token` | GET | 查询 Token 列表（MySQL） | 内网访问 |
| `/api/token/redis` | GET | 查询 Redis 中的 Token 列表 | 内网访问 |
| `/api/token/{id}` | GET | 查询 Token 详情 | 内网访问 |
| `/api/token/{id}` | DELETE | 删除单个 Token | 内网访问 |
| `/api/token/expired` | DELETE | 清理过期 Token | 内网访问 |
| `/api/token/statistics` | GET | 获取 Token 统计信息 | 内网访问 |

### Admin 管理页面（内网访问）

| 端点 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/admin` | GET | 管理首页 - AKSK 列表 | Session |
| `/admin/login` | GET/POST | Admin 登录 | - |
| `/admin/logout` | POST | Admin 登出 | Session |
| `/admin/create` | GET/POST | 创建 AKSK | Session |
| `/admin/delete/{clientId}` | POST | 删除 AKSK | Session |
| `/admin/enable/{clientId}` | POST | 启用 AKSK | Session |
| `/admin/disable/{clientId}` | POST | 禁用 AKSK | Session |
| `/admin/detail/{clientId}` | GET | 查看 AKSK 详情 | Session |
| `/admin/tokens` | GET | Token 管理页面 | Session |
| `/admin/tokens/{id}` | DELETE | 删除 Token | Session |
| `/admin/tokens/expired` | DELETE | 清理过期 Token | Session |

## 数据库表结构

### oauth2_registered_client

存储客户端注册信息（AKSK）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(100) | 主键ID |
| client_id | VARCHAR(100) | 客户端ID（AKSK） |
| client_secret | VARCHAR(200) | 客户端密钥（BCrypt加密） |
| client_name | VARCHAR(200) | 客户端名称 |
| scopes | VARCHAR(1000) | 权限范围 |
| owner_user_id | VARCHAR(255) | 所属用户ID（用户级） |
| owner_username | VARCHAR(255) | 所属用户名（用户级） |
| client_type | INTEGER | 客户端类型（1=平台级，2=用户级） |
| enabled | TINYINT(1) | 是否启用 |

### oauth2_authorization

存储授权信息（Token）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(100) | 主键ID |
| registered_client_id | VARCHAR(100) | 关联的客户端ID |
| access_token_value | BLOB | Access Token 值 |
| access_token_issued_at | TIMESTAMP | Token 签发时间 |
| access_token_expires_at | TIMESTAMP | Token 过期时间 |
| ... | ... | 其他 OAuth2 标准字段 |

**重要提示**：
- 删除 `oauth2_registered_client` 记录时，`oauth2_authorization` 中的相关记录**不会自动删除**
- 这会导致孤儿数据累积，建议定期清理过期的授权记录
- 可以通过以下方式清理：
  ```sql
  -- 清理过期的授权记录
  DELETE FROM oauth2_authorization
  WHERE access_token_expires_at < NOW();

  -- 清理孤儿授权记录
  DELETE FROM oauth2_authorization
  WHERE registered_client_id NOT IN (
    SELECT id FROM oauth2_registered_client
  );
  ```

## 常见问题

### 1. 如何生成 RSA 密钥对？

```bash
# 生成私钥
openssl genrsa -out private.pem 2048

# 从私钥生成公钥
openssl rsa -in private.pem -pubout -out public.pem
```

### 2. 如何禁用 Admin 管理页面？

在 `application.yml` 中设置：

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server.admin.enabled: false
```

### 3. Redis 是否必需？

不是必需的。Redis 仅用于缓存 OAuth2 授权信息以提升性能，默认使用 MySQL 存储。

### 4. 如何自定义 Token 过期时间？

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server.jwt.expires-in: 7200  # 2小时
```

### 5. 如何自定义 Security Context 大小限制？

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server.jwt.security-context-max-size: 8192  # 8KB
```

## 版本历史

### 1.0.1 (2026-01-31)

**新增功能**：
- ✅ 支持换token时scope参数可选（不传scope时自动使用数据库注册的scope）
- ✅ Admin创建页面支持指定scopes（默认值：`/api/**`）
- ✅ Admin详情页支持查看和编辑scopes（RESTful PATCH API）

**安全性增强**：
- ✅ `/api/client` 接口不再返回 `clientSecret` 字段（仅创建时返回一次）
- ✅ 数据库无scope时抛出异常，防止数据完整性问题

**体验优化**：
- ✅ Admin详情页编辑scope成功后先关闭Modal再刷新页面
- ✅ Admin首页启用/禁用AKSK成功后直接刷新，不再弹出提示
- ✅ Token测试页面scope输入框支持留空

**Bug修复**：
- ✅ 修复 `disableClient` 和 `enableClient` 使用错误的错误码

### 1.0.0 (2026-01-19)

初始版本发布：
- ✅ 支持平台级和用户级 AKSK 管理
- ✅ 基于 Spring Authorization Server 0.4.0
- ✅ JWT Token 签发与验证
- ✅ Admin Web 管理界面
- ✅ Redis 缓存支持
- ✅ 自定义安全上下文传递
- ✅ Token 管理功能

## 技术栈

- Spring Boot 2.7.x
- Spring Security 5.7.x
- Spring Authorization Server 0.4.0
- Spring Data JPA
- Spring Data Redis
- Thymeleaf
- MySQL 5.7+ / 8.0+
- Lombok
- JUnit 5

## 许可证

Apache License 2.0

## 联系方式

- 作者：surezzzzzz
- GitHub：https://github.com/Sure-Zzzzzz/normal-sdks
- 问题反馈：https://github.com/Sure-Zzzzzz/normal-sdks/issues

## 参考资料

- [Spring Authorization Server 官方文档](https://docs.spring.io/spring-authorization-server/docs/0.4.0/reference/html/index.html)
- [OAuth 2.0 RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)
- [JWT RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519)
