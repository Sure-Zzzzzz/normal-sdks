# simple-license-verifier-starter

为需要在本机验证 License 签名文件的产品装配验证内核。模块组合 `smart-license-core` 的 `DefaultLicenseVerifier`、默认时钟、信任资料仓、状态文件仓和持久化吊销仓；真实主体与设备证据由宿主提供。

## 解决什么、谁该接

验证端使用协议 2 与 ES256 Compact JWS（三段签名文本）。离线凭据的用途为 `license+jwt`，混合租约为 `license-lease+jwt`。模块不提供身份认证、远端 HTTP 传输、设备采集或私钥签发，不连接 IAM、CRM 或 KMS。

## 依赖坐标

```gradle
implementation 'io.github.sure-zzzzzz:simple-license-verifier-starter:2.0.1'
```

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-license-verifier-starter</artifactId>
    <version>2.0.1</version>
</dependency>
```

模块传递 Core 与 Client Core 契约，宿主不需要重复声明这两个模块。信任资料安装器接收 Client Core 的 `TrustMaterialSnapshot`，因此该依赖属于公开 API，不能改为 `compileOnly`。运行基线为 Spring Boot 2.7.9；公开 Java API 按 Java 8 编写。

## 最小配置与完整配置

本模块默认有以下三个配置项；没有离线身份档案时，第三项可省略，但带 `offlineProofProfile` 的凭据会 fail-closed：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        license:
          verifier:
            enabled: true
            state-dir: /var/lib/myapp/license-verifier-state
            supported-offline-proof-profiles: iam/1,service-key/1
```

| 配置项 | 说明 |
| --- | --- |
| enabled | 必须显式设为 true 才装配验证组件；未配置时不启用 |
| state-dir | 启用默认文件仓时必填，用于可信时间状态及吊销快照；建议独立绝对路径并持久化挂载 |
| supported-offline-proof-profiles | 宿主已安装且实际能核验的离线身份档案，逗号分隔；凭据自带的 profile 不会自动注册为本机能力；未登记则拒绝对应离线/混合凭据 |

设备模式、商业期限、宽限动作及吊销新鲜度来自签名文件的政策，不提供启动配置覆盖这些值。

## 使用方式

```java
VerifiedCredential handle = verifier.verify(credentialBytes, verificationContext);
Decision decision = verifier.authorize(handle, "use", bindingEvidence, trustedFacts);
if (!decision.isAllowed()) {
    // 宿主停止对应业务动作，不把拒绝或异常转换为成功。
}
```

`verifier` 是注入的 `DefaultLicenseVerifier`。`verificationContext` 由受控登记的签发方、产品、客户、授权和预期绑定组成，不能从待验文件抄取。`bindingEvidence` 实现 `FileVerificationInputs`，由宿主逐次完成真实设备核验，并对人员/服务通过受控 `offlineProofProfile` 返回真实 `SubjectRef`；只有布尔通过标记不能授权。人员/服务还必须提供 `TrustedSubjectProvider` Bean，verifier 每次 authorize 会同时核对 profile 版本、证明有效截止和当前主体；缺失、过期、版本不匹配或异常均 fail-closed。`trustedFacts` 来自宿主可信采集。

`verify` 只证明基础凭据有效，不能永久缓存动作许可。每次 `authorize` 都复查当前信任、原文签名、吊销、时间、主体/设备证据、阶段及规则；异常和 DENY 都应停止相关业务动作。

## 端口替换

验证端口按 `@ConditionalOnMissingBean` 让位给宿主 Bean；`TrustedSubjectProvider` 没有默认实现：

