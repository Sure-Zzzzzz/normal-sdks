# Changelog - v1.0.3

## 发布日期

2026-04-12

## 版本类型

**Patch Release** - 规范修正，向后兼容

## 变更概述

`SearchAfterMode` 枚举按编码规范补全，字段名、方法名、注解均对齐规范要求。

## 变更内容

### `SearchAfterMode` 枚举规范化

- 字段名从 `mode` 改为 `code`，新增 `description` 字段
- 新增 `@Getter` 注解
- 方法名从 `fromString` 改为 `fromCode`，对齐枚举规范
- 新增 `isValid(String code)` 方法
- 新增 `getAllCodes()` 方法
- `toString()` 覆盖返回 `code`

### `PaginationInfo` 同步更新

- `getSearchAfterModeEnum()` 内部调用改为 `fromCode()`

## 向后兼容性

⚠️ **不兼容变更（仅影响直接调用枚举方法的代码）**

- `SearchAfterMode.fromString()` 已重命名为 `fromCode()`
- `SearchAfterMode.getMode()` 已重命名为 `getCode()`（由 `@Getter` 生成）

如果业务代码直接调用了这两个方法，需要同步修改。通过 `PaginationInfo.getSearchAfterModeEnum()` 使用的调用方无需修改。

## 升级指南

### 从 1.0.2 升级到 1.0.3

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-core:1.0.3"
```

检查是否直接调用了 `SearchAfterMode.fromString()` 或 `.getMode()`，改为 `fromCode()` / `getCode()`。

## 贡献者

- @surezzzzzz
