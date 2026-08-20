# Simple AKSK Resource Core

`simple-aksk-resource-core` 是 AKSK Resource 3.0 的纯 Java 协议模块。它只导出 AKSK 内省响应的 `active` 字段常量，供 AKSK Resource Provider 判断令牌是否仍有效；不创建或参与 Web、安全过滤器、业务授权和数据查询。

## 业务服务如何接入

业务服务不应单独引入本模块。接入 AKSK 服务身份时，使用公共 Resource Server 加 AKSK Provider：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.0.1'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.0.0'
}
```

同一服务还需要 IAM 人员身份时，额外接入 IAM Resource Provider。IAM 和 AKSK 进入同一公共安全链，Controller 只维护一套应用准入、精确 API permission 和 DATA 授权规则。

## 安全边界

公共 `simple-resource-server-starter` 负责：

- 唯一 Bearer 认证入口和 Provider 选择；
- 认证失败的统一 `401`；
- 应用准入和精确 API permission；
- `DataGrantDocument` 到真实 `DataAccessPlan` 的执行边界；
- 已认证访问的安全摘要事件。

AKSK Provider 负责已认证 introspection、`active`、服务主体和应用授权快照校验。角色、OAuth scope、URL、HTTP method、Controller 名称、请求参数和 `security_context` 都不能授予或扩大 API/DATA 权限。

## 3.0 不包含的能力

3.0 不提供 2.x 的 Header/request-context/AOP 安全链，也不提供兼容注解、SpEL、静态 Servlet helper、context Map 或 AKSK 专属访问事件。仍使用该旧链的服务必须继续保持在已发布的 2.x 依赖，不属于 3.0 升级路径。

2.x 封版说明仍保留在 [README.2.x.md](README.2.x.md)，1.x 封版说明仍保留在 [README.1.x.md](README.1.x.md)；历史 CHANGELOG 也随模块保留，仅供对应已发布版本核对。

访问审计和指标请监听公共 `ResourceAccessEvent`，并按认证来源 `aksk` 过滤。事件不包含 Token、认证头、Secret、Cookie、完整 introspection 响应、完整 claims 或未验证上下文。

## 相关模块

- [AKSK Resource Server Starter](../simple-aksk-resource-server-starter/README.md)
- [公共 Resource Server Starter](../../../resource/simple-resource-server-starter/README.md)
- [AKSK Server 3.0](../../server/simple-aksk-server-starter/README.md)
- [IAM 与 AKSK 协作接入](../../../README.IAM-AKSK协作.md)

## 许可证

Apache License 2.0
