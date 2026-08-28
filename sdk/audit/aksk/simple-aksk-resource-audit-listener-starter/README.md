# simple-aksk-resource-audit-listener-starter

> **2.x 已封版**：2.x 文档冻结快照见 [README.2.x.md](README.2.x.md)；本文档对应 **3.0.0**。

> **1.x 封版文档**：如果你使用的是 1.x 版本，请查看 [README.1.x.md](README.1.x.md)。

监听公共资源层的 `ResourceAccessEvent` 访问事件，过滤出 AKSK 身份来源，生成审计记录并分发给业务处理器。

## 依赖

```gradle
implementation 'io.github.surezzzzz:simple-aksk-resource-audit-listener-starter:3.0.0'
```

前提：项目中已引入 `simple-resource-server-starter`（公共资源层），它在请求通过认证后统一发布 `ResourceAccessEvent` 事件（事件契约定义在 `simple-resource-server-core`）；本模块自身只依赖 core 的事件契约与 `simple-aksk-core:3.0.0` 的来源常量，只处理 AKSK 来源的事件，其他来源（如 IAM）的事件直接忽略。

---

## 快速接入

### 第一步：实现审计处理器

实现 `AkskAuditHandler` 接口，这是唯一必须实现的接口。有了它，监听器才会自动注册。

```java
@Component
public class MyAkskAuditHandler implements AkskAuditHandler {

    @Override
    public void handle(AkskAuditRecord record) {
        // 存数据库、发 MQ、写日志，随你
        log.info("AKSK audit: subjectId={}, applicationCode={}, uri={}, method={}, requestId={}",
            record.getSubjectId(), record.getApplicationCode(),
            record.getRequestUri(), record.getHttpMethod(), record.getRequestId());
    }
}
```

支持多个 Handler 同时工作，所有实现了 `AkskAuditHandler` 的 Bean 都会被调用。

### 第二步（可选）：启用默认日志 Handler

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          aksk:
            resource:
              listener:
                handler:
                  log:
                    enabled: true
```

启用后会自动打印审计日志：
```
INFO  AKSK_RESOURCE_AUDIT - AkskAuditRecord(authenticationSourceId=aksk, subjectType=SERVICE, subjectId=xxx, requestUri=/api/test, ...)
```

### 第三步（可选）：提供链路追踪 ID

```java
@Component
public class MyAkskAuditTraceIdProvider implements AkskAuditTraceIdProvider {

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

## 审计记录字段（AkskAuditRecord）

| 字段 | 类型 | 说明 |
|------|------|------|
| `authenticationSourceId` | `String` | 身份来源标识（AKSK 事件恒为 `aksk`） |
| `subjectType` | `String` | 主体类型：SERVICE / HUMAN |
| `subjectId` | `String` | 主体标识（服务身份为 Client ID） |
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
| `io.github.surezzzzzz.sdk.audit.aksk.resource.listener.handler.log.enabled` | Boolean | false | 是否启用默认日志 Handler |

---

## 工作原理

1. 公共资源层（`simple-resource-server-starter`）在请求通过认证后发布 `ResourceAccessEvent` 事件
2. `AkskAuditEventListener` 监听事件，按身份来源标识过滤——非 AKSK 来源直接忽略，同一资源服务组合 IAM 等多身份来源时互不串扰
3. AKSK 来源事件转换为 `AkskAuditRecord`
4. 使用 `@Async` 异步调用所有 `AkskAuditHandler` 实现
5. 单个 Handler 异常不影响其他 Handler 和主流程

---

## 版本历史

### 3.0.0

- 事件源切换：监听对象由 `AkskAccessEvent`（simple-aksk-resource-core，已随 2.x 封版）改为公共资源层 `ResourceAccessEvent`（契约在 `simple-resource-server-core:1.1.1`，纯模型对象经 `publishEvent(Object)` 发布），并按身份来源标识只处理 AKSK 事件，适配 3.0 公共资源服务架构。
- **破坏性变更**：`AkskAuditRecord` 字段对齐 3.0 服务身份模型——移除 `clientId` / `clientType` / `userId` / `username` / `roles` / `scope` / `source` / `context`，新增 `authenticationSourceId` / `subjectType` / `subjectId` / `applicationCode` / `requestId`；业务 `AkskAuditHandler` 实现需同步适配取值字段。
- `AkskAuditHandler`、`AkskAuditTraceIdProvider` 接口与配置前缀不变。
- 依赖切换：`simple-aksk-resource-core` / `simple-aksk-resource-server-starter`（2.x）→ `simple-resource-server-core:1.1.1` + `simple-aksk-core:3.0.0`（均 `implementation` 收紧，不耦合使用方 classpath）；测试侧按使用方组装形态引入 `simple-resource-server-starter:1.1.0` + `simple-aksk-resource-server-starter:3.0.1` 做链路 E2E。
- 移除旧 E2E 的真实 INTROSPECT 认证链路测试，改为完整链路端到端验证（公共安全链 + AKSK Provider + stub 内省，测试不再依赖外部 AKSK Server）。

详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.0

升级依赖至 resource-core 2.0.0、resource-server-starter 2.0.0；移除 security-context-starter 依赖；移除 Header 认证支持，仅支持 INTROSPECT 模式。

### 1.0.0
- 初始版本
- 支持监听 `AkskAccessEvent` 事件（Header 认证 / JWT 认证）
- 支持多 Handler 机制
- 提供默认日志 Handler（默认关闭）
- 支持自定义链路追踪 ID 提供者
- 异步处理，容错机制
