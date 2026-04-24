# Changelog - v1.0.8

## 发布日期

2026-04-24

## 版本类型

**Patch Release** - 兼容性增强，向后兼容

## 变更概述

修复 `IndexCoordinatesExtractor` 对 Spring Data Elasticsearch 4.0+ 专属类的硬引用，使 route-starter 可在 Spring Boot 2.2.5 下正常加载，实现 2.2.5 / 2.4.5 / 2.7.9 三版本运行时自适应。

## Bug 修复

### 1. IndexCoordinatesExtractor 反射改造，支持 Spring Boot 2.2.5

**问题：**
`IndexCoordinatesExtractor` 直接 `import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates`，该类是 Spring Data Elasticsearch 4.0+ 才引入的，在 3.2.x（对应 Spring Boot 2.2.5）中不存在，导致 JVM 加载时抛出 `NoClassDefFoundError`，应用无法启动。

**根本原因：**
Java 类加载机制下，即使该提取器在 3.2.x 运行时不会被实际调用，只要 class 文件中存在对不存在类的符号引用，类加载器就会报错。

**修复：**
去掉 `IndexCoordinates` 的硬 import，改用反射：

- 使用 `Class.forName()` 在 static 块中检测 `IndexCoordinates` 是否存在，缓存 Class 和 `getIndexName()` Method 引用
- `supports()`: 用 `Class.isInstance()` 替代 `instanceof`，类不存在时返回 `false`
- `extract()`: 用反射调用 `getIndexName()`，类不存在时返回 `null`

**各 Spring Boot 版本行为：**

| Spring Boot | Spring Data ES | IndexCoordinates | 提取器行为 |
|-------------|---------------|-----------------|-----------|
| 2.7.9 | 4.3.x | 存在 | 正常工作，反射调用与直接调用结果一致 |
| 2.4.5 | 4.1.x | 存在 | 正常工作 |
| 2.2.5 | 3.2.x | 不存在 | 自动禁用，由责任链中 EntityObjectExtractor/ClassTypeExtractor 接管 |

## 向后兼容性

✅ **完全向后兼容**

- Spring Boot 2.7.x+ 用户：无感知，行为不变
- Spring Boot 2.4.x 用户：无感知，行为不变
- Spring Boot 2.2.5 单数据源用户：可正常启动，路由降级到简单 template（与 2.4.x 单数据源行为一致）
- Spring Boot 2.2.5 多数据源用户：启动失败并报错（CGLIB 代理无法创建，需升级 Spring Boot）

## 升级指南

### 从 1.0.7 升级到 1.0.8

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.0.8"
```

无需修改任何代码或配置。

## 贡献者

- @surezzzzzz
