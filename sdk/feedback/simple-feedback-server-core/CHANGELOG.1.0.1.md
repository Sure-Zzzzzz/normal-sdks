# simple-feedback-server-core 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / 领域错误码与异常契约补齐

- 新增协作装配不完整的配置错误码与 `FeedbackConfigurationException`，供服务端在启动期拒绝默认资源认证桥缺少有效认证链的组合。
- 新增必填字段缺失、请求解析失败错误码，供 REST 层区分无效请求与并发冲突。
- 新增未认证和主体不允许的安全消息常量，供 REST 层区分 401 与 403。
- 新增重复登记异常类型，沿用既有 `APPLICATION_ALREADY_REGISTERED` 错误码；原有公开类型和错误码保持兼容。
- 新增幂等键 64 字符上限常量，与提交表字段长度一致。
- 新增按调用方策略校验附件规格的 `FeedbackAttachment.validateSpecs(...)` 重载；无参数策略的原入口继续使用默认值，starter 的运行时附件配置可完整贯穿数量、单件、总量和媒体类型校验。
- 附件规格校验拒绝负数大小、空规格和总量累加溢出，避免非法输入绕过总量上限。

## 依赖升级

| 依赖 | 1.0.1 变化 |
|---|---|
| Spring、HTTP、存储 | 无；本模块继续保持纯 Java 8、零运行时依赖 |

## 新增测试

- `FeedbackApplicationAndAttachmentTest`：覆盖注册表模型校验和按调用方策略校验附件规格。

## 向后兼容性

- 保留原有公开模型、错误码和无参数 `validateSpecs()` 入口。
- 新增错误码和异常类型不改变已有合法请求的校验结果。

## 升级指南

服务端宿主将 `simple-feedback-server-core` 与 `simple-feedback-server-starter` 一并升级到 1.0.1；无需修改数据库结构。调用方若只使用 core，可直接替换为 1.0.1 坐标。
