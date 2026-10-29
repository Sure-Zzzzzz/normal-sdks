# CHANGELOG 1.1.0

## 发布信息

- 类型：Feature
- 对接服务：`simple-crm-server-starter:1.1.0`
- HTTP 契约：OpenAPI v2.1.0

## 变更内容

- 新增商机创建、点查、列表、阶段推进/输单和归属转移方法。
- 报价创建与重议方法支持可选 `opportunityId`，响应字段白名单同步更新。
- 保持机器客户端的游标分页方法与管理台页码分页的边界，不在客户端混用两种响应。
- RestTemplate 与 Feign 的服务地址均由宿主通过 `base-url` 显式提供，发布制品不再内置本机地址。

## 兼容性

- 纯 Java 契约层仍不依赖 Spring、HTTP 客户端或认证实现。
- Jackson 继续使用 `compileOnly`，调用方直接使用编解码器时自行提供运行时实现。
