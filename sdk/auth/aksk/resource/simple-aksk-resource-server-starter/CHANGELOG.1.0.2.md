# Changelog - v1.0.2

## 发布日期

2026-04-13

## 版本类型

**Minor Release** - 功能增强 + Bug 修复

## 变更概述

新增 introspect 验证模式，修复 scope claim 解析 bug。

---

## 新增功能

### 1. Introspect 验证模式

新增 `verificationMode` 配置项，支持两种 token 验证方式：

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| `JWT`（默认） | 本地验签，性能最好 | 不需要即时撤销感知 |
| `INTROSPECT` | 调 `/oauth2/introspect` 端点验证 | 需要即时感知 token 撤销 |

```yaml
io.github.surezzzzzz.sdk.auth.aksk.resource.server:
  verification-mode: INTROSPECT   # JWT（默认）或 INTROSPECT
  introspect:
    endpoint: http://localhost:8080/oauth2/introspect
    client-id: AKP...    # 留空则不带认证（需 server 端 require-authentication=false）
    client-secret: SK...
```

introspect 模式下，验证通过后同样会：
- 提取 claims 注入安全上下文（`SimpleAkskSecurityContextHelper` 可正常使用）
- 发布 `AkskAccessEvent` 事件（`source="introspect"`）
- 支持所有权限注解（`@RequireContext`、`@RequireField` 等）

### 2. 新增 `VerificationMode` 枚举

```java
VerificationMode.fromCode("introspect");   // → INTROSPECT
VerificationMode.isValid("jwt");           // → true
VerificationMode.getAllCodes();            // → ["jwt", "introspect"]
```

---

## Bug 修复

### scope claim 解析错误

Spring Authorization Server 将 scope 存为 `List<String>`，`toString()` 后变成 `[/api/**]`（带方括号），导致 `SimpleAkskSecurityContextHelper.getScope()` 返回空列表。

修复：scope 等 List 类型的 claim 改用空格拼接，正确解析为 `/api/**`。

---

## 向后兼容性

✅ **完全向后兼容**，默认仍为 JWT 模式，现有配置无需修改。
