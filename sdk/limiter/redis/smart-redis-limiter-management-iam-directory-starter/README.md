# smart-redis-limiter-management-iam-directory-starter

限流管理的 IAM 目录适配件：目录提供方装饰为两层——服务清单以 IAM 可信应用注册表为权威数据源，
USER 维度对象检索改走 IAM openapi 实时检索；宿主本地声明完整保留，作为远端不可达时的完整兜底。

## 解决什么

- **服务清单与可信应用注册表保持一致**：可被限流的服务即平台已登记的可信应用。本适配件把
  `listServices`/`findService` 的清单来源接到 IAM 应用目录（`IamApplicationClient.listApplications`，
  IAM 服务端 1.3.7 起）：清单为注册表全部启用应用，显示名取注册表 `application_name`；
  未声明限流语义的应用同样出现（资源列表为空，页面不可新建），注册表停用或未登记的本地声明
  不出现并记录 WARN，避免清单与注册表漂移。
- **USER 维度对象目录**需要真实人员名单，静态配置名单会与身份事实漂移。USER 维度转发到 IAM
  用户查询（关键字过滤、页大小取 limit），目录稳定 ID 使用公开主体 `subjectId`
  （IAM 1.3.5 起 users 族响应回传），与运行端令牌主体形态对位。
- **限流语义仍由宿主声明**：资源、计数维度、命名空间与自定义类型由宿主目录声明回答
  （注册表不含接口级限流语义）。

## 聚散形态

引本件为聚：服务清单与 USER 目录的权威数据在 IAM（远端），宿主完整声明保留为兜底；
不引为散：纯宿主声明自治（无 IAM 的部署环境合法终态）。聚散边界由依赖表达，无配置开关。

远端不可达时的行为（聚形态）：

- 连通类故障（超时/连接拒绝/5xx）：回退宿主完整声明，输出与散形态完全一致；
  降级与恢复各记录一条日志；
- 授权失败（401/403）与版本错配（404，IAM 服务端低于 1.3.7 无此端点）：异常原样上抛，
  不以回退掩盖配置问题；
- USER 对象检索失败：异常原样上抛，不以空列表冒充无数据。

注册表快照在内存缓存 60 秒（过期后单线程回源，其余请求沿用旧值）；注册表低频变动，
窗口期内的改名与停用延迟生效。

## 接入（引用即装配）

引用本件并装配 IAM openapi 客户端（RestTemplate 形态，Spring Boot 2.x 宿主）即生效：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-iam-directory-starter:1.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.apache.httpcomponents:httpclient:4.5.13'
}
```

装配约定：

- 本件注册目录装饰器（BeanPostProcessor）：容器内每个目录提供方 Bean
  （`SmartRedisLimiterDirectoryProvider`）初始化后被包装为双层——外层服务清单权威化，
  内层 USER 目录适配件；无开关、无让位、装饰不增量（容器内仍是一个目录 Bean）；
- `IamUserClient` 与 `IamApplicationClient` 由 `simple-iam-aksk-resttemplate-client-starter`
  及其 aksk 底座令牌链装配，任一客户端缺失时启动即失败；
- 调用方 AKP 须授予 `iam:user:api` + `iam:user` read，以及 `iam:application:api` +
  `iam:application` read（all=true，注册表无部门归属，IN 范围不适用）；
- 缺 subjectId 的用户响应条目跳过并告警，不以登录名冒充稳定 ID。

## 依赖

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| smart-redis-limiter-core | 2.3.0 | 目录 SPI 与类型化模型（api；本件不依赖管理面实现） |
| simple-iam-aksk-resttemplate-client-starter | 1.0.2 | IAM 用户/应用契约与 aksk 底座令牌链（api 传递） |
