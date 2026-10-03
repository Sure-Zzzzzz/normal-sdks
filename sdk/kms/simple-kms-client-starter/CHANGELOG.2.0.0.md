# CHANGELOG - simple-kms-client-starter 2.0.0

## 发布信息

- 版本：`2.0.0`
- 类型：Major / API 基路径切换，对齐 KMS Server 2.0.0
- 基线版本：`1.0.1`

## 主要变更（破坏性）

- `API_BASE_PATH` `/api/v1/kms` → **`/api/kms`**：对齐 `smart-kms-server-starter` 2.0.0 的端点基路径（server 无 v1 兼容层）。`base-url` 语义不变（origin 形态，Client 固定追加基路径）。
- 其余契约（签名/验签/信封/公钥/策略路径、错误映射、幂等头）与 1.0.1 一致。

## 新增（随 2.0 收口）

- `getMyDestructionPolicy()` / `saveMyDestructionPolicy(min, max)`：当前凭证身份自身的销毁窗口政策读写
  （幂等 upsert；null = 不限制侧不设限）。AKP 凭证的 owner 即 `aksk:{clientId}`（resetSecret 轮换不改 clientId，归属稳定）。

- `AkskTokenManagerKmsAuthenticationInterceptor`：宿主装配任一 AKSK `TokenManager`（如 simple-aksk-resttemplate-httpsession-client-starter）时自动提供 KMS 认证拦截器，业务不再自行实现令牌获取。
- 最小端口更名：`TenantSignerPort`→`OwnerSignerPort`、`TenantPublicKeyPort`→`OwnerPublicKeyPort`（tenant 词汇随 2.0 归一清除）。
- E2E 夹具切换为 2.0 server（联调期 project 引用，发布后切回坐标）。

## 升级说明

- **必须与 server 同批**：client 2.0.0 + server 2.0.0；client 1.0.x 对 server 2.0.0 全部 404，server 1.x 场景继续使用 client 1.0.x。
- KMS 2.0.0 起访问需经协作三权（IAM 注册 + 授权），业务方接入前阅读协作手册"组合资源服务"节。

## 测试

- 模块测试全绿（URI 构建/执行器/幂等/签名样例随基路径同步更新）。
