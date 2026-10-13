# simple-feedback-server-core

通用产品反馈服务的领域层（纯 Java 8，零 Spring/HTTP/存储）：反馈提交与应用注册表两聚合的模型、字段校验、三态状态机、七组错误码、出站 SPI 契约（可访问应用集+全局目录/提交人联系提示）与 owner 派生定式（`iam:{subjectId}`）。

## 快速上手

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-server-core:1.0.0'
}
```

## 核心能力

| 能力 | 入口 |
|---|---|
| 提交校验（打分 1-10/文本 1-2000/邮箱/环境快照键白名单/枚举） | `FeedbackSubmission.validate()` / `validateEnvironmentKeys(...)` |
| 处理状态机（submitted→resolved/dismissed、终态重开；覆盖式留痕） | `FeedbackStatus.canTransitionTo(...)` / `FeedbackSubmission.applyHandling(...)` |
| 注册表校验（编码正则/展示名/描述） | `FeedbackApplication.validate()` / `validateCode(...)` |
| 附件规格校验（数量/单件/总量/类型白名单，默认值可被 starter 配置覆盖） | `FeedbackAttachment.validateSpecs(...)` |
| owner 定式 | `OwnerHelper.buildIamOwner(...)` |
| 出站 SPI（聚散边界） | `spi.AccessibleApplicationProvider` / `spi.SubmitterProfileProvider` |

## SPI 聚散语义

两个 SPI 是提交便利性（hint）与注册表来源核对的出站取数点：实现返回 null=该取数点降级（交集不过滤/邮箱空预填/来源未知全可编辑）；出站失败实现方自行捕获降级，不拒提交。`AccessibleApplicationProvider` 含两个取数面——`accessibleApplications(subjectId)`（页面准入详情，下拉命名 IAM 同源=修正㉓）与 `catalogApplications()`（全局目录，注册表「IAM 同源」徽标+展示名锁定与抄录=修正㉔，default null 散形态不支持）。starter 内置降级实现，`simple-feedback-iam-adaptor-starter` 提供 IAM 聚形态实现（client-core≥1.0.3）。

## 兼容性

Java 8 字节码；无 Spring 依赖，javax/jakarta 宿主均可引用。

## 版本映射

| 本模块 | starter | 说明 |
|---|---|---|
| 1.0.0 | 1.0.0 | 首发 |
