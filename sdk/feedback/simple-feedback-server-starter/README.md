# simple-feedback-server-starter

通用产品反馈服务的装配与 REST 面：MySQL 持久化（经 `simple-mysql-route-starter` 接管）、S3 附件（经 `s3-client-starter`）、三权协作（公共 Resource + IAM Provider）、提交幂等（首响应快照）与双档频控、注册表数据来源区分与同源锁（修正㉔：IAM 同源行展示名登记自动抄录/编辑拒绝/`syncFromIam` 重同步，受理门与处理组不受锁）、提交人展示名随行快照（聚形态经档案 SPI 取数，降级=空值回退裸 subjectId）。对外契约以 [`../contract/openapi/simple-feedback-server.openapi.yaml`](../contract/openapi/simple-feedback-server.openapi.yaml) 冻结。

## 解决什么、谁该用

部署了 IAM 统一门户的环境需要统一的"产品反馈"入口：任意已登记应用的门户用户提交反馈（打分/文本/邮箱/附件），应用管理侧按 DATA 应用范围查看与处理，提交人在"我的反馈"查看答复。纯人员通道；机器 openapi 面属首发边界外（出现真实消费方按消费范式补三件套）。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-server-starter:1.0.0'
    // 宿主义务件（本 starter 的 Spring 依赖为 compileOnly 口径）
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    // 协作三件（公共 Resource + IAM Provider；不引=散形态，需自备 FeedbackPrincipalResolver）
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'
    // 数据面 MVC 装配件：注解/模型类型已随 starter 的 api 链传递（宿主代码可直接用
    // @RequireApiPermission/@DataPermissionOperation 编译）；本行仅为运行期强制与计划注入
    implementation 'io.github.sure-zzzzzz:simple-data-permission-spring-mvc-starter:1.1.0'
    implementation 'org.springframework.security:spring-security-oauth2-resource-server'
    runtimeOnly 'mysql:mysql-connector-java:8.0.33'
}
```

```yaml
io.github.surezzzzzz.sdk:
  feedback.server:
    enable: true
    attachment:
      bucket-name: feedback-attachment
  auth:
    iam.resource.server:
      verification-endpoint: http://<iam>/iam/resource/tokens/verify
      client-id: <资源验证客户端>
      client-secret: <环境注入>
    resource.server.security:
      protected-paths: [ /api/feedback/** ]
  oss.s3:
    endpoint: http://<minio>:9000
    access-key: <环境注入>
    secret-key: <环境注入>
```

建表执行 `docs/schema.sql`（全新库）；生产升级只走 `docs/migration/`（仅追加）。

## 配置键

前缀 `io.github.surezzzzzz.sdk.feedback.server`（完整键面与默认值见 `SimpleFeedbackServerProperties`）：`enable`（总开关）、`api-base-path`（默认 `/api/feedback`）、`rate-limit`（双档短/长窗）、`attachment`（enabled 开关+bucket-name+max-count/max-size-bytes/max-total-size-bytes/allowed-content-types，全部参与校验）、`page`（default-size/max-size，超限截断生效）。

## 聚散边界

| 依赖 | 引入方式 | 在场/缺席 |
|---|---|---|
| MySQL 路由 | 随 starter 发布 | 缺=拒启（唯一事实源） |
| 公共 Resource+IAM Provider | 宿主自引 | 聚=全链验证 Bearer；散=自备 `FeedbackPrincipalResolver`，双缺席拒启 |
| 数据面 MVC 装配件 | 宿主自引 `simple-data-permission-spring-mvc-starter` | 引=DATA 注解强制与计划注入生效；不引=无数据面遮蔽（注解只编译不强制） |
| S3 附件 | 随 starter 发布 | `enabled=false` 或未部署=附件能力降级（提交不带附件可用） |
| IAM hint adaptor | 宿主可选引入 `simple-feedback-iam-adaptor-starter` | 引=hint 完整（下拉 IAM 同源名+邮箱预填+提交人展示名快照）+注册表来源核对（IAM/本表徽标+同源锁）；不引=降级（全量+空邮箱+来源未知全可编辑） |

## 宿主形态参考

可执行宿主为独立工程 `simple-feedback-server-runtime`（开发期 composite build 组合本仓源码：`gradle -p <工程> --include-build=<本仓> bootJar`；三模块发 Central 后切精确 Maven 坐标并删除 substitution 段）。测试装配与协作三权 HTTP 验收形态见 `src/test`（协作 tag 任务由本地 init 提供，不入库）。

## 兼容性

Spring Boot 2.7.9 / Java 8（server 定线，单基线，不做 Boot 版本矩阵）。

## 版本映射

| 本模块 | core | OpenAPI |
|---|---|---|
| 1.0.0 | 1.0.0 | 随仓冻结首版 |
