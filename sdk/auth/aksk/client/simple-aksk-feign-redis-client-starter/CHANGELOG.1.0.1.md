# Changelog - v1.0.1

## 发布日期

2026-04-08

## 版本类型

**Patch Release** - Bug 修复

## 变更概述

修复 `AkskFeignConfiguration` 被 Spring 全局扫描导致所有 Feign 客户端都携带 AKSK token 的问题。

## Bug 修复

### `AkskFeignConfiguration` 污染全局 Feign 上下文

**问题：**
`AkskFeignConfiguration` 标注了 `@Configuration`，Spring 启动时会将其扫描为全局 Bean，导致项目中所有 `@FeignClient` 都被注入了
AKSK 认证拦截器，包括那些不需要 AKSK 认证、有自己 token 机制的 Feign 客户端。

**修复：**
去掉 `AkskFeignConfiguration` 上的 `@Configuration` 注解。Feign 的 `configuration` 属性会为每个客户端创建独立的子上下文，不需要也不应该加
`@Configuration`。

修复后：

- 使用 `@AkskClientFeignClient` 的客户端：自动携带 AKSK token，行为不变
- 使用普通 `@FeignClient` 的客户端：不受影响，不会携带 AKSK token

## 向后兼容性

✅ **完全向后兼容**

使用 `@AkskClientFeignClient` 的业务代码无需任何修改。

## 升级指南

直接升级依赖版本即可：

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:1.0.1'
```
