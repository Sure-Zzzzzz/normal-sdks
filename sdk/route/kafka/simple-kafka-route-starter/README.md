# Simple Kafka Route Starter

基于 Spring Boot 的 Kafka 多数据源路由组件，支持按 topic 或 route key 将消息路由到不同 Kafka 数据源。

## 核心特性

- 多数据源：支持同一路由实例同时管理多个 Kafka 集群。
- 三类显式路由入口：按 topic 路由、按 route key 路由、显式指定 datasource，互不干扰。
- 多匹配模式：支持 exact / prefix / suffix / wildcard / regex。
- 规则优先级：priority 数字越小越优先，同 priority 按配置声明顺序匹配。
- Broker 诊断层：核心 Bean 就绪后可选探测 broker 可达性、cluster 信息和 capability；探测失败默认只 warn，不阻断启动。
- 事务透传：`transaction-id-prefix` 通过 `setTransactionIdPrefix()` 设置，保留原生 Spring Kafka 事务语义。
- 安全默认：保留键（bootstrap.servers、serializer、group.id 等）禁止写入 raw properties；敏感字段（JAAS、SSL 密码等）不进 toString、日志、异常 message。
- 不污染业务上下文：不注册全局 `KafkaTemplate` / `ProducerFactory` / `ConsumerFactory` / `KafkaAdmin`。
- Spring Kafka 全版本兼容：跨版本差异集中在 helper 层，业务路径不感知版本细节。
- 可扩展：`KafkaRouteResolver`、`KafkaProducerFactoryFactory`、`KafkaConsumerFactoryFactory`、`KafkaRouteDiagnostics` 均可业务侧覆盖。
- 兼容 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9。

## 依赖配置

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.kafka:spring-kafka'
}
```

## 快速开始

### 1. 启用路由

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            enable: true
            default-source: default
```

### 2. 配置多数据源

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            enable: true
            default-source: default
            sources:
              default:
                bootstrap-servers:
                  - localhost:9092
                client-id: kafka-route-default
                producer:
                  client-id: kafka-route-default-producer
                  acks: all
                consumer:
                  client-id: kafka-route-default-consumer
                  group-id: kafka-route-default-group
                  auto-offset-reset: earliest
                  enable-auto-commit: false
              event:
                bootstrap-servers:
                  - localhost:9093
                client-id: kafka-route-event
                producer:
                  client-id: kafka-route-event-producer
                  acks: all
                consumer:
                  client-id: kafka-route-event-consumer
                  group-id: kafka-route-event-group
                  auto-offset-reset: earliest
                  enable-auto-commit: false
```

### 3. 配置路由规则

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            rules:
              - pattern: "event."
                type: prefix
                datasource: event
                priority: 1
                enable: true
              - pattern: "tenant-*"
                type: wildcard
                datasource: event
                priority: 2
                enable: true
```

## 使用示例

### 按 topic 自动路由

```java
@Service
public class DemoEventService {

    private final KafkaRouteTemplate kafkaRouteTemplate;

    public DemoEventService(KafkaRouteTemplate kafkaRouteTemplate) {
        this.kafkaRouteTemplate = kafkaRouteTemplate;
    }

    public void sendEvent(String topic, String key, String value) {
        // topic 以 "event." 开头，命中规则，自动路由到 event datasource
        kafkaRouteTemplate.send(topic, key, value);
    }

    public void sendWithRecord(ProducerRecord<String, String> record) {
        // topic / partition / timestamp / key / value / headers 原样透传
        kafkaRouteTemplate.send(record);
    }
}
```

### 按 route key 路由

```java
// routeKey 只用于选 datasource，最终发送 topic 不变
kafkaRouteTemplate.sendByRouteKey("tenant-a", "mock.order.topic", "mock-key", "mock-value");
```

### 显式指定 datasource

```java
// 绕过规则解析，直发指定 datasource
kafkaRouteTemplate.sendOn("event", "mock.topic", "mock-key", "mock-value");
```

