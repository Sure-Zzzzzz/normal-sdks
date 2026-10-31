# simple-license-audit-listener-starter

License 业务操作审计扩展模块。监听 `smart-license-server-starter` 的提交后领域事件，生成审计记录并分发给宿主处理器；覆盖授权、凭据、吊销、模型、角色协作、额度和吊销包终态。

License 的 HTTP 访问层审计由宿主经 aksk/iam resource-server 组装鉴权时的 `ResourceAccessEvent` 体系覆盖；本模块只补业务操作审计。

## 版本选型

| 版本 | Spring Boot 基线 | 说明 |
|---|---|---|
| 2.0.0 | 2.7.9 | 领域事件和审计字段使用授权、额度与吊销包模型 |

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:simple-license-audit-listener-starter:2.0.0'
```

事件源由宿主自行组装 `smart-license-server-starter`。

## 工作方式

- 至少存在一个 `LicenseAuditHandler` 实现时自动注册异步监听器；无处理器时不产生任何副作用。
- 事件在事务提交后由 License Server 发布，回滚操作不产生终态事件。吊销包幂等回读不重新发布；额度事件是操作观察，可能包含幂等重放，不能据事件条数累计实际扣减。
- 单个处理器异常被捕获并记 error 日志，不影响其余处理器与 License 主链路。
- 审计记录不含 Compact JWS、Claims、签名、KMS `keyRef`、DER/JWK 公钥材料。
- 异步监听不是持久事件队列，不承诺进程退出后的补发或一次且仅一次交付；需要可靠审计落地时，由宿主实现持久处理与幂等消费。

## 最小配置

不需要 YAML 开关。宿主提供至少一个 `LicenseAuditHandler` Bean，自动配置注册监听器；宿主已有 `LicenseAuditEventListener` 时自动配置让位。无处理器时不注册监听器。

## 全部配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          license:
            listener:
              handler:
                log:
                  enabled: false   # 默认日志处理器，仅开发测试环境开启
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `io.github.surezzzzzz.sdk.audit.license.listener.handler.log.enabled` | `false` | 注册默认日志处理器；仅开发调试时开启，生产按审计留存要求提供处理器 |

## 宿主接入

实现处理器将审计记录落地到自有设施。最小配置示例：

```java
@Component
public class BusinessLicenseAuditHandler implements LicenseAuditHandler {

    @Override
    public void handle(LicenseAuditRecord record) {
        // 按 eventId 幂等保存到宿主自有审计设施。
    }
}
```

可选实现 `LicenseAuditTraceIdProvider` 为审计记录附加链路追踪标识。

```java
@Bean
public LicenseAuditTraceIdProvider licenseAuditTraceIdProvider() {
    return () -> {
        // 从宿主可跨异步边界读取的追踪设施返回标识；无标识时返回 null。
        return null;
    };
}
```

该接口位于 `io.github.surezzzzzz.sdk.audit.license.provider` 包。Provider 缺失或读取失败时，`traceId` 为空，不影响其余字段。

## 事件类型

| eventType | 含义 |
|---|---|
| `grant-created` | 客户授权创建 |
| `grant-state-changed` | 授权管理状态变更 |
| `credential-issued` | 离线凭据签发就绪 |
| `revocation-created` | 吊销创建 |
| `model-published` | 条件模型发布 |
| `role-projection-changed` | 角色协作计划与安全状态变更 |
| `quota-operation-completed` | 额度事务提交后的操作观察；含重放，不能当作新增扣减 |
| `revocation-package-completed` | 吊销包实际提交为 `READY` 或 `FAILED`；幂等回读不重发 |

## 审计记录字段

| 字段 | 说明 |
|---|---|
| eventType | 上表中的领域事件类型 |
| eventId / eventVersion | 事件标识与事件结构版本；eventId 用于幂等消费，不是业务请求幂等键 |
| timestamp | 发生时间 epochMillis |
| principalId / requestId | 人员或调用请求上下文；后台角色协调及吊销包终态不推断人员身份 |
| grantId / customerId / productId / subjectKind / mode / modelVersionId | 授权创建及相关业务对象标识 |
| fromState / toState | 状态变化或观察到的终态 |
| credentialId / kid | 签发就绪凭据与公开签名键标识 |
| revocationId / targetKind / targetId / reasonCategory | 单项吊销标识、粒度与安全原因类别；吊销包事件不携带目标列表 |
| packageId / operationId / packageSequence | 吊销包、签名操作与不可复用序号；不借 grantId/credentialId 表示包标识 |
| modelVersion | 发布的条件模型版本 |
| projectionId / commandId / desiredRevision / executionMode | 角色协调计划与执行模式；不记录规则、部门名单或认证材料 |
| reservationId / quotaKind / reservationFence / quotaOperation | 额度预留标识、类型、持久围栏与观察类型；不记录数量或权益内容 |
| traceId | 链路追踪标识，Provider 缺省为空 |

## 最佳实践

1. 业务审计与 HTTP 访问审计分别消费。请求通过鉴权不代表签发或吊销完成，应以提交后的领域终态和业务回读为准。
2. 按 `eventId` 幂等入库；按 `packageId`、`operationId` 或 `reservationId` 关联业务。额度重放观察不计入新增扣减，吊销包 `FAILED` 不表示可以交付签名包。
3. 处理器实现线程安全，避免在异步事件内依赖请求线程上下文。需要追踪标识时通过 Provider 明确提供，缺省值允许为空。
4. 宿主持久化失败时记录并按自身恢复策略处理。单个处理器故障不会回滚 License 事务，不能将监听器用作业务一致性协调器。
5. 不把签名、凭据正文、权益明文、密钥引用或令牌追加到日志或自定义审计字段。查询审计资料也应执行宿主自己的访问控制。
