# simple-iam-aksk-feign-client-jakarta-starter

Feign 装配的 IAM openapi client（Spring Cloud OpenFeign 4.x，Spring Boot 3.x / Jakarta 宿主）：
直接引用 aksk 底座 `simple-aksk-feign-redis-client-jakarta-starter:1.0.0`，提供三份契约接口——
`IamUserFeignClient` / `IamDepartmentFeignClient` / `IamOpenRoleFeignClient`（服务名族内唯一），
与 OpenFeign 3.x 形态（`simple-iam-aksk-feign-client-starter`）同源代码、同一份 wire DTO；
宿主 `@EnableFeignClients` 扫描后认证由底座令牌链注入，本模块为契约接口层，无自有装配面。

## 核心能力

### 1. 三契约接口 30 方法

与 RestTemplate 形态逐端点一一对应；请求体为 `Map<String, Object>`（可选字段 null 不放入），
路径注解写契约字面量。

### 2. If-Match 经 @RequestHeader 携带

open-roles 族四个写方法带 `String ifMatch` 参数（null=首写不携带）；值形如
`open-role:<id>:<revision>`，revision 取自上次响应 wire DTO 的 `revision` 字段。

### 3. wire DTO 直配 Jackson

`model` 包公开字段 DTO（11 个），字段名逐字对位 server 契约；分页双形态同契约
（`IamUserPageResponse` 0 起 vs `IamOpenRolePageResponse` 1 起）。

### 4. 错误透传

非 2xx 由 Feign 默认 ErrorDecoder 抛 `FeignException`（含状态码）；Feign 自带重试必须显式关闭。

## 依赖说明

| 依赖 | 传递方式 | 说明 |
|------|---------|------|
| `simple-iam-client-core:1.0.1` | `api` 编译期传递 | 契约常量/异常族 |
| `simple-aksk-feign-redis-client-jakarta-starter:1.0.0` | `implementation` 运行时传递 | `@AkskClientFeignClient` 元注解 + 认证装配（jakarta 线） |
| Spring Cloud OpenFeign 4.2.0 | `compileOnly`，**宿主自行引入** | 模块内 BOM `spring-cloud-dependencies:2024.0.0` + force 纠偏（形态同 aksk feign jakarta 底座） |
| Spring Boot AutoConfiguration | `compileOnly` | — |

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-feign-client-jakarta-starter:1.0.1'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

### 2. 启用扫描 + 配置（aksk jakarta 令牌链配置与底座 README 一致）

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
        IamOpenRoleRuleResponse written = openRoles.putOpenRoleRule(
                role.openRoleId, applicationId,
                "open-role:" + role.openRoleId + ":" + role.revision, rule);
    }
}
```

## 与 OpenFeign 3.x 形态的差异

| 维度 | 3.x（`simple-iam-aksk-feign-client-starter`） | 本模块 |
|------|------|------|
| Spring Boot | 2.x | 3.x（Java 17+） |
| Spring Cloud OpenFeign | 3.1.8（根构建变体管理） | 4.2.0（模块内 BOM 纠偏） |
| 传输客户端 | feign-httpclient（hc4） | feign-hc5（openfeign 4.x 弃 hc4） |
| 契约接口/wire DTO | 同一份源码 | 同一份源码 |

## 安全边界

- 本模块代码零认证/零传输装配（全在底座与 Feign 宿主）
- Feign 自带重试必须显式关闭；消费方 AKP 按接口族申领 API 码与 DATA 范围
  （详见 core README 授权面表）

## 测试覆盖

- 契约测试 4：经 `@EnableFeignClients` 真链路逐族断言（路径/方法/If-Match 头/双分页 wire/
  428 透传/元注解挂接，OpenFeign 4.2.0 链路）
- 装配测试 2：底座拦截器条件链两态

## 兼容性

- 对应 server：`simple-iam-server-starter:1.3.2+`
- 兼容矩阵全绿（2026-10-08，坐标形态）：Boot 3.4.2×Cloud 2024.0.0×OpenFeign 4.2.0；Boot 3.3.13 / 3.2.12 × Cloud 2023.0.6 × OpenFeign 4.1.5；各配 Java 17 / 21
- SB2 宿主用 `simple-iam-aksk-feign-client-starter`；RestTemplate 栈（hc5）用
  `simple-iam-aksk-resttemplate-client-jakarta-starter`
