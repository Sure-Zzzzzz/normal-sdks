# CHANGELOG - simple-iam-resource-audit-listener-jakarta-starter 1.0.1

## 版本信息

- 版本：`1.0.1`
- 基线版本：`1.0.0`
- 类型：Patch / Bug Fix，修复审计监听器条件装配时序。

## 主要变更

### 修复宿主 @Bean Handler 的装配时序

`1.0.0` 将 `@ConditionalOnBean(IamResourceAuditHandler.class)` 放在被组件扫描的监听器类上。
宿主通过配置类的 `@Bean` 方法注册审计处理器时，扫描阶段可能早于该 Bean 定义的注册，
导致条件判断不通过。后续 Handler 注册不会重新触发判断，应用虽然能启动且审计已开启，
容器却缺少 `IamResourceAuditEventListener`，IAM 访问事件无法通过官方监听器交给 Handler。

本版本将监听器改为由自动配置的 `@Bean` 方法注册，并将 Handler 条件移到该方法上，
避免扫描阶段提前判断。业务 Handler 继续支持 `@Component` 和 `@Bean` 两种注册方式，
无需改变实现或注册方式。

### 宿主监听器让位

自动配置增加 `@ConditionalOnMissingBean(IamResourceAuditEventListener.class)`：宿主已经提供
同类型监听器时，不再注册第二个官方监听器。判断按类型执行，不要求宿主使用固定 Bean 名称。

## 兼容性与依赖

- 既有公开类型、构造方法签名、Handler 回调方法、审计记录字段和配置前缀不变。
- 总开关 `io.github.surezzzzzz.sdk.audit.iam.resource.enable` 仍默认 `true`；设为 `false`
  时不装配模块自带监听器和日志 Handler，宿主自有 Handler 不受影响。
- 默认 `LogIamResourceAuditHandler` 仍自动注册，与业务 Handler 并存；新增业务 Handler
  不代表替换或关闭默认日志输出。
- 仍只处理 IAM 来源的已认证访问，异步分发至处理器；单个 Handler 异常不阻断其他处理器。
  TraceId 提供者仍为可选，缺失或执行异常时记录中的 TraceId 为 `null`。
- 生产依赖不变：`simple-resource-server-core:1.1.1`、`simple-iam-core:1.0.0`；
  不新增依赖，不要求配套资源认证组件升级，无数据库或历史审计数据迁移。
- 本版本不新增审计重试、可靠投递或持久化能力，不扩大访问事件的采集范围。

## 升级指南

1. 将本模块依赖升级至 `simple-iam-resource-audit-listener-jakarta-starter:1.0.1`。
2. 保留既有总开关、业务 `IamResourceAuditHandler` 和可选 `IamResourceAuditTraceIdProvider`。
   沿用 Starter 自动配置即可，不需要手动补注册官方监听器；已有宿主自定义监听器可继续保留。
3. 启用审计后核验业务 Handler 与唯一的 `IamResourceAuditEventListener` 同时存在，
   再通过 Spring 事件发布或已认证资源访问确认业务 Handler 实际收到记录。
   直接调用 Handler 不能证明官方事件消费链已经建立。

## 验证

新增 8 项装配与事件回归，覆盖默认日志 Handler、宿主 `@Bean` Handler、默认与显式启用、
显式关闭、TraceId 缺失与异常、其他认证来源过滤、多个 Handler 异常隔离和自定义监听器让位。
事件消费使用真实 Spring 事件发布与后台执行器，断言异步消费线程及审计记录内容；来源过滤
通过单线程按序消费的后续 IAM 事件确认此前事件已经处理，避免以即时空结果误判通过。

保留原有 2 项 MockMvc 公共安全链与 IAM Provider 受控验证用例，以及 1 项关闭开关用例。
Spring Boot `3.4.2`、Gradle `8.5`、Java `17.0.20.1` 下完整测试共 11 项，
无失败、错误或跳过。上述验证不连接真实 IAM 服务，不替代宿主部署后的身份验证与审计存储验收。
