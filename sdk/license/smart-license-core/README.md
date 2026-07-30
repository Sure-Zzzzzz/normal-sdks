# smart-license-core

`smart-license-core` 是纯 Java 8 的 License 签发领域内核：调用方为业务 `kid` 固化 KMS 精确密钥版本与可发布公钥的映射，再由 KMS 对固定 ES256 Compact JWS 签名。

它适合嵌入 License Server；不提供 Spring 自动装配、HTTP 接口、数据库实现、JSON 实现或离线验签能力。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>smart-license-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

运行时最低需要 `simple-kms-client-starter:1.0.1` 提供的 `TenantPublicKeyPort` 与 `TenantSignerPort`。Core 的公开 API 兼容 Java 8；它没有 Spring 配置项，也没有自动装配。

## 最小接入路径

签发前完成以下四步：

1. 由 Server 适配层实现 `LicenseKeyMappingRepository` 和 `LicensePayloadCodec`。
2. 从已配置的 KMS Client 获取 `TenantPublicKeyPort` 与 `TenantSignerPort`。
3. 读取 KMS 某个精确版本的 ES256 公钥，创建一次性业务 `kid` 映射。
4. 用同一 tenant、同一 `kid` 和已验证 Claims 组装签发命令。

```java
TenantPublicKeyPort publicKeyPort = /* 已配置的 KMS Client 端口 */;
TenantSignerPort signerPort = /* 已配置的 KMS Client 端口 */;
LicenseKeyMappingRepository mappingRepository = /* Server 持久化适配 */;
LicensePayloadCodec payloadCodec = /* Server 确定性 JSON 编码适配 */;

LicenseKeyMappingService mappingService = new DefaultLicenseKeyMappingService(
        mappingRepository, publicKeyPort);
LicenseIssuanceService issuanceService = new DefaultLicenseIssuanceService(
        mappingRepository, signerPort, payloadCodec);

String keyRef = "kms-signing-key";
Integer keyVersion = 1;
KmsPublicKey kmsPublicKey = publicKeyPort.read(keyRef, keyVersion);

LicenseKeyMapping mapping = LicenseKeyMapping.builder()
        .tenantId("tenant-example")
        .kid("lic-example-es256-v1")
        .kmsKeyRef(kmsPublicKey.getKeyRef())
        .kmsKeyVersion(kmsPublicKey.getVersion())
        .algorithm(kmsPublicKey.getAlgorithm())
        .publicKey(kmsPublicKey.getPublicKey())
        .status(LicenseKeyStatus.fromCode(kmsPublicKey.getState()))
        .build();
mappingService.create(mapping);

Instant now = Instant.now();
LicenseClaims claims = LicenseClaims.builder()
        .jti("lic-example-001")
        .issuer("tenant-example")
        .tenantId("tenant-example")
        .audience("product-example")
        .customerId("customer-example")
        .deviceKeyFingerprint("sha256:example")
        .issuedAt(now)
        .notBefore(now)
        .schemaVersion(1)
        .terms(Collections.<LicenseTerm>singletonList(new CapacityTerm("nodes", 100)))
        .build();

IssuedLicense issued = issuanceService.issue(LicenseIssueCommand.builder()
        .tenantId("tenant-example")
        .kid("lic-example-es256-v1")
        .claims(claims)
        .build());
```

`KmsPublicKey` 必须来自 KMS 对应 `keyRef` 和精确 `version` 的读取结果，且其公钥为 X.509 SubjectPublicKeyInfo DER 字节。`mappingService.create` 会再次通过 KMS 端口核对 `keyRef`、版本、`ES256`、状态和公钥字节；任一不一致即失败。

## 必须实现的两个适配端口

### `LicenseKeyMappingRepository`

- 查询必须以 `(tenantId, kid)` 为条件。
- 创建必须在持久化事务内以 `(tenantId, kid)` 唯一约束拒绝并发创建与重绑。
- 已发布映射不得更新 KMS `keyRef`、版本、公钥、算法或状态；KMS 轮换必须创建新的业务 `kid`。
- `ACTIVE` 映射可签发新 License；`RETIRED` 映射只保留给历史公钥发布和验签，不能新签发。

### `LicensePayloadCodec`

Core 故意不包含 JSON 库。Server 必须实现确定性的紧凑 JSON 编码：相同 `LicenseClaims` 必须生成相同 payload 文本，并以协议字段名、UTC Epoch 秒 NumericDate 表示时间。Core 会对 codec 返回的文本原样 UTF-8 编码、Base64URL 编码并签名，不会重新解析或序列化。

自定义条款只能在目标校验端已经具备同类型执行器时签发；校验端遇到未知 `term.type` 必须拒绝，不得忽略。

## v1 条款

Core 内置以下标准条款模型：

| 条款 | 模型 | 规则 |
| --- | --- | --- |
| `featureSet` | `FeatureSetTerm` | 功能标识集合非空、去重、排序。 |
| `capacity` | `CapacityTerm` | `metric` 非空，`limit` 为非负整数。 |
| `trial` | `TrialTerm` | `durationDays` 为正整数。 |

`LicenseTerm#getType()` 返回受校验的字符串类型代码。因此后续可新增标准或租户自定义条款，而不改变 Core 公共接口；新增条款必须同步提供所有目标 Verifier 的执行器。

## 协议与安全约束

完整规则以 [License JWS 协议](../LICENSE-JWS-PROTOCOL.md) 为准。接入时必须遵守：

- protected header 固定且仅含 `alg=ES256`、业务 `kid`、`typ=JWT`；不得从 payload 选钥。
- `iss` 必须与 `tenantId` 完全一致；`iat <= nbf`；限期 License 必须 `nbf < exp`，永久 License 省略 `exp`。
- 所有 Base64URL 段无 padding；签名必须是 64 字节 JOSE ES256 `R || S`，不接受 ASN.1 DER。
- KMS 返回版本必须精确等于映射版本；算法必须为 `ES256`。任一异常或不一致均失败关闭，Core 不重试、不改写 header/payload，也不返回部分 License。
- Core 不读取 KMS 表、不保存或导出私钥；完整 JWS、payload、公钥和签名不会进入 Core 默认 `toString()` 或错误消息。

## 模块边界

`smart-license-server-starter` 后续负责持久化、JSON codec、身份适配、产品选择、激活、吊销和在线权威校验。`simple-license-verifier-starter` 后续负责离线验签、设备绑定、条款执行和本地时钟防回拨。Verifier 不连接 KMS、不读取 KMS 表，也不持有私钥。
