# smart-redis-limiter-management-iam-directory-starter

限流管理的 IAM 用户目录适配件：目录提供方的 USER 维度对象检索改走 IAM openapi 实时检索，
其余调用转发被装饰的原实现（管理件配置式或宿主自有实现）。

## 解决什么

管理面的对象目录检索（`listObjects`）在 USER 维度需要真实人员名单，静态配置名单会与身份事实漂移。
本适配件把 USER 维度转发到 IAM 用户查询（`IamUserClient.listUsers`，关键字过滤、页大小取 limit），
目录稳定 ID 使用公开主体 `subjectId`（IAM 1.3.5 起 users 族响应回传），与运行端令牌主体形态对位；
服务清单与资源声明仍由原目录实现回答（限流部署事实，与身份系统无关）。

## 接入（adaptor 形态：引用即装配）

引用本件并装配 IAM openapi 客户端（RestTemplate 形态，Spring Boot 2.x 宿主）即生效：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-iam-directory-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.apache.httpcomponents:httpclient:4.5.13'
}
```

装配约定：

- 本件注册目录装饰器（BeanPostProcessor）：容器内每个目录提供方 Bean（`SmartRedisLimiterDirectoryProvider`）
  初始化后被包装——USER 维度走 IAM，其余转发原实现；无开关、无让位、装饰不增量（容器内仍是一个目录 Bean）；
- `IamUserClient` 由 `simple-iam-aksk-resttemplate-client-starter` 及其 aksk 底座令牌链装配
  （AKP 凭据须授予 `iam:user:api` 与 `iam:user` read），**客户端缺失时启动即失败**（响亮失败，
  不静默降级）；
- IAM 调用失败按透传语义上抛，不以空列表伪装"无对象"；缺 subjectId 的响应条目跳过并告警。

## 依赖

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| smart-redis-limiter-core | 2.3.0 | 目录 SPI 与类型化模型（api；本件不依赖管理面实现） |
| simple-iam-aksk-resttemplate-client-starter | 1.0.1 | IAM 用户契约与 aksk 底座令牌链（api 传递） |
