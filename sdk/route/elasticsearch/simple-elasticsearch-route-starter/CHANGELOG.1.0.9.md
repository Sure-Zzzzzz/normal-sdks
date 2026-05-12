# CHANGELOG - simple-elasticsearch-route-starter 1.0.9

## 发布日期

2026-05-12

## 版本类型

**重构 + Bug 修复**

## 变更概述

1. **Bug 修复**：修复 Spring Boot 2.3.12.RELEASE 下的 `BootstrapMethodError: NoClassDefFoundError: org/elasticsearch/client/core/MainResponse` 启动报错
2. **规范归置**：对照 `sdk开发规范.md` 全面归置包名、类名、代码规范

---

## Bug 修复

### 修复 Spring Boot 2.3.12.RELEASE 兼容性问题

**问题**：
`SimpleElasticsearchRouteConfiguration` 中使用 CGLIB 创建 `ElasticsearchRestTemplate` 代理，依赖 ES Client 7.x 类链路。Spring Boot 2.3.12 使用 ES Client 6.8.x，`org.elasticsearch.client.core.MainResponse` 在 6.8.x 中不存在，JVM 加载时报 `BootstrapMethodError`。

**修复**：
在 `elasticsearchRestTemplate()` 方法前增加 `Class.forName()` 前置检测：

```java
private static final boolean ES_CLIENT_7X_AVAILABLE;

static {
    try {
        Class.forName("org.elasticsearch.client.core.MainResponse");
        available = true;
    } catch (ClassNotFoundException e) {
        available = false;
    }
    ES_CLIENT_7X_AVAILABLE = available;
}
```

检测到 6.8.x 时：单数据源降级简单 template + warn；多数据源抛明确异常。

**兼容性矩阵**：

| Spring Boot | ES Client | 单数据源 | 多数据源 |
|-------------|-----------|---------|---------|
| 2.2.5 | 6.5.x | 降级简单 template | 报错提示升级 |
| 2.3.12 | 6.8.x | 降级简单 template（1.0.9 修复） | 报错提示升级 |
| 2.4.x | 7.9+ | CGLIB 降级 | 报错提示升级 |
| 2.7.x+ | 7.17+ | CGLIB 代理，路由正常 | 路由正常 |

---

## 规范归置

### 1. 常量类合并

**变更**：`ConfigConstant` + `ElasticsearchApiConstant` → 合并为 `SimpleElasticsearchRouteConstant`

- 新增 `CONFIG_PREFIX` 配置前缀常量，消除 Properties/Configuration 中的硬编码
- 新增 `ES_CLIENT_7X_MARKER_CLASS`、`MSG_ES_CLIENT_6X_SINGLE`、`MSG_ES_CLIENT_6X_MULTI` 等常量
- 新增 `TEMPLATE_DATASOURCE_PREFIX`、`TEMPLATE_RULE_PREFIX` 模板常量，消除字符串拼接硬编码
- 删除 `ConfigConstant.java`、`ElasticsearchApiConstant.java`

### 2. 私有构造函数规范化

`ErrorCode`、`ErrorMessage` 私有构造函数统一改为：
```java
private ErrorCode() {
    throw new UnsupportedOperationException("Utility class");
}
```

### 3. 基础异常类规范

`SimpleElasticsearchRouteException` 改为使用 `@Getter` 注解，新增 `serialVersionUID`。

### 4. Properties 瘦身

- 去掉 `@SimpleElasticsearchRouteComponent` 注解（Properties 不应标自定义组件注解）
- 去掉 `@Slf4j`、`@PostConstruct`、`validate()` 及所有验证相关方法
- 验证逻辑提取到 `SimpleElasticsearchRouteValidator.java`，通过 `@ComponentScan` 自动扫描
- 配置前缀改为引用 `SimpleElasticsearchRouteConstant.CONFIG_PREFIX`

### 5. Configuration 改造

- 新增 `@EnableConfigurationProperties(SimpleElasticsearchRouteProperties.class)`
- 配置前缀改为引用 `SimpleElasticsearchRouteConstant.CONFIG_PREFIX`
- ES Client 版本检测使用 `SimpleElasticsearchRouteConstant.ES_CLIENT_7X_MARKER_CLASS`

### 6. 枚举标准方法补全

`RouteMatchType` 补全 `getAllCodes()` 和 `toString()` 方法。

### 7. 工具类重命名

`SpELResolver` → `SpELHelper`（规范要求工具类用 Helper 后缀）

### 8. 包结构优化

`RoutePatternMatcher` 从 `resolver/` 包移至 `matcher/` 包（语义更准确）

### 9. 新增 Validator 组件

`validator/SimpleElasticsearchRouteValidator.java`：从 Properties 提取的验证逻辑，标记 `@SimpleElasticsearchRouteComponent` + `@ConditionalOnProperty`，enable=true 时才执行校验。

---

## 向后兼容性

✅ **完全向后兼容**

- 所有变更均为模块内部重构，无对外 API 破坏
- 接入方通过配置文件使用，无需任何代码改动

## 升级指南

### 从 1.0.8 升级到 1.0.9

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.0.9"
```

无需修改任何代码或配置。

## 贡献者

- @surezzzzzz
