# 2.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：见同仓上一版 CHANGELOG

## 变更

- 对接 AKSK Client 多 AKP Profile。
- 新增 `io.github.surezzzzzz.sdk.kms.client.aksk-profile` 配置键；空值或 `default` 继续使用默认 AKP。
- 认证头仍只注入一个；默认业务契约、Bean 行为和错误口径不变。

## 兼容性

- 不配置新键时，原有调用方式不变。
- 命名 Profile 是静态配置，修改后重启生效。
