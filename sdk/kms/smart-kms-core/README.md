# smart-kms-core

`smart-kms-core` 定义 KMS 1.0 的纯 Java 8 领域契约：密钥及版本状态、精确授权、幂等、销毁任务、审计安全边界、ES256 JOSE 编码和
AES-GCM `SKMS` v1 封装。

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:smart-kms-core:1.0.1'
```

公开 Java API 根包为 `io.github.surezzzzzz.sdk.kms.core`。

## 接入边界

本模块不依赖 Spring、HTTP、MySQL 或具体 JCA Provider。`smart-kms-server-starter` 负责认证主体解析、JDBC/事务、实际 JCA
调用、严格脱敏的审计事件发布和销毁任务调度；独立 `sdk/audit/kms` listener 负责审计事件的转换与外部持久化/投递。

KMS 服务及其专属 MySQL 是可信边界。本模块不实现 KEK、DEK、信封加密、外部 KMS、HSM、TPM、系统密钥库、密钥导入、私钥导出或对称密钥导出。

## 关键约束

- 所有 tenant 身份仅从 `KmsPrincipal` 派生。
- 授权同时要求服务 scope 与精确 allow-only policy。
- 密钥材料、明文、密文、签名、AAD、凭据和异常链不得出现在对外模型、审计或日志中。
- `KmsAuditEvent` 必须包含有效 tenant、主体、操作、结果、请求标识和发生时间；仅创建密钥在分配 `keyRef` 前被拒绝或失败时可同时省略
  `keyRef` 与版本，其他事件均必须关联资源。`PROCESS_KEY_DESTRUCTION` 必须关联正版本号。事件仅接受固定操作与安全 metadata：资源类型、密钥或版本状态、输入或
  输出长度、失败类别和幂等重放标记；任意其他 metadata 均会被 Core 拒绝。
- `PROCESS_KEY_DESTRUCTION` 只能使用固定 `KMS_SYSTEM` 主体；其他操作不得冒用该主体。精确 key policy 必须具备有效 policyId、tenant、keyRef 与主体标识；可选版本存在时必须为正整数；仅可授权密码学或公钥读取操作，管理与 worker 操作不进入 policy。
- `KmsAuditOutcome.ALLOWED` 表示操作已正常完成，验签结果为 `false` 时同样使用该结果，且不得携带 `failureCategory`；`REJECTED` 与
  `FAILED` 分别表示拒绝和未完成失败，均必须携带固定 `failureCategory`。
- `SKMS` v1 仅描述 AES-256-GCM 密文封装；实际加解密由 server 适配器完成。
