# simple-elasticsearch-persistence-audit-listener-starter

`simple-elasticsearch-persistence-audit-listener-starter` 是 `simple-elasticsearch-persistence-core` 的写侧审计监听器，监听 persistence 写入事件并转换为统一审计记录，再异步分发给调用方自定义的 `EsPersistenceAuditHandler`。

## 版本

| 模块 | 版本 |
|---|---|
| simple-elasticsearch-persistence-audit-listener-starter | 1.0.0 |
| simple-elasticsearch-persistence-core | 1.0.2 |
| 推荐配套 simple-elasticsearch-persistence-starter | 1.1.0 |

## 能力边界

- 监听 `EsPersistenceEvent` / `EsPersistenceErrorEvent`
- 支持单条 index / create / update / delete 审计
- 支持 bulk 总数、成功数、失败数、批次结果和失败明细审计
- 支持 updateByQuery / deleteByQuery 完成结果与服务端异步 task 提交审计
- 支持 `EsPersistenceAuditUserProvider` 和 `EsPersistenceAuditTraceIdProvider` 补充调用方上下文
- 默认日志 handler 关闭，调用方可自定义 handler 落库、发消息或写日志

不记录文档内容、字段值、脚本内容、脚本参数和 byQuery 查询条件。

## 快速接入

### 1. 引入依赖

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-persistence-audit-listener-starter:1.0.0"
```

### 2. 开启配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          persistence:
            elasticsearch:
              listener:
                enable: true
                executor:
                  core-size: 4
                  max-size: 20
                  queue-capacity: 2000
                  keep-alive-seconds: 60
                  reject-policy: CALLER_RUNS
                record:
                  max-failure-size: 20
```

### 3. 实现审计处理器

```java
@Component
public class CustomEsPersistenceAuditHandler implements EsPersistenceAuditHandler {

    @Override
    public void handle(EsPersistenceAuditRecord record) {
        // 将审计记录写入调用方自己的存储或日志系统
    }
}
```

只有存在 `EsPersistenceAuditHandler` Bean 时，监听器才会注册。

### 4. 可选用户和 TraceId Provider

```java
@Component
public class CustomEsPersistenceAuditUserProvider implements EsPersistenceAuditUserProvider {

    @Override
    public String getClientId() {
        return "client-id";
    }

    @Override
    public String getClientType() {
        return "application";
    }

    @Override
    public String getUserId() {
        return "user-id";
    }

    @Override
    public String getUsername() {
        return "username";
    }
}
```

```java
@Component
public class CustomEsPersistenceAuditTraceIdProvider implements EsPersistenceAuditTraceIdProvider {

    @Override
    public String getTraceId() {
        return "trace-id";
    }
}
```

Provider 会在事件线程同步读取，之后再异步分发给 handler，适合从调用上下文读取用户和链路信息。

## 默认日志 Handler

默认日志 handler 不启用。如需临时输出审计记录：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          persistence:
            elasticsearch:
              listener:
                handler:
                  log:
                    enabled: true
```

日志格式：

```text
ES_PERSISTENCE_AUDIT - <EsPersistenceAuditRecord>
```

## 审计结果说明

| result | 说明 |
|---|---|
| success | 操作成功 |
| failure | 操作失败 |
| partial_failure | bulk / byQuery 存在部分失败或冲突 |
| task_submitted | byQuery 服务端异步任务已提交 |

## 失败明细

`failureList` 最多保留 `record.max-failure-size` 条，默认 20 条。

- bulk 失败明细保留 item 下标、操作类型、索引、文档 ID、HTTP 状态码、SDK 错误码、ES 错误类型、ES 错误原因、是否建议重试、是否冲突。
- byQuery 失败明细保留索引、文档 ID、状态文本、失败原因和是否冲突。

## 线程池拒绝策略

| 配置值 | 策略 |
|---|---|
| CALLER_RUNS | 调用线程执行，默认值 |
| DISCARD | 丢弃新任务 |
| DISCARD_OLDEST | 丢弃队列最旧任务 |
| ABORT | 抛出拒绝异常 |

拒绝异常和 handler 异常都会被监听器捕获并记录日志，不会反向影响 persistence 写入主链路。
