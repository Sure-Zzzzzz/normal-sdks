# Changelog - v1.0.7

## 发布日期

2026-04-12

## 版本类型

**Patch Release** - Bug 修复，向后兼容

## 变更概述

修复 Spring Boot 2.4.x 下 CGLIB 兼容性问题，route-starter 自闭环处理降级逻辑；同时修复 `IndexQueryExtractor` 在 Spring Data Elasticsearch 4.1.x 下的兼容性问题。

## Bug 修复

### 1. CGLIB 降级逻辑自闭环

**问题：**
`SimpleElasticsearchRouteConfiguration` 在 `RouteTemplateProxy.createProxy` 失败时直接抛出异常，导致在 Spring Boot 2.4.x 下应用启动失败。

**根本原因：**
Spring Boot 2.4.x 下 CGLIB 无法访问 `AbstractElasticsearchTemplate` 的 protected 成员：
```
org.springframework.cglib.core.CodeGenerationException: java.lang.IllegalAccessException
cannot access a member of class AbstractElasticsearchTemplate with modifiers "protected"
```

**修复：**
在 `SimpleElasticsearchRouteConfiguration.elasticsearchRestTemplate` 中捕获 `createProxy` 的异常，按数据源数量智能降级：

| 场景 | 行为 |
|------|------|
| 单数据源 + CGLIB 失败 | 降级到简单 `ElasticsearchRestTemplate`，打 warn 日志，功能正常 |
| 多数据源 + CGLIB 失败 | 抛出 `BeanCreationException`，明确提示升级 Spring Boot |
| Spring Boot 2.7.x+ | 行为不变，proxy 创建成功 |

### 2. IndexQueryExtractor 兼容 Spring Data Elasticsearch 4.1.x

**问题：**
`IndexQueryExtractor` 直接调用 `IndexQuery.getIndexName()`，该方法在 Spring Data Elasticsearch 4.1.x（对应 Spring Boot 2.4.x）中不存在，导致编译失败。

**修复：**
改用反射调用 `getIndexName()`，4.1.x 下方法不存在时返回 `null`，由责任链中的其他提取器继续处理，行为与旧版本一致。

## 向后兼容性

✅ **完全向后兼容**

- Spring Boot 2.7.x+ 用户：无感知，行为不变
- Spring Boot 2.4.x 单数据源用户：启动不再失败，自动降级
- Spring Boot 2.4.x 多数据源用户：启动失败并报错（路由失效会导致数据写入错误集群，必须升级）

## 新增测试

新增 `RouteConfigurationCglibFallbackTest`，覆盖降级逻辑的两个分支：
- 单数据源 CGLIB 失败时降级到简单 template，不抛异常
- 多数据源 CGLIB 失败时抛出 `BeanCreationException`

## 升级指南

### 从 1.0.6 升级到 1.0.7

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-route-starter:1.0.7"
```

无需修改任何代码或配置。

## 贡献者

- @surezzzzzz
