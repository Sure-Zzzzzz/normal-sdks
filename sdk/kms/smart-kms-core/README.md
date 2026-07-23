# smart-kms-core

`smart-kms-core` 定义 KMS 1.0 的纯 Java 8 领域契约：密钥及版本状态、精确授权、幂等、销毁任务、审计安全边界、ES256 JOSE 编码和 AES-GCM `SKMS` v1 封装。

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:smart-kms-core:1.0.0'
```

公开 Java API 根包为 `io.github.surezzzzzz.sdk.kms.core`。

## 接入边界

本模块不依赖 Spring、HTTP、MySQL 或具体 JCA Provider。`smart-kms-server-starter` 负责认证主体解析、JDBC/事务、实际 JCA 调用、审计持久化和销毁任务调度。

KMS 服务及其专属 MySQL 是可信边界。本模块不实现 KEK、DEK、信封加密、外部 KMS、HSM、TPM、系统密钥库、密钥导入、私钥导出或对称密钥导出。

## 关键约束

- 所有 tenant 身份仅从 `KmsPrincipal` 派生。
- 授权同时要求服务 scope 与精确 allow-only policy。
- 密钥材料、明文、密文、签名、AAD、凭据和异常链不得出现在对外模型、审计或日志中。
- `SKMS` v1 仅描述 AES-256-GCM 密文封装；实际加解密由 server 适配器完成。