### callback 模式

```java
// execute 返回已选 datasource 的 KafkaTemplate，callback 内发送不会二次路由
kafkaRouteTemplate.execute("event.order.created", kafkaTemplate -> {
    kafkaTemplate.send("event.order.created", "mock-key", "mock-value");
    return null;
});

// 显式 datasource callback
kafkaRouteTemplate.executeOn("event", kafkaTemplate -> {
    kafkaTemplate.send("mock.topic", "mock-key", "mock-value");
    return null;
});
```

### 获取 KafkaTemplate

```java
KafkaTemplate<Object, Object> defaultTemplate = kafkaRouteTemplate.kafkaTemplate();
KafkaTemplate<Object, Object> eventTemplate  = kafkaRouteTemplate.kafkaTemplate("event");
KafkaTemplate<Object, Object> routedTemplate = kafkaRouteTemplate.kafkaTemplateByTopic("event.order.created");
```

### 事务

```java
// tx37 datasource 配置了 transaction-id-prefix，保留原生 Spring Kafka 事务语义
kafkaRouteTemplate.executeOn("tx37", kafkaTemplate ->
    kafkaTemplate.executeInTransaction(operations -> {
        operations.send("mock.topic", "mock-key", "mock-value");
        return Boolean.TRUE;
    })
);
```

## 配置说明

### 顶层配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enable` | `false` | 是否启用 Kafka route |
| `default-source` | `default` | 默认 datasource key |
| `sources` | 空 | datasource 配置 |
| `rules` | 空 | 路由规则配置 |
| `diagnostics` | 见下 | Broker 诊断配置 |

### 数据源配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `bootstrap-servers` | 空 | Kafka broker 地址列表 |
| `client-id` | 空 | datasource 级公共 client id |
| `properties` | 空 | datasource 级公共 raw properties（不允许保留键） |
| `security.security-protocol` | 空 | 安全协议：PLAINTEXT / SSL / SASL_PLAINTEXT / SASL_SSL |
| `security.sasl-mechanism` | 空 | SASL 机制 |
| `security.sasl-jaas-config` | 空 | JAAS 配置（不进 toString） |
| `security.ssl-truststore-location` | 空 | TrustStore 路径 |
| `security.ssl-truststore-password` | 空 | TrustStore 密码（不进 toString） |
| `security.ssl-keystore-location` | 空 | KeyStore 路径 |
| `security.ssl-keystore-password` | 空 | KeyStore 密码（不进 toString） |
| `security.ssl-key-password` | 空 | Key 密码（不进 toString） |

### Producer 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `producer.client-id` | 空 | producer client id |
| `producer.key-serializer` | `StringSerializer` | key 序列化器类名 |
| `producer.value-serializer` | `StringSerializer` | value 序列化器类名 |
| `producer.acks` | 空 | acks 配置：0 / 1 / all / -1 |
| `producer.retries` | 空 | 重试次数 |
| `producer.compression-type` | 空 | 压缩类型：none / gzip / snappy / lz4 / zstd |
| `producer.transaction-id-prefix` | 空 | 事务 ID 前缀（通过 setTransactionIdPrefix 设置） |
| `producer.properties` | 空 | producer raw properties（不允许保留键） |

### Consumer 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `consumer.client-id` | 空 | consumer client id |
| `consumer.key-deserializer` | `StringDeserializer` | key 反序列化器类名 |
| `consumer.value-deserializer` | `StringDeserializer` | value 反序列化器类名 |
| `consumer.group-id` | 空 | 消费组 ID |
| `consumer.auto-offset-reset` | 空 | 消费起点：earliest / latest / none |
| `consumer.enable-auto-commit` | 空 | 是否自动提交 offset |
| `consumer.max-poll-records` | 空 | 单次 poll 最大条数 |
| `consumer.properties` | 空 | consumer raw properties（不允许保留键） |

