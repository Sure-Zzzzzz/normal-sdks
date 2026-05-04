# Changelog - v1.0.1

## 发布日期

2026-05-04

## 版本类型

Bug 修复 + 重构

## 变更概述

### Bug 修复

**AkskRestTemplateInterceptor 硬编码修复**：`Authorization` 和 `Bearer ` 改为引用 `SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION` 和 `HEADER_AUTHORIZATION_TEMPLATE`，与 Feign 拦截器保持一致。

### 规范修复

**AkskRestTemplateInterceptor 加 `@RequiredArgsConstructor`**：去掉手写构造函数，符合 SDK Lombok 规范。

### 重构

**新增 `SimpleAkskRestTemplateConstant`**：提取 `SimpleAkskRestTemplateProperties` 中的硬编码默认值（连接池大小、超时时间）和配置前缀到常量类，符合硬编码零容忍规范。

## 依赖升级

`simple-aksk-redis-token-manager`: 1.0.0 → 1.0.1

## 向后兼容性

完全向后兼容，默认值与原版本一致。

## 贡献者

- @surezzzzzz
