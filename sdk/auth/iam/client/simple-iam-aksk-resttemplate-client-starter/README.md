# simple-iam-aksk-resttemplate-client-starter

RestTemplate 装配的 IAM openapi client（Spring Boot 2.x 宿主）：直接引用 aksk 底座
`simple-aksk-resttemplate-redis-client-starter:3.0.2`，连接池、超时与 AKSK 认证头全部复用底座
`akskClientRestTemplate`，本模块在其上装配 core 三接口的默认实现——
`IamUserRestTemplateClient` / `IamDepartmentRestTemplateClient` / `IamOpenRoleRestTemplateClient`。

## 核心能力

### 1. 三接口 29 方法全覆盖

- **IamUserClient**（11）：用户分页/详情/角色/启停/删除/重置密码/挂摘角色
- **IamDepartmentClient**（5）：部门列表/详情/增改删
- **IamOpenRoleClient**（13）：受委托角色全生命周期，含 If-Match 乐观并发写

### 2. 认证零自研

宿主装配任一 aksk 令牌链后，底座自动向出站请求注入 `Authorization: Bearer` 头；
本模块代码零凭据接触，AKSK 侧配置与令牌链 starter 的 README 一致。

### 3. 双分页形态自动处理

users/departments 族返回 `IamSpringPage`（Spring Page wire：content/totalElements，**页码 0 起**）；
open-roles 族返回 `IamOpenRolePage`（server 自持：items/total，**页码 1 起**）。
两种形态是 server 契约事实，接口 javadoc 已注明，调用方按族取用不会混。

### 4. 错误透传

非 2xx 直接透传 `HttpStatusCodeException`（状态码与错误体保留），不包装；
2xx 形状不符契约抛 core `IamClientProtocolException`。

## 依赖说明

| 依赖 | 传递方式 | 说明 |
|------|---------|------|
| `simple-iam-client-core:1.0.0` | `api` 编译期传递 | 契约层：接口/模型/常量/异常族 |
| `simple-aksk-resttemplate-redis-client-starter:3.0.2` | `implementation` 运行时传递 | aksk 底座：akskClientRestTemplate + 令牌链 |
| Spring Boot Web / HttpClient 4 | `compileOnly`，**宿主自行引入** | RestTemplate 与连接池 |
| Spring Boot AutoConfiguration | `compileOnly` | 自动装配 |

## 快速开始

### 1. 添加依赖（宿主自引 Web 与 HttpClient）

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-resttemplate-client-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.apache.httpcomponents:httpclient:4.5.13'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

### 2. 配置应用（完整链：Redis Route → 令牌链 → 底座模板 → IAM client）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:                        # 令牌链缓存的数据源（底座要求）
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
            client:                     # aksk 底座：令牌链（凭据经部署环境注入）
              enable: true
              server-url: https://aksk.example.internal
              token-endpoint: /oauth2/token
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
              resttemplate:
                enable: true            # 底座 akskClientRestTemplate Bean 开关
        iam:
          client:                       # 本模块：两键
            enable: true
            base-url: https://iam.example.internal   # 仅 origin，基路径 /iam/api 契约固定
```

### 3. 注入使用

```java
@Service
public class UserSyncService {

    private final IamUserClient users;
    private final IamOpenRoleClient openRoles;

    public UserSyncService(IamUserClient users, IamOpenRoleClient openRoles) {
        this.users = users;
        this.openRoles = openRoles;
    }

    public void sync() {
        // users 族：Spring Page wire（页码 0 起）
        IamSpringPage<IamUser> page = users.listUsers(null, null, "张", 0, 20);
        IamUser admin = users.getUser(subjectId);
        List<String> roles = users.getUserRoles(subjectId);   // 角色编码裸列表

        // open-roles 族：If-Match 乐观并发写
        IamOpenRole role = openRoles.createOpenRole(externalId, applicationId, rootDeptId, "角色", null);
        IamOpenRoleRule rule = openRoles.putOpenRoleRule(role.getOpenRoleId(), applicationId,
                "open-role:" + role.getOpenRoleId() + ":" + role.getRevision(),   // revision 取自响应模型
                manifestVersion, manifestDigest, pagePermissions, apiPermissions, dataGrant);
    }
}
```

**If-Match 冲突处理**：缺条件 428、版本旧 412 均透传 `HttpStatusCodeException`——捕获后重读
`getOpenRole()` 取新 revision 重算差异再发，不盲重发。

## 使用场景

| 场景 | 用法 |
|------|------|
| 组织同步（HR→IAM） | users + departments 族；DATA 授权部门范围求交由 server 执行 |
| 服务代理角色托管 | open-roles 族全链：create → putRule → mount/unmount（均带 If-Match） |
| 只读消费（清单/目录） | organization-directories / target-applications 读端点，无并发头 |

## 安全边界

- 凭据、令牌由底座管理；本模块日志埋点仅记方法/路径/状态码，零敏感数据
- 不内置重试；幂等语义由 server 契约与调用方负责
- 消费方 AKP 按接口族申领 API 码与 DATA 范围（详见 core README 授权面表）

## 测试覆盖

- 契约测试 4：29 方法逐端点断言（URL/方法/请求体 STRICT/If-Match 头/双分页解析/revision 直取/4xx 透传）
- 装配测试 4：默认关/正常装配三客户端/缺底座模板响亮失败/非法 origin 拒启
- 端到端 6：AKP → aksk 底座真令牌链 → dual-identity 宿主 → 真 IAM openapi
  （users 分页/详情/角色/keyword/部门列表/详情/404 透传，2026-10-08 全绿）

## 兼容性

- 对应 server：`simple-iam-server-starter:1.3.2+`
- Spring Boot 2.7.9 验证（server 部署基线），自动配置经 `spring.factories` 注册
- Spring Boot 3.x 宿主用 `simple-iam-aksk-resttemplate-client-jakarta-starter`；
  Feign 形态用 `simple-iam-aksk-feign-client-starter`