### 路由规则配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pattern` | 空 | 匹配表达式 |
| `type` | `exact` | 匹配类型：exact / prefix / suffix / wildcard / regex |
| `datasource` | 空 | 命中的 datasource key |
| `priority` | `1000` | 优先级，数字越小越优先 |
| `enable` | `true` | 是否启用规则 |

### 诊断配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `diagnostics.enable` | `true` | 是否启用诊断能力 |
| `diagnostics.startup-check` | `true` | 是否在核心 Bean 就绪后执行启动探测 |
| `diagnostics.fail-fast` | `false` | broker 不可达时是否阻断启动 |
| `diagnostics.timeout-ms` | `3000` | 单 datasource 探测超时时间（毫秒） |
| `diagnostics.log-summary` | `true` | 探测成功时是否打印摘要日志 |

## 路由语义

- `exact`：topic/routeKey 与 pattern 完全一致。
- `prefix`：topic/routeKey 以 pattern 开头。
- `suffix`：topic/routeKey 以 pattern 结尾。
- `wildcard`：支持 `*` 和 `?` 通配符。
- `regex`：按 Java 正则表达式匹配。
- 没有命中任何启用规则时，使用 `default-source`。

## Bean 边界

模块启用后只注册 Kafka route 自身 Bean：

- `SimpleKafkaRouteRegistry`
- `KafkaRouteResolver`
- `KafkaRouteTemplate`
- `KafkaRoutePatternMatcher`
- `KafkaRouteDiagnostics`

模块不会注册或替换业务项目的全局 Kafka Bean：

- `ProducerFactory`
- `ConsumerFactory`
- `KafkaTemplate`
- `KafkaAdmin`
- `KafkaTransactionManager`
- `ConcurrentKafkaListenerContainerFactory`

如果业务项目已经使用 Spring Boot 默认 Kafka 自动配置，可以继续保留；Kafka route 通过 `KafkaRouteTemplate` 独立使用。

## 扩展点

业务侧可以通过自定义 Bean 覆盖默认实现：

| 扩展点 | 默认实现 | 说明 |
|--------|----------|------|
| `KafkaRouteResolver` | `DefaultKafkaRouteResolver` | 自定义 topic/routeKey 到 datasource 的解析逻辑 |
| `KafkaProducerFactoryFactory` | `DefaultKafkaProducerFactoryFactory` | 自定义 ProducerFactory 创建逻辑 |
| `KafkaConsumerFactoryFactory` | `DefaultKafkaConsumerFactoryFactory` | 自定义 ConsumerFactory 创建逻辑 |
| `KafkaRoutePropertiesValidator` | `DefaultKafkaRoutePropertiesValidator` | 自定义或增强配置校验 |
| `KafkaRouteDiagnostics` | `DefaultKafkaRouteDiagnostics` | 自定义 Broker 诊断实现 |

## 测试

单元测试不依赖真实 broker；端到端测试通过 Docker 启动 Kafka 1.1.0 / 2.8.1 / 3.7.1 单节点与 3 broker cluster，验证多版本 broker 路由隔离与事务边界。端到端测试通过 `-Dkafka.route.e2e.test=true` 开启，默认不跑。

## 版本兼容

| Spring Boot | Spring Kafka | Kafka Broker 矩阵 | 状态 |
|-------------|--------------|-------------------|------|
| 2.7.9 | 2.8.x | 1.1.0 / 2.8.1 / 3.7.1 + 3 broker cluster 全通过 | 已验证 |
| 2.4.5 | 2.6.x | 单元矩阵通过 | 已验证 |
| 2.3.12 | 2.5.x | 单元矩阵通过 | 已验证 |
| 2.2.x | 2.3.x | 单元矩阵通过；Spring Kafka 2.3 `getTransactionIdPrefix` 为 protected，兼容层通过反射覆盖 | 已验证 |
