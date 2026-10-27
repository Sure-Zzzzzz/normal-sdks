# simple-feedback-iam-adaptor-starter

Feedback 提交便利性（hint，指提交页面的应用和联系信息提示）与注册表来源核对的 IAM 聚形态适配件：以纯消费方身份引入 IAM 客户端契约和 RestTemplate 认证传输件，实现 starter 预留的两个出站 SPI（可访问应用集含页面准入详情/全局目录两取数面+提交人联系提示）。

## 解决什么、谁该用

部署方希望反馈提交页的应用下拉按"本人可访问应用"交集过滤、联系邮箱按 IAM 用户资料预填时引入本模块；不引即散形态（全量下拉+空邮箱），提交功能完整。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-server-starter:1.0.1'
    implementation 'io.github.sure-zzzzzz:simple-feedback-iam-adaptor-starter:1.0.1'
    // 认证传输由宿主显式提供；也可以替换为宿主兼容的其他实现
    implementation 'io.github.sure-zzzzzz:simple-iam-aksk-resttemplate-client-starter:1.0.2'
}
```

```yaml
# IAM 客户端凭据由 resttemplate 传输件配置（AKP，服务型凭据；机密经环境注入）
```

## 行为契约

| SPI | 实现 | 出站失败 |
|---|---|---|
| `AccessibleApplicationProvider` | `IamUserClient.listPageAdmittedApplicationDetails(subjectId)`（1.3.7 页面准入详情：code/displayName/description，displayName 与门户侧边栏同源；权限码 iam:portal:api 与裸编码端点同码） | 降级返回 null（不拒提交） |
| `AccessibleApplicationProvider#catalogApplications` | `IamApplicationClient.listApplications(page,size)` 分页拉全（1.3.7 全局目录：code/applicationName/status，无描述字段；用于核对注册表数据来源） | 降级返回 null（来源未知，注册表不锁、全字段可编辑） |
| `SubmitterProfileProvider` | `IamUserClient.getUser(subjectId)` 取 email/displayName（配对键=入参侧绑定，IamUser 响应不含 subjectId） | 降级返回 null |

聚散由依赖表达：不引本模块即散形态合法终态。引入本模块但 IAM 客户端 Bean 缺席时，装配期直接失败，不静默降级；这是显式选择聚形态后缺少认证传输的配置错误。本模块对外只暴露 Feedback SPI 实现，不暴露 IAM 类型；引用本模块即装配其实现，没有额外开关或让位配置。自定义实现通过不引入本模块来表达散形态。

## AKP 最小授权

IAM 侧为宿主发服务型凭据（AKP），授 `iam:user:api`（getUser：邮箱预填+提交人展示名快照）、`iam:portal:api`（页面准入详情）与 `iam:application:api`（全局目录）三 API 码，DATA 范围 iam:user 与 iam:application（均 read all），不给 PAGE、不给超出取数所需动作。

## 兼容性

Spring Boot 2.7.9 / Java 8；本模块公开依赖 `simple-iam-client-core:1.0.3`，宿主另引 `simple-iam-aksk-resttemplate-client-starter:1.0.2`（页面准入详情随 1.0.2 客户端族引入；服务端须 IAM≥1.3.7）。

## 版本映射

| 本模块 | server-starter | iam-client-core |
|---|---|---|
| 1.0.1 | 1.0.1 | 1.0.3 |

本版本变更见 [`CHANGELOG.1.0.1.md`](CHANGELOG.1.0.1.md)。
