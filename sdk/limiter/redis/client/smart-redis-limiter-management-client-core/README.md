# smart-redis-limiter-management-client-core

限流策略客户端契约层：为消费 Management 策略快照的运行端（限流 SDK 或第三方系统）提供纯契约与容错语义。本模块是「谁提供客户端」范式的契约件：**服务方（limiter 线）提供并发布，消费方只引不改传输**。

## 解决什么、谁该接

- 你在 Java 8+ 上需要以编程接口消费 smart-redis-limiter-management-starter 暴露的 v1（三元组）或 v2（类型化）策略快照。
- 传输与认证不在本模块：HTTP 装配与 AKSK 认证由传输 starter 提供（见 `smart-redis-limiter-management-aksk-resttemplate-client-starter`），或你自行实现 `SmartRedisLimiterManagementClient`。
- 引本模块即获得：双协议拉取接口、拉取结果模型、严格 JSON codec（拒绝未知字段；协议版本判别由快照模型构造器承担，v1 内容解析不出 v2、反之亦然）。

## 依赖坐标

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-client-core:1.0.0'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:2.2.0'
// jackson 由你的运行环境提供（本模块 POM 零传递）
implementation 'com.fasterxml.jackson.core:jackson-databind'
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```

## 最小使用

```java
SmartRedisLimiterManagementClient client = ...; // 传输 starter 装配或自行实现
SmartRedisLimiterPolicyFetchResult result = client.fetchPolicy("order-service", currentEtag);
if (result.isNotModified()) {
    // 保留本地 last-known-good
} else {
    // result.getSnapshot() 已通过构造校验：字段非法/键重复/服务不匹配在解析时整份拒绝
}
```

## 语义边界

- 拉取结果是单次快照：last-known-good 保留、条件请求与刷新节奏由消费方（限流运行端的刷新管理器）负责。
- codec 使用 SDK 自有 ObjectMapper，禁止注入宿主 Bean；严格拒绝未知字段。
- v1 与 v2 的已接受快照状态相互独立；本模块不做协议选择。
- 本模块无 Spring 依赖、无 HTTP 客户端依赖、无凭据概念。

## 聚散形态

引传输 starter = 聚（消费远端策略）；只引本模块或一件不引 = 散（本地限额自治）。聚散由依赖表达，配置开关不构成假散。
