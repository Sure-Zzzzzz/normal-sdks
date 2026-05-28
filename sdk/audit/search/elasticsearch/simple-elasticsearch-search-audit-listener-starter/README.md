# simple-elasticsearch-search-audit-listener-starter

监听 ES 查询和聚合事件，生成审计记录并分发给业务处理器。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-audit-listener-starter:1.0.2'
```

前提：项目中已引入 `simple-elasticsearch-search-starter`，它负责发布 `EsQueryEvent` 和 `EsAggEvent`。

---

## 快速接入

### 第一步：实现审计处理器

实现 `EsAuditHandler` 接口，这是唯一必须实现的接口。有了它，监听器才会自动注册。

```java
@Component
public class MyEsAuditHandler implements EsAuditHandler {

    @Override
    public void handle(EsAuditRecord record) {
        // 存数据库、发 MQ、写日志，随你
        log.info("ES audit: user={}, index={}, total={}, took={}ms",
            record.getUsername(), record.getIndexAlias(),
            record.getTotal(), record.getTook());
    }
}
```

### 第二步（可选）：提供用户信息

实现 `EsAuditUserProvider`，在 ES 查询发生时提供当前用户信息。

```java
@Component
public class MyEsAuditUserProvider implements EsAuditUserProvider {

    @Override
    public String getClientId() { ... }

    @Override
    public String getClientType() { ... }

    @Override
    public String getUserId() { ... }

    @Override
    public String getUsername() { ... }
}
```

不实现此接口，审计记录中用户相关字段为 null，其他字段正常。

### 第三步（可选）：提供链路追踪 ID

```java
@Component
public class MyEsAuditTraceIdProvider implements EsAuditTraceIdProvider {

    @Override
    public String getTraceId() {
        return MDC.get("traceId");  // 或从你的链路追踪框架获取
    }
}
```

---

## EsAuditUserProvider 参考实现

`EsAuditUserProvider` 的实现取决于你的认证方式，以下是四种常见场景的参考。

### 场景一：INTROSPECT 认证

配合 `simple-aksk-resource-server-starter` 的 INTROSPECT 模式使用，从 Spring Security 的 `OAuth2AuthenticatedPrincipal` 中解析。

```java
@Component
public class IntrospectEsAuditUserProvider implements EsAuditUserProvider {

    @Override
    public String getClientId() {
        return getClaim("client_id");
    }

    @Override
    public String getClientType() {
        return getClaim("client_type");
    }

    @Override
    public String getUserId() {
        return getClaim("user_id");
    }

    @Override
    public String getUsername() {
        return getClaim("username");
    }

    private String getClaim(String claimName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticatedPrincipal)) return null;
        Object value = ((OAuth2AuthenticatedPrincipal) auth).getAttribute(claimName);
        return value != null ? value.toString() : null;
    }
}
```

### 场景二：AkskAccessEvent + ThreadLocal

同时兼容 INTROSPECT 等认证方式。监听 `AkskAccessEvent`（所有认证方式都会发布），存入 ThreadLocal，ES 查询时读取。

需要同时注册清理拦截器，防止线程池复用导致数据污染。

```java
// Provider：监听事件 + 提供用户信息
@Component
public class AkskContextEsAuditUserProvider implements EsAuditUserProvider {

    private static final ThreadLocal<AkskAccessEvent> CONTEXT = new ThreadLocal<>();

    @EventListener
    public void onAkskAccessEvent(AkskAccessEvent event) {
        CONTEXT.set(event);
    }

    @Override
    public String getClientId() {
        return CONTEXT.get() != null ? CONTEXT.get().getClientId() : null;
    }

    @Override
    public String getClientType() {
        return CONTEXT.get() != null ? CONTEXT.get().getClientType() : null;
    }

    @Override
    public String getUserId() {
        return CONTEXT.get() != null ? CONTEXT.get().getUserId() : null;
    }

    @Override
    public String getUsername() {
        return CONTEXT.get() != null ? CONTEXT.get().getUsername() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

// 拦截器：请求结束后清理 ThreadLocal，防止内存泄漏
@Configuration
public class AkskContextClearConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptorAdapter() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception ex) {
                AkskContextEsAuditUserProvider.clear();
            }
        }).addPathPatterns("/**");
    }
}
```

### 场景三：直接从请求上下文获取

不依赖 aksk 模块，直接从请求上下文（ThreadLocal、MDC 等）获取用户信息，适合自建认证体系的场景。

---

## 审计记录字段

| 字段 | 类型 | 说明 |
|------|------|------|
| clientId | String | 客户端 ID |
| clientType | String | 客户端类型 |
| userId | String | 用户 ID |
| username | String | 用户名 |
| indexAlias | String | 索引别名 |
| actualIndices | String[] | 实际查询的物理索引 |
| datasource | String | 数据源名称 |
| queryCondition | String | 查询条件 |
| total | Long | 命中总数（聚合查询为 null） |
| returnedSize | Integer | 本次返回条数 |
| took | Long | 查询耗时（毫秒） |
| timestamp | Long | 事件时间戳 |
| traceId | String | 链路追踪 ID |
| result | String | 操作结果（`success` / `failure`） |
| downgradeLevel | Integer | 降级级别，0=未降级 |
| sourceType | String | 来源端点类型（QUERY_API / NL_API / EXPRESSION_API） |
| errorMessage | String | 错误信息（仅 `result=failure` 时有值） |

---

## 配置项

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          search:
            elasticsearch:
              listener:
                executor:
                  core-size: 4          # 核心线程数，默认 4
                  max-size: 20          # 最大线程数，默认 20
                  queue-capacity: 2000  # 队列容量，默认 2000
                  keep-alive-seconds: 60  # 空闲线程存活时间（秒），默认 60
                  reject-policy: CALLER_RUNS  # 拒绝策略，默认 CALLER_RUNS
                handler:
                  log:
                    enabled: false  # 是否启用内置日志处理器，默认 false
```

### 拒绝策略说明

| 策略 | 说明 |
|------|------|
| CALLER_RUNS | 由调用线程直接执行（默认），保证审计不丢失 |
| DISCARD | 直接丢弃 |
| DISCARD_OLDEST | 丢弃队列中最旧的任务 |
| ABORT | 抛出 RejectedExecutionException |

### 自定义线程池

如需完全自定义线程池，声明名为 `esAuditExecutor` 的 bean 即可覆盖默认实现：

```java
@Bean("esAuditExecutor")
public Executor myEsAuditExecutor() {
    // 自定义线程池
}
```

---

## 注意事项

- 用户信息在 ES 查询发生时**同步**获取，`EsAuditUserProvider` 的实现需要能在请求线程中访问到认证上下文
- 审计处理器的调用通过内置线程池**异步**执行，不阻塞主流程
- 多个 `EsAuditHandler` 实现会全部被调用，任意一个抛出异常不影响其他处理器
- 内置日志处理器仅用于开发调试，生产环境建议实现自己的 `EsAuditHandler`