| 端口 | 默认实现 | 何时必须替换 |
| --- | --- | --- |
| `ClockProvider` | `SystemClockProvider`（基线滚动重置防漂移误报） | 有可信硬件时间源时 |
| `TrustStore` | `InMemoryTrustStore`（版本单调原子安装） | 需持久化信任资料/多实例共享时 |
| `StateStore` | `FileStateStore`，原子替换文件并保存可信时间与存储水位 | 需共享存储或自定义布局时；默认单进程独占 |
| `RevocationStore` | `FileRevocationStore`，持久化按签发方/产品划分的吊销快照 | 需使用其他持久化介质时 |
| `TrustedSubjectProvider` | 无默认实现 | 人员/服务离线或混合授权必填；由宿主接入已有本地身份核验，不由本模块创建账号或令牌 |

默认吊销仓实现为 `FileRevocationStore`（实现 `AtomicRevocationStore`），同时提供 `LicenseTrustMaterialInstaller`。宿主替换为仅实现查询接口的 RevocationStore 时，需要自备完整资料安装流程。验证端不注册 LicenseSigner。

信任资料安装器先使用已登记的 REVOCATION 用途公钥对清单和全部分片验签，再核对摘要、作用范围、序号与快照元数据，全部通过后安装。传输响应内的公钥不会自动成为信任根。整批核验失败保留已有吊销资料。

## 语义边界

- 验证顺序固定（格式→选钥验签→结构→目标绑定→吊销→新鲜度→时间→规则），`authorize` 每次判定复查当前吊销状态，不复用导入时结论。
- 状态仓只增不减：已知否定水位（吊销/资格撤销）在重启后不得回退放行；跨修订/换文件不重置状态目录。默认文件仓以旁路锁文件和写前回读保护多个实例的单调水位，首次空目录允许初始化；旁路锁存在时主状态文件缺失或损坏按失败关闭处理。
- 文件状态仓支持同一状态目录的多实例串行写入，但不防整盘回滚、备份恢复或网络文件系统不具备锁语义的场景；这些场景需宿主提供更高强度的可信锚点或专用 StateStore。
- 设备绑定证据（WEIGHTED 分量采集/DEVICE_KEY 挑战）由宿主端口先行完成，本件只消费布尔结论；分量级匹配算法属核心引擎后续能力。
- REQUIRED 吊销政策要求已安装资料足够新鲜；缺少或过期即拒绝。OPTIONAL 仍检查已知吊销。
- 实名额度动作要求与固定资格编号、代数和指标相符的签名引用，不能只凭权益数量允许。
- HYBRID 在线并发动作要求有效的已签占用引用，最终租约截止不得超过占用有效截止。
- LOCAL 或在线累计扣减动作需要业务操作编号与原子台账事务；此判定入口返回 DENY/CAPABILITY_MISSING，部署分配不等于消费成功。
- 独立维护截止没有类型化维护动作白名单时，动作返回 DENY/CAPABILITY_MISSING。组织型混合文件缺少可核验成员观察结构时，报 CAPABILITY_UNSUPPORTED。
- 未知必需能力拒绝，真实设备/主体证据不能用常量 true 代替。
- `subject-proof/{profile}` 必须同时出现在宿主受控能力登记和凭据的必需能力集合中；不要把授权文件中的 profile 当作本机能力来源。

## 最佳实践

1. 独立交付并安装可信公钥，按凭据签名、吊销和状态恢复用途隔离；待验文件不允许自授权公钥。
2. 将 state-dir 挂载到持久化存储，使用独立进程目录；不得通过清目录或重导入文件重置可信时间。纯软件文件仓不能防止整盘回滚，需更强保护时由宿主提供可信锚点。
3. 使用 REQUIRED 政策时安排受控资料更新，完整验签后再安装，不先清旧仓。重启后同时恢复可信公钥和持久状态。
4. 产品提交动作前逐次调用 authorize，保留结构化拒绝分类；额度预占/扣减与实际业务提交另行原子关联，不能把 ALLOW 当作扣减成功。
5. 记录动作及安全错误分类供宿主诊断；不输出原始签名文件、令牌、设备指纹或完整权益。

## 聚散形态

本件为验证端（散侧）核心装配；在线判定走 client 三件套（聚侧）。两者共用 `smart-license-core` 协议类型，不共用传输。
