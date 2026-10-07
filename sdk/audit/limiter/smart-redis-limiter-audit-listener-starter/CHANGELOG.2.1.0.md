# 2.1.0

类型：功能升级（接入类型化管理事件审计）。

## 变更

- 监听器新增消费 v2 类型化管理事件（`SmartRedisLimiterTypedManagementEvent`）：转换为只含受控摘要字段的审计记录（操作/服务/资源/维度/选择器/规则标识/revision/结果/受控原因/操作人短摘要），计数对象只保留命名空间受控摘要，不输出原始用户、客户、IP 或自定义 key，不输出 operatorIdentity 完整身份事实。
- 新增 `SmartRedisLimiterTypedAuditHandler` 扩展点与默认日志实现（`typed-enabled` 控制，默认开）：SUCCESS/FAILURE 分级输出；Handler 异常隔离，不中断后续分发、不反写事件发布方。
- v1 执行事件审计行为不变。

## 升级注意

- 本模块 2.1.0 编译基于 Core 2.2.0（类型化事件与操作人摘要常量）；消费 Core 2.1.0 的宿主升级本模块需同步升 Core。
- 运行端 starter 2.2.0 起 route/AOP/连接池为自备依赖，本模块测试侧已显式自备，对宿主无新传递。

## 覆盖与兼容性

- 新增测试：受控摘要映射（含 operatorDigest 提取）、监听器分发、Handler 异常隔离。
- 全量 18/18（真实 Redis，含存量 v1 执行事件集成回归）。
