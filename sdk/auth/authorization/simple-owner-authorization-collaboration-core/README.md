# simple-owner-authorization-collaboration-core

Owner Authorization Collaboration 的中性 Java 契约模块。

本模块只承载进程内 SPI 和稳定模型，供凭据运行时与身份源适配器共同依赖。它不认识任何具体身份源或消费端的实体、数据库和传输协议，也不包含 HTTP、OAuth、Spring Boot、重试、缓存和持久化实现。

## 依赖边界

```text
credential-management runtime
        -> simple-owner-authorization-collaboration-core

identity-source collaboration adapter
        -> simple-owner-authorization-collaboration-core
```

具体身份源的 wire contract 由对应适配器维护；本模块只负责把协作能力以中性 SPI 暴露给运行时组合方。

## 最小接入

业务方只需依赖本模块，并为 `OwnerAuthorizationProvider` 提供一个实现：

```java
OwnerAuthorizationReadResult result = provider.resolve(ownerKey, applicationId);
if (!result.isActive()) {
    // 拒绝依赖所属人授权投影的创建、签发或访问。
    return;
}
ApplicationAuthorizationContext authorization = result.toApplicationAuthorizationContext();
```

- `resolve` 返回某个所属人在目标应用下的最终授权投影。
- `listCandidates` 返回所属人可自助创建凭证的候选应用；来源不可用时返回空列表。
- `pullChanges` 返回有序最终态变更页；来源不可用时返回不可用页，消费方保留已有投影并按自身租约失败关闭。

SPI 不规定身份源的传输协议或认证方式。具体身份源可通过可选 starter 提供实现，业务方不引入该 starter 时，不会产生对任何身份源的运行时依赖。

## 使用规则

- 凭据运行时只依赖本模块，不依赖任何具体身份源适配器。
- 具体身份源适配器实现本模块 SPI，并由业务应用按需引入。
- 未装配 provider 时，消费端的本地凭据策略不受影响；依赖所属人授权投影的能力必须禁用或失败关闭。
- 主体值是稳定不透明字符串，调用方不得按数字 ID、username 或数据库主键解析。
- 有效读取结果必须携带完整、可验证的授权 claim；无效结果会自动清除旧纪元、归属展示与授权快照，仅可保留恢复位点。
- 载荷会递归复制为不可变 JSON 形态，调用方不能通过嵌套集合篡改已验证快照。
- `toApplicationAuthorizationContext()` 只提供进程内的显式强类型转换；它不是 JavaBean getter，不属于稳定 wire 字段。跨进程只能传递 `authorization` claim 形态。
- 模型构造或载荷校验失败统一抛出 `OwnerAuthorizationCollaborationException`，错误码为 `BIZ_001`；调用方不应依赖 JVM 通用参数异常。
