# simple-iam-aksk-resttemplate-client-jakarta-starter

RestTemplate 装配的 IAM openapi client（Spring Boot 3.x / Jakarta 宿主，Apache HttpClient 5 连接池）：
直接引用 aksk 底座 jakarta 线 `simple-aksk-resttemplate-redis-client-jakarta-starter:1.0.0`，
在底座 `akskClientRestTemplate`（hc5）之上装配 core 三接口的默认实现——
`IamUserRestTemplateClient` / `IamDepartmentRestTemplateClient` / `IamOpenRoleRestTemplateClient`。
契约语义（三接口 30 方法/双分页/If-Match/错误透传）与 SB2 形态完全同标。

## 核心能力

### 1. 三接口 30 方法全覆盖

- **IamUserClient**（12）：用户分页/详情/角色/启停/删除/重置密码/挂摘角色/页面准入应用清单（单码 `iam:portal:api`，无 DATA 面）
- **IamDepartmentClient**（5）：部门列表/详情/增改删
- **IamOpenRoleClient**（13）：受委托角色全生命周期，含 If-Match 乐观并发写

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
| `simple-iam-client-core:1.0.1` | `api` 编译期传递 | 契约层：接口/模型/常量/异常族（Java 8 字节码，双线共用） |
| `simple-aksk-resttemplate-redis-client-jakarta-starter:1.0.0` | `implementation` 运行时传递 | aksk 底座 jakarta 线：akskClientRestTemplate（hc5）+ 令牌链 |
| Spring Boot 3 Web / HttpClient 5 | `compileOnly`，**宿主自行引入** | RestTemplate 与连接池 |
| Spring Boot AutoConfiguration | `compileOnly` | 自动装配 |

## 快速开始

### 1. 添加依赖（宿主自引 Web 与 HttpClient 5）

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-resttemplate-client-jakarta-starter:1.0.1'
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
| 契约/接口/模型 | 同一份 `simple-iam-client-core:1.0.1` | 同左 |

## 安全边界

- 凭据、令牌由底座管理；日志埋点仅记方法/路径/状态码，零敏感数据
- 不内置重试；消费方 AKP 按接口族申领 API 码与 DATA 范围（详见 core README 授权面表）

## 测试覆盖

- 契约测试 4：30 方法逐端点断言（同 SB2 契约集，断言随 Spring 6 API 适配）
- 装配测试 4：默认关/正常装配三客户端/缺底座模板响亮失败/非法 origin 拒启

## 兼容性

- 对应 server：`simple-iam-server-starter:1.3.2+`
- Spring Boot 3.4.2 / 3.3.13 / 3.2.12 × Java 17 / 21 兼容矩阵全绿（2026-10-08，坐标形态），jakarta 线独立走号
- SB2 宿主用 `simple-iam-aksk-resttemplate-client-starter`；Feign 4.x 形态用
  `simple-iam-aksk-feign-client-jakarta-starter`
