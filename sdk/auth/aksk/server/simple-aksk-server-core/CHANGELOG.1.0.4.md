# Changelog - v1.0.4

## 发布日期

2026-04-13

## 版本类型

**Patch Release** - 规范修正

## 变更内容

### 新增常量 `DEFAULT_SECURITY_CONTEXT_MAX_SIZE`

`SimpleAkskServerConstant` 新增：

```java
public static final int DEFAULT_SECURITY_CONTEXT_MAX_SIZE = 4096;
```

`SimpleAkskServerProperties.JwtConfig.securityContextMaxSize` 默认值改为引用此常量，消除硬编码。

### `TokenEventType` 补全标准枚举方法

按 SDK 开发规范，枚举类需提供 `fromCode()`、`isValid()`、`getAllCodes()` 三个标准方法：

```java
TokenEventType.fromCode("issued");    // → ISSUED，不存在返回 null
TokenEventType.isValid("revoked");    // → true
TokenEventType.getAllCodes();         // → ["issued", "revoked", "removed", "introspected"]
```

## 向后兼容性

✅ **完全向后兼容**，无破坏性变更。
