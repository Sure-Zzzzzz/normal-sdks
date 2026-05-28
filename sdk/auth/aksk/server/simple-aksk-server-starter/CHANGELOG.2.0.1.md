# CHANGELOG - simple-aksk-server-starter 2.0.1

## 发布日期

2026-05-28

## 版本类型

Patch Release - Bug Fix，向后兼容

## 变更概述

修复 `TokenManagementServiceImpl.deserializeTokenValue` 无法解析 raw JWT token 的问题。无论是 1.x JWS 还是 2.0.0 JWE，`accessTokenValue` 存储的均为原始 JWT 字符串（以 `eyJ` 开头），但该方法仍按旧 JSON 格式解析，导致 `JsonParseException`。

## 变更详情

### Bug Fix

**`TokenManagementServiceImpl.deserializeTokenValue`** - 修复 raw JWT token 反序列化失败

| 问题 | 说明 |
|------|------|
| 根因 | 无论 1.x JWS 还是 2.0.0 JWE，`accessTokenValue` 存储的均为原始 JWT 字符串（以 `eyJ` 开头），但反序列化方法仍按旧 JSON 格式解析 |
| 现象 | 清理/撤销 Token 时 `JsonParseException: Unrecognized token 'eyJ...'` |
| 修复 | 新增 `JWT_TOKEN_PREFIX` 前缀判断：raw JWT 直接返回，1.x 旧 JSON 数据走 JSON 解析 |

修复前：
```java
private String deserializeTokenValue(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return null;
    try {
        String tokenStr = new String(bytes, UTF_8);
        JsonNode root = OBJECT_MAPPER.readTree(tokenStr); // ❌ raw JWT 不是 JSON
        JsonNode tokenValue = root.get(JSON_FIELD_TOKEN_VALUE);
        return tokenValue != null ? tokenValue.asText() : null;
    } catch (Exception e) {
        log.debug("Failed to deserialize token value", e);
        return null;
    }
}
```

修复后：
```java
private String deserializeTokenValue(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return null;
    try {
        String tokenStr = new String(bytes, UTF_8);
        // 原始 JWT 以 eyJ 开头，直接返回；1.x 旧数据走 JSON 解析
        if (tokenStr.startsWith(JWT_TOKEN_PREFIX)) {
            return tokenStr;
        }
        JsonNode root = OBJECT_MAPPER.readTree(tokenStr);
        JsonNode tokenValue = root.get(JSON_FIELD_TOKEN_VALUE);
        return tokenValue != null ? tokenValue.asText() : null;
    } catch (Exception e) {
        log.debug("Failed to deserialize token value", e);
        return null;
    }
}
```

### 行为变更

- 1.x 活跃 Token：raw JWS 直接返回，清理/撤销正常
- 2.0.0 Token：raw JWE 直接返回，清理/撤销正常
- 1.x 旧数据（JSON 格式）：仍然走 JSON 解析，兼容旧数据

### 代码整洁

| 变更 | 说明 |
|------|------|
| 新增 `JWT_TOKEN_PREFIX` 常量 | `"eyJ"` 提取为类内常量，方法中不再散落魔法值 |
| 新增 `PROP_ACCESS_TOKEN_ISSUED_AT` 常量 | JPA 查询排序字段提取为常量 |

---

## 贡献者

- @surezzzzzz