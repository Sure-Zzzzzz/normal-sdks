# simple-iam-aksk-feign-client-starter

Feign 装配的 IAM openapi client（Spring Cloud OpenFeign 3.x，Spring Boot 2.x 宿主）：直接引用
aksk 底座 `simple-aksk-feign-redis-client-starter:3.0.2`，提供预写好的三份契约接口——
`IamUserFeignClient` / `IamDepartmentFeignClient` / `IamOpenRoleFeignClient`（服务名族内唯一）。
宿主 `@EnableFeignClients` 扫描后 Authorization 头由底座令牌链自动注入，本模块为契约接口层，
无自有装配面。

## 核心能力

### 1. 三契约接口 30 方法

与 RestTemplate 形态（`simple-iam-aksk-resttemplate-client-starter`）逐端点一一对应；
请求体为 `Map<String, Object>`（可选字段 null 不放入），路径注解写契约字面量。

### 2. If-Match 经 @RequestHeader 携带

open-roles 族的四个写方法带 `String ifMatch` 参数（null=首写不携带）；值形如
`open-role:<id>:<revision>`，revision 取自上次响应 wire DTO 的 `revision` 字段。

### 3. wire DTO 直配 Jackson

响应为 `model` 包公开字段 DTO（`IamUserResponse`/`IamOpenRoleResponse` 等 11 个），
字段名逐字对位 server 契约；分页双形态同契约（`IamUserPageResponse` 0 起 vs
`IamOpenRolePageResponse` 1 起）。

### 4. 错误透传

非 2xx 由 Feign 默认 ErrorDecoder 抛 `FeignException`（含状态码），本模块不包装；
Feign 自带重试必须显式关闭（幂等语义由调用方负责）。

## 依赖说明

| 依赖 | 传递方式 | 说明 |
|------|---------|------|
| `simple-iam-client-core:1.0.1` | `api` 编译期传递 | 契约常量/异常族 |
| `simple-aksk-feign-redis-client-starter:3.0.2` | `implementation` 运行时传递 | `@AkskClientFeignClient` 元注解 + 认证拦截器装配 |
| Spring Cloud OpenFeign 3.x | `compileOnly`，**宿主自行引入** | 版本随宿主 Spring Boot 线 |
| Spring Boot AutoConfiguration | `compileOnly` | — |

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-feign-client-starter:1.0.1'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

### 2. 启用扫描 + 配置（aksk 令牌链配置与底座 README 一致）

```java
@SpringBootApplication
@EnableFeignClients(basePackageClasses = IamUserFeignClient.class)
public class Application { }
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              server-url: https://aksk.example.internal
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
        iam:
          client:
            base-url: https://iam.example.internal   # 仅 origin，Feign url 占位符引用
```

### 3. 注入使用

```java
@Service
public class RoleHostService {

    private final IamOpenRoleFeignClient openRoles;

    public RoleHostService(IamOpenRoleFeignClient openRoles) {
        this.openRoles = openRoles;
    }

    public void provision() {
        Map<String, Object> create = new HashMap<>();
        create.put("externalId", externalId);
        create.put("applicationId", applicationId);
        create.put("rootDepartmentId", rootDeptId);
        create.put("name", "角色");
        IamOpenRoleResponse role = openRoles.createOpenRole(create);

        Map<String, Object> rule = new HashMap<>();
        rule.put("manifestVersion", manifestVersion);
        rule.put("manifestDigest", digest);
        rule.put("apiPermissions", apiCodes);
        // If-Match 携带；冲突（428/412）按 FeignException 状态码捕获后重读重算
        IamOpenRoleRuleResponse written = openRoles.putOpenRoleRule(
                role.openRoleId, applicationId,
                "open-role:" + role.openRoleId + ":" + role.revision, rule);
    }
}
```

## 使用场景

| 场景 | 用法 |
|------|------|
| 已用 OpenFeign 的微服务 | 引本件即得 IAM 契约，无需引入 RestTemplate 栈 |
| 服务间级联调用 | Feign 统一超时/熔断治理，IAM 调用并入既有治理面 |
| 声明式偏好团队 | 注解契约接口即全部代码，wire DTO 直读 |

## 安全边界

- 本模块代码零认证/零传输装配（全在底座与 Feign 宿主）
- Feign 自带重试必须显式关闭；凭据与令牌由底座管理
- 消费方 AKP 按接口族申领 API 码与 DATA 范围（详见 core README 授权面表）

## 测试覆盖

- 契约测试 4：经 `@EnableFeignClients` 真链路对 JDK HttpServer 桩逐族断言
  （路径/方法/If-Match 头/双分页 wire/428 透传/元注解挂接）
- 装配测试 2：底座拦截器条件链两态（TokenManager 在场/不在场）

## 兼容性

- 对应 server：`simple-iam-server-starter:1.3.2+`
- Spring Boot 2.7.9 验证（server 部署基线，OpenFeign 3.1.8 线）；OpenFeign 4.x（Spring Boot 3.x）宿主用
  `simple-iam-aksk-feign-client-jakarta-starter`；RestTemplate 栈用
  `simple-iam-aksk-resttemplate-client-starter`
