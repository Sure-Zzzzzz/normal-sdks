# CHANGELOG - simple-elasticsearch-route-starter 1.0.10

## 发布日期

2026-05-12

## 版本类型

Bug 修复

## 变更概述

1. **Bug 修复**：修复 `SimpleElasticsearchRouteRegistry.createDataSourceClients()` 中的 `BootstrapMethodError: NoClassDefFoundError: org/elasticsearch/client/core/MainResponse` 启动报错
2. **新增异常码**：`ROUTE_004` + `ROUTE_TEMPLATE_UNAVAILABLE`，用于提示 ES Client 6.8.x 场景下 `ElasticsearchRestTemplate` 不可用

---

## Bug 修复

### 修复 Registry 层 `new ElasticsearchRestTemplate(client)` 触发 BootstrapMethodError

**问题**：

1.0.9 修复了 `SimpleElasticsearchRouteConfiguration.elasticsearchRestTemplate()` 中的 `RouteTemplateProxy.createProxy()` 调用，但遗漏了 `SimpleElasticsearchRouteRegistry.createDataSourceClients()` 中的 `new ElasticsearchRestTemplate(client)` 构造调用。Spring Boot 2.3.x 的 Spring Data Elasticsearch 4.0.x，构造函数依赖 `org.elasticsearch.client.core.MainResponse`（ES Client 7.9+ 才有的类），`new` 操作数入栈时就抛出 `BootstrapMethodError: NoClassDefFoundError`。

**修复 1**：`createDataSourceClients()` 中 try-catch 兜底

```java
ElasticsearchRestTemplate template;
try {
    template = new ElasticsearchRestTemplate(client);
} catch (BootstrapMethodError | ExceptionInInitializerError e) {
    log.warn("Failed to create ElasticsearchRestTemplate for datasource [{}] (ES Client 6.8.x?): {}. " +
            "Use getHighLevelClient() instead.", key, e.getMessage());
    template = null;
}
```

**修复 2**：`getTemplate()` 判空抛明确异常

```java
if (t == null) {
    throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, ...);
}
```

**修复 3**：`createAllClients()` 只把非空 template 放入 templatesView

**修复 4**：新增 `ROUTE_004` 错误码

---

## 兼容性矩阵

| Spring Boot | ES Client | RestHighLevelClient | ElasticsearchRestTemplate | 多数据源路由 |
|-------------|-----------|---------------------|--------------------------|-------------|
| 2.2.5 | 6.5.x | ✅ 正常 | ❌ BootstrapMethodError | ❌ 启动失败 |
| 2.3.12 | 6.8.x | ✅ 正常 | ❌ BootstrapMethodError | ❌ 启动失败 |
| 2.4.x | 7.9+ | ✅ 正常 | ✅ CGLIB 降级 | ❌ 启动失败 |
| 2.7.x+ | 7.17+ | ✅ 正常 | ✅ CGLIB 代理 | ✅ 正常 |

---

## 向后兼容性

✅ **完全向后兼容**

- 所有变更均为内部容错增强，无对外 API 破坏
- `ElasticsearchRestTemplate` 不可用时，现有业务代码如调用了 `getTemplate()` 会收到 `RouteException(ROUTE_004)` 而非启动崩溃
- 接入方通过配置文件使用，无需任何代码改动

---

## 升级指南

### 从 1.0.9 升级到 1.0.10

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.0.10"
```

**重要提示**：Spring Boot 2.2.x / 2.3.x + ES Client 6.8.x 场景下，`ElasticsearchRestTemplate` 不可用。推荐使用 `registry.getHighLevelClient(datasourceKey)` 替代。

---

## 贡献者

- @surezzzzzz