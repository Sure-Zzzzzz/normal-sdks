# Changelog - v1.0.1

## 发布日期

2026-07-24

## 版本类型

**Patch Release** - 向后兼容的安全增强

## 变更概述

补齐 KMS 审计事件的稳定操作标识，并将审计 metadata 白名单和值格式校验收口到 Core，避免适配层遗漏脱敏约束。

## 新增功能

### 审计操作标识

`KmsOperation` 新增创建密钥、轮换、状态变更、安排或取消销毁、创建或撤销策略、worker 处理最终销毁等稳定操作标识。

### 审计 metadata 安全边界

`KmsAuditEvent` 必须具备有效 tenant、主体、操作、结果、请求标识与发生时间；仅创建密钥在分配 `keyRef`
前被拒绝或失败时可同时省略 keyRef 与版本，其他事件均必须关联资源；最终销毁还必须关联正版本号。事件仅接受资源类型、密钥状态、版本状态、输入或输出长度、失败类别、幂等重放标记七类
metadata，并校验条数、长度和值格式。`ALLOWED` 不得携带失败类别，`REJECTED` 与 `FAILED` 必须携带固定失败类别。最终销毁操作固定使用 `KMS_SYSTEM` 系统主体。

## 向后兼容性

- 保留既有 `KmsAuditEvent` 构造器、Builder、`KmsEventPublisher`、`KmsAuditRepository` 和领域服务接口。
- 保留既有密码学与公钥读取操作的枚举名称和稳定编码。
- 精确 key policy 仅接受密码学与公钥读取操作；策略的 policyId、tenant、keyRef、主体标识及可选版本均在 Core 校验，管理和 worker 操作由管理 scope 或系统内部链路处理。
- `KmsAuditRepository` 作为历史兼容端口保留；默认链路使用提交后事件发布与独立 listener，监听失败不回滚已提交 KMS 状态。
- 本版本会拒绝 1.0.0 中曾可传入的任意 metadata；调用方需迁移为固定白名单，这是必要的安全收紧。

## 升级指南

将依赖版本升级至 `1.0.1`。构造 `KmsAuditEvent` 时，传入有效 tenant、主体、操作、结果、请求标识和发生时间；仅创建密钥在分配
`keyRef` 前被拒绝或失败时可同时省略 keyRef 与版本，最终销毁必须关联正版本号并使用固定 `KMS_SYSTEM` 主体。`ALLOWED`
不传失败类别，`REJECTED` 与 `FAILED` 必须传固定失败类别。创建或回填策略时传入有效 policyId、tenant、keyRef、主体标识及正整数版本（如指定）。不要传递密码学输入输出、材料、凭据、异常详情或任意扩展字段。
