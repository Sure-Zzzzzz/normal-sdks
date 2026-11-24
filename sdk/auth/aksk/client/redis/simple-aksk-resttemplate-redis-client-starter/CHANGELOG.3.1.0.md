# 3.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：3.0.2

## 变更

- 升级 `simple-aksk-redis-token-manager:3.1.0` 和 `simple-aksk-client-core:3.1.0`。
- 新增 `AkskProfileRestTemplateFactory` 与 `AkskRestTemplateResolver`，按命名 AKP Profile 创建和复用受保护 RestTemplate。
- 空 Profile 或 `default` 继续使用顶层 `akskClientRestTemplate`；命名 Profile 模板只注入一个认证拦截器。
- 同名 Profile 复用模板和 HTTP 连接工厂；Profile 不存在时启动或解析失败。

## 兼容性

- 默认 AKP 配置、Bean 名称和调用方式不变。
- Profile-only 配置不创建默认模板；显式开启默认模板但默认凭据缺失时启动失败。
