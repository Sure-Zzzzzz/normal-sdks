# smart-kms-server-starter

面向独立 Spring Boot 2.7.9 服务的 KMS Server 模块。它以 KMS Server 与专属 MySQL 数据库构成可信边界，为业务服务提供受控的密钥管理、精确授权、签名验签、加解密、公钥分发、延迟销毁和审计事件发布能力。

适合被 License 等业务服务通过 HTTP 调用；业务服务不得直连 `smart_kms_*` 表，也不能取得任何私钥或 AES 密钥材料。

## 已提供的能力

### 逻辑密钥与版本管理

- 创建 ES256 签名密钥或 AES-256-GCM 加密密钥，服务端自动生成材料与随机 `keyRef`。
- 查询单个密钥、分页列出当前 tenant 的密钥，并按别名、用途、算法、状态筛选。
- 修改逻辑密钥状态；使用乐观锁版本防止并发覆盖。
- 轮换密钥版本：旧活动版本退役，新版本成为唯一活动版本。
- 安排整个逻辑密钥及其未销毁版本在指定 UTC 时间后销毁；未被 worker 首次领取前可取消。
- 内置租约 CAS 销毁 worker：支持实例故障后的租约恢复、历史领取后禁止取消、连续失败时停止新任务领取。

### 精确授权与公钥分发

- 管理操作要求 `kms.manage` scope。
- 密码学和公钥读取同时要求对应 scope 与 allow-only 精确策略；策略可限定主体、具体版本、操作和到期时间。
- 支持创建、列表和撤销策略；`keyVersion` 省略时授权该逻辑密钥的全部可执行版本。
- 支持读取 ES256 单版本公钥和全部可分发公钥；公钥响应固定 `Cache-Control: no-store`。

### 密码学操作

- ES256：`secp256r1`、`SHA256withECDSA`、64 字节 JOSE 签名和 low-S 输出。
- AES-256-GCM：随机 12 字节 IV、16 字节 tag、Core `SKMS` 二进制封装与可选 AAD。
- 验签失败是正常结果，返回 HTTP `200` 与 `{ "valid": false }`。
- 二进制字段均使用无填充 Base64url；服务端在解码前后执行长度限制。

### 幂等与审计

- 所有管理写操作必须带 `Idempotency-Key`，作用域为 tenant、认证主体、具体端点与幂等键。
- 相同规范化请求重放最初的成功状态和安全响应快照；相同作用域不同请求返回 HTTP `409`。
- 发布 Core `KmsAuditEvent`：成功操作仅在事务提交后发布，拒绝和失败操作尽力发布；审计 listener 失败不会改变 KMS 操作结果。

## 接入前提

- 使用 Java 8 字节码兼容的 Spring Boot 2.7.9 应用。
- 准备专属 MySQL 5.7+ 数据库，并在启用服务前人工执行 [docs/01_schema.sql](docs/01_schema.sql)。模块不会自动建表或迁移数据。
- 应用提供 JDBC `DataSource`、`PlatformTransactionManager` 和认证基础设施。
- 应用必须提供 `KmsPrincipalResolver` Bean。它只能从已认证安全上下文解析 `principalId`、tenant、scope 和 requestId；路径、查询参数、请求头和正文均不能指定或覆盖 tenant。
- 启用销毁 worker 时，`worker.instance-id` 可选：未配置或配置为空白时，当前进程自动生成 UUID；显式配置稳定值可让 worker 连续失败状态跨重启保留。服务关闭后不再领取新任务；已领取任务由租约到期后的后续实例恢复。

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:smart-kms-server-starter:1.0.0'
```

## 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kms:
          server:
            enable: true
            worker:
              enable: true
              # 可选：未配置时自动生成当前进程 UUID；需要跨重启保留连续失败状态时再显式设置
              instance-id: kms-server-a
```

普通 Deployment 无需通过环境变量、Pod 名或 StatefulSet 注入实例标识即可启用 worker。显式 `instance-id` 仅用于 worker 连续失败状态和排障归属，不参与任务互斥；任务领取始终由 MySQL CAS、lease 与 claim token 协调。

可选配置包括：密钥列表默认/最大分页大小、管理幂等记录保留时长、签名输入/签名/明文/AAD/密文封装的最大字节数，以及 worker 扫描间隔、租约时长和连续失败阈值。默认值与完整字段见 `SmartKmsServerProperties`。

## HTTP API

基础路径为 `/api/v1/kms`。接口均为 REST API，只使用 HTTP status 表达结果，不返回统一包装体或业务 `code`。错误响应仅包含安全中文消息、UTC 时间和 requestId。

| 方法 | 路径 | 能力 |
| --- | --- | --- |
| `POST` | `/keys` | 创建逻辑密钥，返回 `201` 和 `Location` |
| `GET` | `/keys` | 分页查询当前 tenant 密钥 |
| `GET` | `/keys/{keyRef}` | 查询单个密钥元数据 |
| `PATCH` | `/keys/{keyRef}/state` | 修改密钥状态 |
| `POST` | `/keys/{keyRef}/versions` | 轮换至下一个活动版本 |
| `PUT` | `/keys/{keyRef}/destruction` | 安排延迟销毁 |
| `DELETE` | `/keys/{keyRef}/destruction` | 取消未被领取的销毁任务 |
| `GET` | `/keys/{keyRef}/public-key` | 读取一个 ES256 公钥 |
| `GET` | `/keys/{keyRef}/public-keys` | 读取全部可分发 ES256 公钥 |
| `POST` | `/keys/{keyRef}/policies` | 创建 allow-only 精确策略 |
| `GET` | `/keys/{keyRef}/policies` | 查询策略 |
| `DELETE` | `/keys/{keyRef}/policies/{policyId}` | 撤销策略 |
| `POST` | `/crypto/signatures` | 创建 ES256 签名 |
| `POST` | `/crypto/verifications` | 验证 ES256 签名 |
| `POST` | `/crypto/envelopes` | 创建 AES-GCM 密文封装 |
| `POST` | `/crypto/decryptions` | 解开 AES-GCM 密文封装 |

所有管理变更请求都必须携带 `Idempotency-Key`。状态修改、轮换、安排/取消销毁和撤销策略还必须传入当前 `expectedRowVersion`。

密码学接口不使用 `Idempotency-Key`，也不会自动重试；签名和 AES-GCM 输出包含随机性，调用方在网络结果未知时不能盲目重放请求。

完整请求字段、响应字段和状态约定见 [DESIGN.md](DESIGN.md)。

## 可信边界与限制

- 私有 EC 密钥与 AES 密钥材料仅以 BLOB 保存于专属 KMS MySQL；不会出现在 HTTP 响应、日志、审计事件或管理幂等响应快照中。
- 不支持密钥导入、导出、KEK、DEK、信封加密存储、根密钥、HSM、TPM、操作系统密钥库、外部 KMS、配置文件密钥或人工解封。
- 数据库 UTC 时间是策略到期、状态迁移、销毁调度和租约判断的权威时间。
- Server 1.0 不提供 Actuator、独立运维界面或公开销毁 worker 运维接口；未来管理页面进入 IAM 乾坤微前端壳。
- 模块只发布 Core 审计事件，不内置审计落库或投递 listener；审计 listener 由后续独立模块提供。
