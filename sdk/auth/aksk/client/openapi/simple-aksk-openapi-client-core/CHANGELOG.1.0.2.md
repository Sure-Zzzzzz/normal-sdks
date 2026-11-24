# 1.0.2

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：1.0.1

## 变更

- 新增 `CONFIG_PREFIX`：管理 OpenAPI 配置前缀 `io.github.surezzzzzz.sdk.auth.aksk.openapi.client`。
- 新增 `AKSK_PROFILE_CONFIG_KEY`：`aksk-profile` 配置键完整形态，供四个传输件 starter 读取管理 AKP 选择。
- 新增 `DEFAULT_PROFILE`：默认 Profile 名称常量。

## 兼容性

- 契约方法、DTO、异常族零变化；1.0.x 消费方升级零影响。
- 三个常量为纯增量，配套管理 OpenAPI 传输件 starter 1.1.0 的 `aksk-profile` 选择。
