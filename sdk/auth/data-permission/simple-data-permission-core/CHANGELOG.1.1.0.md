# 1.1.0 版本变更

## 新增功能

### 1. 结构化 Claim 映射

新增 `DataGrantDocumentClaimMapper`，支持在不经过 JSON 字符串二次转换的情况下，将 `DataGrantDocument` 转换为结构化 Java Claim，或从结构化 Claim 还原授权文档。

- 只接受 `Map`、`List`、`String` 和 `Boolean` 协议类型。
- 每层 object 必须严格匹配协议字段全集，拒绝缺失字段、未知字段和非字符串字段名。
- 保持协议字段顺序、授权项顺序和约束顺序。
- 输出的 Map、List 及嵌套对象均不可修改。
- 通过既有数据权限模型完成最终协议校验，不重复实现 DATA grant 语义。

### 2. Claim 防御性边界

结构化 Claim 增加节点数量和 UTF-8 文本字节预算，超限立即拒绝，不截断、不丢字段、不返回部分文档。

- 错误输入统一使用既有 `INVALID_DOCUMENT` 错误码。
- 不回显完整 Claim、约束值或业务敏感内容。
- 不新增 Spring、Jackson、IAM、AKSK、ORM 或 HTTP 生产依赖。

## 兼容性

- `DataGrantDocument`、`DataGrant`、`DataConstraint`、`DataPermissionEvaluator` 和既有 SPI 保持兼容。
- 1.0 协议模型、评估结果、授权命中语义、集合规范化顺序和规范 JSON 语义保持不变。
- 新增 Mapper 只证明 Claim 结构和 DATA 协议合法，不替代载体签名、解密、主体绑定、应用绑定、时效或授权版本校验。

## 修复

- 保留 1.0 协议规范 fixture 的 canonical 内容，避免测试资源格式变化掩盖规范 JSON 顺序和紧凑格式回归。
