# simple-license-audit-listener-starter 2.0.0

## 首发

- 提供 License 提交后领域事件的异步审计监听和宿主 `LicenseAuditHandler` 扩展点。
- 覆盖授权、凭据、吊销、模型、角色协作、额度和吊销包终态事件。
- 处理器异常隔离，不回滚 License 已提交事务；无处理器时不注册监听器。

## 安全与边界

- 审计记录不包含 Compact JWS、Claims、签名、KMS keyRef、公钥材料、凭据正文或令牌。
- 本模块只补业务操作审计；HTTP 访问审计仍由宿主的 Resource 事件体系负责。
- 事件不是持久队列，宿主需自行负责可靠落地、幂等消费和恢复。

## 测试覆盖

- 覆盖默认处理器注册、无处理器不装配、提交后事件分发和处理器异常隔离路径。
