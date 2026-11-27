# simple-iam-aksk-resttemplate-client-jakarta-starter

RestTemplate 装配的 IAM openapi client（Spring Boot 3.x / Jakarta 宿主，Apache HttpClient 5 连接池）：
直接引用 aksk 底座 jakarta 线 `simple-aksk-resttemplate-redis-client-jakarta-starter:1.1.0`，
在底座 `akskClientRestTemplate`（hc5）之上装配 core 十一接口（含 Portal 可信应用路由投影）的默认实现——
`IamUserRestTemplateClient` / `IamDepartmentRestTemplateClient` / `IamOpenRoleRestTemplateClient` 等，含 `IamApplicationRestTemplateClient`、六族新实现和 `IamPortalApplicationRestTemplateClient`。
契约语义（十一接口 49 方法/双分页/If-Match/错误透传）与 SB2 形态完全同标。

> **1.0.3**：契约对齐 server 1.3.8，新增 Portal 可信应用路由投影 Jakarta RestTemplate 实现；总计 49 方法。**1.1.1**：manifest 解析 `dataResources`，依赖 `simple-iam-client-core:1.0.5`；旧 Server 缺字段保持 `null`。

## 核心能力

### 1. 契约接口族 49 方法全覆盖

- **十一族 49 方法**：既有十族 48 方法加 Portal 可信应用路由投影 1 方法，详见 client-core 契约。

### 2. 认证零自研（jakarta 线底座）

宿主装配 aksk jakarta 线令牌链（`simple-aksk-redis-token-manager-jakarta-starter` 等）后，
底座自动注入 `Authorization: Bearer` 头；本模块代码零凭据接触。

### 3. 双分页形态自动处理

users/departments 族返回 `IamSpringPage`（**页码 0 起**）；open-roles 族返回
`IamOpenRolePage`（**页码 1 起**）——server 契约事实，接口 javadoc 已注明。

### 4. 错误透传

非 2xx 透传 `HttpStatusCodeException`（Spring 6 下 `getStatusCode().value()` 保留状态码），
不包装；2xx 形状不符抛 core `IamClientProtocolException`。

## 依赖说明

| 依赖 | 传递方式 | 说明 |
|------|---------|------|
| `simple-iam-client-core:1.0.5` | `api` 编译期传递 | 契约层：接口/模型/常量/异常族（Java 8 字节码，双线共用） |
| `simple-aksk-resttemplate-redis-client-jakarta-starter:1.1.0` | `implementation` 运行时传递 | aksk 底座 jakarta 线：akskClientRestTemplate（hc5）+ 令牌链 |
| Spring Boot 3 Web / HttpClient 5 | `compileOnly`，**宿主自行引入** | RestTemplate 与连接池 |
| Spring Boot AutoConfiguration | `compileOnly` | 自动装配 |

## 快速开始

### 1. 添加依赖（宿主自引 Web 与 HttpClient 5）

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-resttemplate-client-jakarta-starter:1.1.1'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.apache.httpcomponents.client5:httpclient5'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

### 2. 配置应用（完整链：Redis Route → jakarta 令牌链 → 底座模板 → IAM client）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: 127.0.0.1
                port: 6379
                database: 0
        auth:
          aksk:
            client:
              enable: true
              server-url: https://aksk.example.internal
              token-endpoint: /oauth2/token
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
              resttemplate:
                enable: true
        iam:
          client:
            enable: true
            base-url: https://iam.example.internal   # 仅 origin，基路径 /iam/api 契约固定
```

### 3. 注入使用

```java
@Service
public class UserSyncService {

    private final IamUserClient users;

    public UserSyncService(IamUserClient users) {
        this.users = users;
    }

    public void sync() {
        // 与 SB2 形态同一 core 接口；Spring Page wire（页码 0 起）
        IamSpringPage<IamUser> page = users.listUsers(null, null, null, 0, 20);
        List<String> roles = users.getUserRoles(subjectId);
    }
}
```

If-Match 乐观并发用法与 SB2 形态一致（revision 取自响应模型，428/412 透传后重读重算）。

## 与 SB2 形态的差异

| 维度 | SB2（`simple-iam-aksk-resttemplate-client-starter`） | 本模块 |
|------|------|------|
| Spring Boot | 2.x（建议 2.7.x） | 3.x（Java 17+） |
| 连接池 | Apache HttpClient 4 | Apache HttpClient 5 |
| 自动配置注册 | `spring.factories` | `AutoConfiguration.imports` 单入口 |
| 契约/接口/模型 | 同一份 `simple-iam-client-core:1.0.5`，manifest 含 DATA 申报 | 同左 |

## 安全边界

- 凭据、令牌由底座管理；日志埋点仅记方法/路径/状态码，零敏感数据
- 不内置重试；消费方 AKP 按接口族申领 API 码与 DATA 范围（详见 core README 授权面表）

## 测试覆盖

- 1.1.1 全量测试 13：与 SB2 同标的契约、装配、manifest DATA 声明和缺失申报
  保持 `null` 均无失败/错误/跳过。

## 兼容性

- 对应 server：`simple-iam-server-starter:1.3.8+`
- 本轮坐标验证：Spring Boot 3.4.2，Java 17；其他 Spring Boot 档位不在本版本验收范围内，jakarta 线独立走号
- SB2 宿主用 `simple-iam-aksk-resttemplate-client-starter`；Feign 4.x 形态用
  `simple-iam-aksk-feign-client-jakarta-starter`

## AKP Profile

本模块支持 AKSK Client 多 AKP Profile。调用方在 AKSK Client 底座的 `auth.aksk.client.profiles.<name>`
中声明 Profile，再通过本模块配置键 `io.github.surezzzzzz.sdk.iam.client.aksk-profile` 选择 Profile。

- 配置留空或 `default` 时使用顶层默认 AKP，原有接入不变。
- 配置命名 Profile 时，RestTemplate 走底座 Profile 模板；Feign 只挂一个 Profile 认证拦截器。
- Profile 不存在或凭据不完整时启动失败，不静默改用默认 AKP。
- Secret 由 AKSK 底座管理；本模块不输出 Secret、clientId、Token 或 Authorization。
