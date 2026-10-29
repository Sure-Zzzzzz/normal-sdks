# simple-crm-audit-listener-starter

CRM 业务操作审计扩展模块。监听 `simple-crm-server-starter` 写事务提交后发布的稳定审计事件（`CrmAuditEvent`），生成业务审计记录并分发给宿主处理器。事件契约由 `simple-crm-server-core` 提供，listener 不依赖 server 装配件。

CRM 的 HTTP 访问层审计由宿主经 aksk/iam resource-server 组装鉴权时的 `ResourceAccessEvent` 体系覆盖；本模块只补业务操作审计。事件由 CRM server 在事务提交后发布，本地 `crm_audit` 事实表仍是审计查询端点的权威数据源，本模块不改变落库行为。

## 兼容基线

| 项目 | 基线 |
|---|---|
| 发布版本 | 1.0.0 |
| Spring Boot | 2.7.9 |
| Java | 8 |

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:simple-crm-audit-listener-starter:1.0.0'
```

事件源由宿主自行组装 `simple-crm-server-starter`。

## 工作方式与边界

- 至少存在一个 `CrmAuditHandler` 实现时自动注册异步监听器；无处理器时不产生任何副作用。
- 事件在写事务提交后由 CRM server 发布，回滚事务不产生事件；Server publisher 负责写事务提交后发布和写路径幂等重放不重复发布。
- 本模块只消费已发布事件，不自行持久化、不建立消息队列、不保证进程重启后的补发，也不对 `eventId` 去重。宿主 `CrmAuditHandler` 必须按 `eventId` 实现幂等落地或去重。
- 监听器先在事件线程采集可选 `CrmAuditTraceIdProvider`，再将不可变审计记录交给进程内异步分发；异步 Handler 不应依赖分发线程中的请求上下文，缺失或读取失败时 `traceId` 为空。
- 单个处理器异常被捕获并记 error 日志，不影响其余处理器与 CRM 主链路；链路追踪提供者异常降级为空，不丢失审计记录。
- 审计记录不含命令参数与业务快照，仅含归属坐标、动作、目标与结果事实。
- 首发仅提供默认日志处理器；持久化类处理器由宿主按 `CrmAuditHandler` 扩展接入。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          crm:
            listener:
              handler:
                log:
                  enabled: false   # 默认日志处理器，仅开发测试环境开启
```

## 宿主接入

实现处理器将审计记录落地到自有设施：

```java
@Component
public class ElasticsearchCrmAuditHandler implements CrmAuditHandler {

    @Override
    public void handle(CrmAuditRecord record) {
        // 转发 Elasticsearch / Kafka / MySQL 等
    }
}
```

可选实现 `CrmAuditTraceIdProvider` 为审计记录附加链路追踪标识；Provider 在事件线程调用，宿主应从可安全跨异步边界读取的追踪设施提供值。

## 审计记录字段

| 字段 | 类型 | 说明 |
|---|---|---|
| eventType | String | 审计类型（与审计行动作码一致，如 customer_create） |
| eventId | String | 事件唯一标识 |
| eventVersion | Integer | 事件版本 |
| timestamp | Long | 发生时间（毫秒纪元） |
| owner | String | 归属主体（iam:/aksk: 前缀坐标） |
| resourceTypeCode | String | 目标资源类型码 |
| resourceId | String | 目标资源标识 |
| aggregateVersion | Long | 目标聚合版本 |
| correlationId | String | 命令关联标识 |
| resultCode | String | 结果码（成功审计为 null） |
| traceId | String | 链路追踪ID（提供者缺省时为 null） |
