# simple-iam-resource-audit-listener-starter

监听公共资源层的 `ResourceAccessEvent` 访问事件，过滤出 IAM 身份来源，生成审计记录并分发给业务处理器。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-iam-resource-audit-listener-starter:1.0.0'
```

前提：项目中已引入 `simple-resource-server-starter`（公共资源层）与 `simple-iam-resource-server-starter`（IAM Provider）。公共层在请求通过认证后统一发布 `ResourceAccessEvent` 事件（事件契约定义在 `simple-resource-server-core`）；本模块自身只依赖 core 的事件契约与 `simple-iam-core:1.0.0` 的来源常量，只处理 IAM 来源的事件，其他来源（如 AKSK）的事件直接忽略。

与 `simple-iam-server-audit-listener-starter`（记录 IAM Server 管理面与令牌生命周期事件）相互独立：本模块记录的是资源服务侧的已认证访问。

---

## 快速接入

### 第一步：实现审计处理器

实现 `IamResourceAuditHandler` 接口，这是唯一必须实现的接口。有了它，监听器才会自动注册。

```java
@Component
public class MyIamResourceAuditHandler implements IamResourceAuditHandler {

    @Override
    public void handle(IamResourceAuditRecord record) {
        // 存数据库、发 MQ、写日志，随你
        log.info("IAM access audit: subjectId={}, applicationCode={}, uri={}, method={}, requestId={}",
            record.getSubjectId(), record.getApplicationCode(),
            record.getRequestUri(), record.getHttpMethod(), record.getRequestId());
    }
}
```

支持多个 Handler 同时工作，所有实现了 `IamResourceAuditHandler` 的 Bean 都会被调用。

### 第二步（可选）：启用默认日志 Handler

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          iam:
            resource:
              listener:
                handler:
                  log:
                    enabled: true
```

启用后会自动打印审计日志：
```
INFO  IAM_RESOURCE_AUDIT sourceId=iam, subjectType=HUMAN, subjectId=xxx, applicationCode=xxx, requestId=xxx, uri=/api/test, method=GET, remoteAddr=1.2.3.4, userAgent=xxx, timestamp=1699999999999, traceId=xxx
```

### 第三步（可选）：提供链路追踪 ID

```java
@Component
public class MyIamResourceAuditTraceIdProvider implements IamResourceAuditTraceIdProvider {

    @Override
    public String getTraceId() {
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader("X-Trace-Id");
    }
}
```

不实现此接口，审计记录中 `traceId` 字段为 null，其他字段正常。

---

## 审计记录字段（IamResourceAuditRecord）

| 字段 | 类型 | 说明 |
|------|------|------|
| `authenticationSourceId` | `String` | 身份来源标识（IAM 事件恒为 `iam`） |
| `subjectType` | `String` | 主体类型：HUMAN / SERVICE |
| `subjectId` | `String` | 主体标识（IAM 人员身份为用户 ID） |
| `applicationCode` | `String` | 应用编码 |
| `requestId` | `String` | 请求 ID |
| `requestUri` | `String` | 请求 URI |
| `httpMethod` | `String` | HTTP 方法 |
| `remoteAddr` | `String` | 来源 IP |
| `userAgent` | `String` | User-Agent |
| `timestamp` | `Long` | 时间戳 |
| `traceId` | `String` | 链路追踪 ID |

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.audit.iam.resource.listener.handler.log.enabled` | Boolean | false | 是否启用默认日志 Handler |

---

## 工作原理

1. 公共资源层（`simple-resource-server-starter`）在请求通过认证后发布 `ResourceAccessEvent` 事件
2. `IamResourceAuditEventListener` 监听事件，按身份来源标识过滤——非 IAM 来源直接忽略，同一资源服务组合 AKSK 等多身份来源时互不串扰
3. IAM 来源事件转换为 `IamResourceAuditRecord`
4. 使用 `@Async` 异步调用所有 `IamResourceAuditHandler` 实现
5. 单个 Handler 异常不影响其他 Handler 和主流程

---

## Spring Boot 兼容性

基于 Spring Boot 2.7.9 / javax 基线开发，已验证 `2.2.13.RELEASE` / `2.3.12.RELEASE` / `2.4.5` / `2.7.9` 四档矩阵；源码兼容 Java 8。不支持 Spring Boot 3 与 `jakarta.servlet`。
