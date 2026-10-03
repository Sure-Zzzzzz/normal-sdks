# simple-kms-client-core

KMS client 的契约层：定义与 KMS Server 交互的全部公开接口、wire 模型、错误族与传输中立认证 SPI。
SB2 与 jakarta 四个传输装配件（resttemplate-starter / feign-starter 及对应 jakarta 版）共用本模块。

## 定位

提供与 `smart-kms-server-starter:2.0.0` 对齐的 Java 契约，使调用方与 HTTP 传输细节、JSON 编解码、
认证头注入解耦。

## 适用场景

- **传输 starter 维护者**：直接依赖本模块构建传输装配件。
- **业务调用方**：不直接引入本模块，而是选择一个传输装配件（`simple-kms-client-resttemplate-starter`
  或 `simple-kms-client-feign-starter`），由其传递依赖本 core。
- **自定义传输实现方**（如 gRPC、WebClient）：需要 core 契约但自行装配。

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:simple-kms-client-core:2.0.0'
```

- 发布 POM 零依赖（jackson 仅编译期使用，由传输 starter 的 Spring Boot 提供）
- Java 8 字节码：SB2（Java 8+）与 jakarta（Java 17+）宿主均可消费
- 公开包 `io.github.surezzzzzz.sdk.kms.client`（与传输 starter 同根包）

## 公开 API

| 类别 | 内容 |
| --- | --- |
| 接口 | `KmsClient`（16 个方法：密钥生命周期、精确策略、密码学四操作、公钥分发） |
| 认证 SPI | `KmsCredentialApplier`（向出站请求注入身份头） |
| 模型 | `KmsKey` / `KmsKeyPage` / `KmsPolicy` / `KmsPublicKey` / `KmsSignature` / `KmsSigningResult` |
| 端口 | `OwnerSignerPort`（最小签名） / `OwnerPublicKeyPort`（公钥查询） / `KeyEncryptionPort`（信封加解密） |
| 异常 | `SimpleKmsClientException` 及 11 个子类（按 HTTP 语义划分） |
| 支撑 | `KmsJsonCodec`（jackson 编解码） / `KmsClientUriHelper`（URI 拼装） / `KmsHttpErrorMapper`（错误映射） / `KmsValidationHelper`（参数校验） |

## 认证 SPI

```java
public interface KmsCredentialApplier {
    void apply(Map<String, String> headers);
}
```

- 宿主实现此接口注入调用身份（如从 AKSK `TokenManager` 获取令牌写入 `Authorization: Bearer` 头）
- 键按 HTTP 语义大小写不敏感，由传输适配层归一
- 实现不得读取或修改请求体，不得在网络失败时重试获取凭据
- 各传输 starter 的自动配置接受至多一个 applier Bean；未提供时不注入身份头，由 Server 返回 `401`

## 安全边界

- 认证凭据、明文、密文、签名、AAD、envelope 不写入日志、异常 message 或模型 toString
- 客户端不缓存密钥材料与密码学结果；不内置重试；幂等键由调用方生成并负责
- 契约层不产生日志输出（脱敏与诊断责任在传输 starter 层）

## 模型设计决策

client 与 server 的模型字段存在重叠（如 `KmsKey`、`KmsPolicy`），属于有意的双投影设计：
单一事实源是 `sdk/kms/server/contract/openapi` 的 OpenAPI 契约，两侧 Java 类是同一契约面向
不同消费端的形态。

不抽取共享模型层的理由：

1. **前向兼容**：client 枚举字段为 String，新 server 版本引入新的状态或算法值时，已有 client 可正常处理。
2. **独立演进**：server 领域模型新增字段不构成 client 公开 API 变更。
3. **领域面隔离**：`stateBeforeDestruction`、密钥材料关联等服务端内部字段不出现在 client 公开 API 中。

分界原则：内部协议（两端同团队维护）采用共享 jar；对外发布的消费 SDK 采用投影加 wire 层容忍。

## 兼容性

- 对应 server 版本：`smart-kms-server-starter:2.0.0`（`/api/kms` 基路径）
- `simple-kms-client-starter` 1.x 无 2.0 直升路径，按传输方式选择新坐标
