# 2.2.0

类型：功能升级（类型化限流契约，v2 增强第一批）。

## 变更

- 新增类型化计数维度（RESOURCE/IP/USER/SERVICE/CREDENTIAL/CUSTOMER/CUSTOM）、规则选择器（DEFAULT/EXACT）与服务控制模式（LEGACY_V1/TYPED_V2）三个枚举；CUSTOMER 是与 License 同源消费客户绑定事实的商业配额维度。
- 新增类型化规则键、规则与服务级快照模型：协议版本固定 "2"，快照携带 policyEpoch（策略代次，不小于 1）与单调 revision；组合约束——RESOURCE 仅 DEFAULT、DEFAULT 对象为 null、CUSTOM 必带 customType 而其他维度必须为 null；EXACT 对象保留原值（不 trim、不转大小写、不做 Unicode 归一化，最多 256 码点，禁控制字符）；规则限额窗口 1 至 16 个且窗口单位规范化后不重复；快照内规则键唯一、服务编码必须匹配，非法整份拒绝。
- 新增计数桶身份 Helper：`serviceCode + resourceCode + dimension + namespace + customType + 实际对象` 的版本化无歧义编码摘要（typed-v2 业务类型；每字段大端两字节长度前缀+原始 Java 字符单元；RESOURCE 使用固定共享对象保证同资源共桶）；ruleId、selector、revision、Token 与限额值不进入桶身份，修改额度或切换默认/覆盖不重置计数；未配对代理字符按原始字符单元保留，不因编码替换而合并。
- 新增操作人摘要 Helper：`resource:v1:sha256:<64位小写十六进制>` 固定 83 字符短摘要（resource-principal-v1 编码域，输入为 sourceId/subjectType/subjectId 三元组），配套事件 attributes 的 `operatorIdentity` 保留键与 schema 常量；摘要仅作稳定标识，不用于权限判断。
- 新增类型化管理事件与载荷：表达操作、服务、资源、维度、选择器、规则标识、revision、结果（SUCCESS/FAILURE）与受控原因；计数对象只记录命名空间受控摘要，不输出原始用户、客户、IP 或自定义 key；扩展属性沿用递归不可变快照。
- v1 契约零改动：旧模型、事件、常量、错误码与既有行为保持不变。

## 覆盖与兼容性

- 新增模型与 Helper 契约测试：字段组合约束、码点上限与补充平面、原值保留（含前后空白）、窗口数量与单位唯一、快照严格性（schema/代次/重复键/服务不匹配）、摘要确定性与区分度（长度前缀防拼接歧义、未配对代理、空白保留、RESOURCE 共桶）、操作人摘要 83 字符与三元组区分。
- 本版本为纯增量中立契约：不含 Redis、HTTP 或身份服务实现；限流运行端与 Management 在各自后续版本中按本契约消费，消费前不视为能力已交付。
