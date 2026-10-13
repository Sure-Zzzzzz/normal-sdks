# simple-feedback-iam-adaptor-starter

Feedback 提交便利性（hint）与注册表来源核对的 IAM 聚形态适配件：以纯消费方身份引入 IAM 现成客户端（`simple-iam-client-core`≥1.0.3+resttemplate 传输件，limiter/ticket 适配件同款），实现 starter 预留的两个出站 SPI（可访问应用集含页面准入详情/全局目录两取数面+提交人联系提示）。

## 解决什么、谁该用

部署方希望反馈提交页的应用下拉按"本人可访问应用"交集过滤、联系邮箱按 IAM 用户资料预填时引入本模块；不引即散形态（全量下拉+空邮箱），提交功能完整。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-feedback-iam-adaptor-starter:1.0.0'
}
```

```yaml
# IAM 客户端凭据由 resttemplate 传输件配置（AKP，服务型凭据；机密经环境注入）
```

## 行为契约

| SPI | 实现 | 出站失败 |
|---|---|---|
| `AccessibleApplicationProvider` | `IamUserClient.listPageAdmittedApplicationDetails(subjectId)`（1.3.7 页面准入详情：code/displayName/description，displayName 与门户侧边栏同源；权限码 iam:portal:api 与裸编码端点同码） | 降级返回 null（不拒提交） |
| `AccessibleApplicationProvider#catalogApplications` | `IamApplicationClient.listApplications(page,size)` 分页拉全（1.3.7 全局目录：code/applicationName/status，无描述字段；注册表数据来源核对=修正㉔） | 降级返回 null（来源未知，注册表不锁、全字段可编辑） |
| `SubmitterProfileProvider` | `IamUserClient.getUser(subjectId)` 取 email/displayName（配对键=入参侧绑定，IamUser 响应不含 subjectId） | 降级返回 null |

聚散由依赖表达：不引本模块即散形态合法终态。引入本模块但 IAM 客户端 Bean 缺席=装配期失败（fail-fast，不做静默降级——adaptor 是有意引入的聚形态，客户端缺席属装配错误）。本模块对外只暴露 SPI 实现，不暴露 IAM 类型；**引用即装配（adaptor 纪律 §3.7）：无开关、无让位**——自定义注解+ComponentScan 三件套加载扫描件，不引本模块即散形态，宿主自定义实现经依赖替换表达。

## AKP 最小授权

IAM 侧为宿主发服务型凭据（AKP），授 `iam:user:api`（getUser：邮箱预填+提交人展示名快照）、`iam:portal:api`（页面准入详情）与 `iam:application:api`（全局目录，修正㉔）三 API 码，DATA 范围 iam:user 与 iam:application（均 read all），不给 PAGE、不给超出取数所需动作。

## 兼容性

Spring Boot 2.7.9 / Java 8；`simple-iam-client-core:1.0.3`+`simple-iam-aksk-resttemplate-client-starter:1.0.2`（页面准入详情随 1.0.2 客户端族引入；服务端须 IAM≥1.3.7）。

## 版本映射

| 本模块 | server-starter | iam-client-core |
|---|---|---|
| 1.0.0 | 1.0.0 | 1.0.3 |
