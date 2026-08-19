# Simple AKSK Server Starter 3.0.0 发布验收清单

本清单用于手动发布前的收口，不执行发布、上传或标签操作。

## 新装与升级

- [ ] 新装环境仅执行 `01_schema_3.0.0.sql`，确认三张表、授权版本与乐观锁字段存在。
- [ ] 2.x 环境完成备份、停写，并且仅执行一次 `02_upgrade_3.0.0.sql`。
- [ ] 每个升级 Client 都先处理 2.x 活跃 Token，再配置完整授权并显式准入。

## 应用授权与 Token

- [ ] 缺失、未准入、禁用或已撤销投影的 Client 不能签发有效 Token。
- [ ] Token A 在撤销后 inactive；同一 Client 再准入并签发 Token B 后，A 仍 inactive、B active。
- [ ] 完整替换授权后，替换前 Token A inactive；新签发 Token B 才能携带新授权版本和新 permission。
- [ ] `scope` 与 `security_context` 不能扩大 API permission 或 DATA grant。

## 管理安全与一致性

- [ ] 管理 REST 需要精确 API permission 与逐资源 `DataAccessPlan`；授权列表在过滤后计数与分页，缺失 Client 关联的投影不可见。
- [ ] 应用授权替换/撤销还验证 `akskToken:update` 与目标 Token DATA 范围，预检失败不改变投影。
- [ ] Token 撤销失败时，本地和机器管理的替换/撤销均不留下部分投影更新。
- [ ] 两个持久化事务并发替换同一投影时恰有一个提交，最终内容没有混合，授权版本和 JPA 锁版本各只推进一次。
- [ ] 乐观锁异常经机器 REST 返回无响应体 HTTP 409。

## IAM 协作

- [ ] IAM 人员主体和 AKSK 服务主体能在同一资源服务分别认证并执行各自可信授权快照。
- [ ] 任一主体缺 API permission 时资源服务返回 403；AKSK DATA grant 在 API 通过后仍独立生效。
- [ ] 歧义、未知或被选认证源拒绝的凭据不触发跨认证源回退。
- [ ] AKSK Server 主源码与发布依赖图不包含 IAM 运行时依赖；真实三进程部署时另行运行受保护 fixture E2E。

## 交付质量

- [ ] 运行完整 Starter `test`，不使用跳过关键场景的开关。
- [ ] 执行 `git diff --check`，复核 SQL、实体、README、CHANGELOG 与手册一致。
- [ ] 按覆盖、断言、开发规范、下一版本视角、中文注释完整性复审。
- [ ] 上游 Core `3.0.0` 与 Server Core `3.0.1` Central 坐标可解析后，同时切换两个 Starter 上游依赖并完成干净外部消费者验证。
- [ ] 可选 audit listener `3.0.0` 发布后，从 Central 解析它与 Starter；确认事务回滚不投递记录、Handler record 的 `tokenValue` 为 `null`，且验收输出不包含认证材料或完整响应。
