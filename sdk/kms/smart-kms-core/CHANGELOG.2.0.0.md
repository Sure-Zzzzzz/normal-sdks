# CHANGELOG - smart-kms-core 2.0.0

## 发布信息

- 版本：`2.0.0`
- 类型：Major / 租户模型拆除，归属切换 ownerPrincipalId
- 基线版本：`1.0.1`

## 版本定位

KMS 1.x 以 `tenantId` 为归属主键，与协作三权体系（IAM 主体投影）无法对接。2.0.0 将归属全面切换为稳定主体标识 `ownerPrincipalId = sourceId:subjectId`（如 `iam:{subjectId}`、`aksk:{clientId}`），对齐协作手册"业务资源服务"定位：IAM 清库、用户改名、租户增删均不影响 KMS 已持久化归属。

## 主要变更（破坏性）

- **`tenantId` 字段全量移除**：`KmsKey` / `KmsKeyVersion` / `KmsKeyPolicy` / `KmsAuditEvent` / 幂等记录及仓储契约的构造器与字段统一替换为 `ownerPrincipalId`（校验规则：非空、≤256 码点、`sourceId:subjectId` 形态由上层合成，core 不感知 IAM/AKSK）。
- **审计事件主体语义**：`KmsAuditEvent.ownerPrincipalId` 为操作所属 owner；操作者主体保持 `principalId` 独立字段（owner 与操作者可为不同主体——管理面代操作场景）。
- **owner 级销毁窗口政策**（新增能力）：`KmsOwnerDestructionPolicy` 模型 + `KmsOwnerDestructionPolicyRepository` 接口 +
  `KmsOperation.SET_OWNER_DESTRUCTION_POLICY` 操作码。窗口是 owner（人或业务凭证）自身的政策选择：无行=不限制
  （组件能力态），min/max 可单侧设置；闭区间强校验与服务端执行在 server-starter，core 仅定义模型与操作契约。
- 上述构造器签名变化对 1.x 使用方为**源码级破坏**；core 无默认行为变化，校验/脱敏/幂等语义与 1.x 逐项对齐。

## 兼容性

- 依赖方必须同批升级：`smart-kms-server-starter` 2.0.0（实体/SQL/控制器同步切换）、`simple-kms-client-starter` 2.0.0。
- 数据迁移：存量 `tenant_id` 列数据无法自动映射为 ownerPrincipalId（语义不同）——2.0.0 不提供迁移脚本，升级部署按全新归属初始化（详见 server-starter CHANGELOG 的升级说明）。

## 测试

- 模块测试全绿（contract/model/idempotency 三族），fixture 样例值统一为主体形态（`iam:10001` 等）。
