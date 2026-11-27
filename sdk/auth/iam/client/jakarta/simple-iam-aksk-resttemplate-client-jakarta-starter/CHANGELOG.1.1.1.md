# 1.1.1

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：`1.1.0`

## 变更

- 目标应用 manifest 解析 `dataResources`，与 SB2 RestTemplate 形态同标。
- IAM Server 旧响应缺字段时保持 `null`，不伪装为空申报。

## 兼容性

- Profile、认证、hc5 连接池、错误透传和 49 方法契约不变。
- 依赖 `simple-iam-client-core:1.0.5`。

## 验证

- Java 17 全量测试 13 项；0 失败、0 错误、0 跳过。覆盖与 SB2 同标的 manifest DATA 声明和缺失申报语义。
