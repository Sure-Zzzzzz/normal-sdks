# Changelog - v1.0.6

## 发布日期

2026-04-13

## 版本类型

**Minor Release** - 功能增强 + Bug 修复

## 变更概述

新增 introspect 端点匿名访问支持，修复 Admin session 超时白屏问题，完善 Admin 换 Token 测试页面。

---

## 新增功能

### 1. Introspect 端点匿名访问支持

新增配置项，支持 server 端开放匿名 introspect（适用于内网/测试环境）：

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server:
  introspect:
    require-authentication: false  # 默认 true
```

`require-authentication=false` 时，`/oauth2/introspect` 无需 Basic Auth 即可调用。启动时会打印安全警告日志。

> **安全警告**：仅适用于网络隔离的内网/测试环境，生产环境请保持默认值 `true`。

### 2. Admin 换 Token 测试页面增强

新增"对比 Introspect"按钮，同时发送带认证和匿名两种 introspect 请求，结果并排展示：
- 左侧：带 clientId + clientSecret 的 introspect（始终成功）
- 右侧：匿名 introspect（绿色=无需认证，红色=需要认证）

直观验证 `require-authentication` 配置是否生效。

---

## Bug 修复

### Admin session 超时后白屏

服务重启后浏览器持有失效的 JSESSIONID，`invalidSessionStrategy` 重定向到登录页时未清除旧 cookie，导致重定向循环白屏。

修复：
- 重定向前先清除失效的 JSESSIONID cookie
- 所有 Admin 页面加全局 fetch 拦截，AJAX 请求返回 401 时自动跳转登录页

---

## 依赖变更

```gradle
api 'io.github.sure-zzzzzz:simple-aksk-server-core:1.0.4'
```

`simple-aksk-server-core:1.0.4` 变更：
- `TokenEventType` 补全 `fromCode()`、`isValid()`、`getAllCodes()` 标准枚举方法
- `SimpleAkskServerProperties.JwtConfig.securityContextMaxSize` 默认值改为引用常量

## 向后兼容性

✅ **完全向后兼容**，现有配置无需修改。
