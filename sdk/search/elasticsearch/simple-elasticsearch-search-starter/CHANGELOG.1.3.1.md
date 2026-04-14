# Changelog - v1.3.1

## 发布日期

2026-04-14

## 版本类型

**Patch Release** - Bug 修复 + 内部重构，向后兼容

## 变更概述

修复 JDK 8 兼容性问题，并将翻页逻辑重构为策略模式，提升可扩展性。

---

## Bug 修复

### JDK 8 兼容性修复

**问题：**
v1.3.0 引入的代码使用了 JDK 11+ API，在 JDK 8 环境下运行时报错：
```
java.lang.NoSuchMethodError: java.lang.String.isBlank()Z
```

**修复：**
- `String.isBlank()` → `!StringUtils.hasText()`（Spring 工具类，JDK 8 兼容）
- `InputStream.readAllBytes()` → 手动 `ByteArrayOutputStream` 循环读取（JDK 8 兼容）

---

## 内部重构

### 翻页策略模式重构

**背景：**
v1.3.0 的 `QueryExecutorImpl` 将 offset、tiebreaker、none、pit 四种翻页逻辑混写在一起，扩展新翻页方式需要修改核心执行器。

**重构内容：**

新增 `PaginationStrategy` 接口，四种翻页方式各自实现：

| 策略类 | 对应模式 |
|--------|---------|
| `OffsetPaginationStrategy` | offset 分页 |
| `TiebreakerPaginationStrategy` | search_after + _id tiebreaker |
| `NonePaginationStrategy` | search_after，无 tiebreaker |
| `PitPaginationStrategy` | search_after + PIT 快照 |

`PaginationStrategyRegistry` 负责注册和查找策略，支持用户扩展自定义策略（不允许覆盖内置策略）。

**对用户透明，无需任何修改。**

---

## 向后兼容性

✅ **完全向后兼容**，无需修改任何代码或配置。

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.3.1"
```

## 贡献者

- @surezzzzzz
