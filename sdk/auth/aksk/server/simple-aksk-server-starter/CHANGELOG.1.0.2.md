# Changelog 1.0.2

## 新增

- 添加 `auth_server_id` JWT claim，用于多认证服务器场景区分token来源
  - 值从配置项 `jwt.key-id` 中获取
  - 与 JWT Header 的 `kid` 保持一致
  - 用途：API网关（如APISIX）可通过此字段识别token来源，实现多租户或多环境隔离

## 改进

### 代码质量
- 完善 `JwtTokenCustomizer` 类的注释文档
  - 详细说明每个 JWT claim 的用途和使用场景
  - 添加 `security_context` 的使用示例
  - 优化日志输出，合并基础 claims 的日志
- 完善 `SimpleAkskServerConstant` 常量类的注释
  - 为 `JWT_CLAIM_AUTH_SERVER_ID` 添加详细说明
  - 补充使用场景和配置来源说明

### 日志优化
- 合并 `client_id` 和 `auth_server_id` 的日志输出，减少日志量
- 优化异常处理注释，明确区分阻断型异常和非阻断型异常

## 技术细节

### JWT Claims 结构

签发的 JWT Token 包含以下 claims：

**标准 claims**：
- `sub`: 主体标识（等同于 client_id）
- `iss`: 签发者
- `aud`: 受众
- `exp`: 过期时间
- `iat`: 签发时间
- `jti`: JWT ID

**自定义 claims**：
- `client_id`: 客户端ID（AKSK）
- `auth_server_id`: 认证服务器标识（**新增**）
- `client_type`: 客户端类型（platform/user）
- `scope`: 权限范围
- `user_id`: 用户ID（仅用户级AKSK）
- `username`: 用户名（仅用户级AKSK）
- `security_context`: 自定义安全上下文（可选）

### 使用场景示例

#### 场景1：多环境隔离

```yaml
# 开发环境
jwt:
  key-id: aksk-dev-2026

# 生产环境
jwt:
  key-id: aksk-prod-2026
```

API网关可以根据 `auth_server_id` 区分token来源，实现环境隔离。

#### 场景2：多租户架构

```yaml
# 租户A的认证服务器
jwt:
  key-id: aksk-tenant-a

# 租户B的认证服务器
jwt:
  key-id: aksk-tenant-b
```

API网关可以根据 `auth_server_id` 路由到不同的后端服务。

#### 场景3：APISIX集成

APISIX可以通过 `auth_server_id` 识别token来源：

```lua
-- APISIX插件中读取auth_server_id
local jwt = ctx.var.jwt_obj
local auth_server_id = jwt.payload.auth_server_id

if auth_server_id == "aksk-prod-2026" then
    -- 生产环境token，执行严格验证
elseif auth_server_id == "aksk-dev-2026" then
    -- 开发环境token，放宽限制
end
```

## 升级指南

从 1.0.1 升级到 1.0.2：

1. 更新依赖版本：
   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.0.2'
   ```

2. 无需修改配置，`auth_server_id` 会自动从现有的 `jwt.key-id` 配置中读取

3. 重新签发的 JWT Token 会自动包含 `auth_server_id` claim

4. 如果需要在API网关中使用此字段，参考上述场景示例进行配置

## 兼容性

- ✅ 向后兼容：新增的 `auth_server_id` claim 不影响现有功能
- ✅ 配置兼容：无需修改现有配置文件
- ✅ API兼容：所有API接口保持不变
