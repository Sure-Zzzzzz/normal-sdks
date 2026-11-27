# CHANGELOG - simple-aksk-server-starter 3.2.3

## 发布信息

- 版本：`3.2.3`
- 日期：`2026-11-27`
- 类型：Patch / OAuth2Authorization L2 缓存反序列化修复
- 基线版本：`3.2.2`

## 版本定位

SmartCache `2.2.0` 写入的授权对象 JSON 含 Spring Security 多态结构，读侧 allowlist 不信任 `OAuth2Authorization` 顶层类型，导致 `oauth2:authorization` 和 `oauth2:authorization:token` 命中 L2 后反序列化失败、被迫回源。3.2.3 为授权对象建立专用、可校验的载荷，并保持其他 SmartCache 载荷格式不变。

## 主要变更

- 新增 `AuthorizationSmartCacheCodec`：`OAuth2Authorization` 使用版本化 JSON 外壳封装授权字节，读取时校验版本、类型和 Java 反序列化类名白名单。
- 新增组合 `SmartCacheSerializer` 自动配置：只拦截授权对象；其他缓存值继续走 SmartCache 默认 JSON 信封，避免影响 Token、Client 等既有格式。
- `OAuth2Authorization` 缓存完整保留 token、metadata 和 attribute；读回对象不再触发 allowlist 失败。
- Redis Token 管理扫描复用同一授权解码器，可直接从 3.2.3 载荷统计 token；旧 3.2.2 授权 JSON 载荷识别后失效回源。
- 历史裸 Java 序列化授权缓存同步接入同一类名白名单，白名单外类型拒绝并回源。
- `spring.factories` 在 SmartCache 自动配置前登记组合序列化器；不暴露 `ObjectMapper` Bean，不影响宿主 MVC JSON。

## 依赖与数据库

- `smart-cache-starter` 升级到 `2.2.1`。
- 无 Core 版本变更、无 SQL、无表结构变化、无数据回填或清理要求。
- 无 HTTP 路径、请求参数、响应体、响应头或配置键变化。

## 回归验证

- 授权对象专用载荷往返：对象 ID、principal、grant type、registered client 与 authorities 完整回读。
- 非授权值继续走 SmartCache 默认 `schemaVersion:1` JSON 载荷。
- 旧授权 JSON 载荷不再还原为不完整对象，而是序列化失败并回源。
- 管理 Redis Token 过滤、历史 Token 统计与删除 Client 后的历史记录链路通过 `ManagementDataAccessIntegrationTest`。
- Server Starter 全量 `239` 个测试通过，JUnit XML 核对 `0` 失败、`0` 错误。

## 向后兼容性

- 升级后新写入的授权缓存使用 3.2.3 载荷；升级瞬间残留的 3.2.2 授权 JSON 载荷失效回源，不需要人工清缓存。
- 其他 SmartCache 载荷格式不变；AKSK Client Token Manager 的旧 token 载荷不受本模块组合序列化器影响。
- 自定义 `SmartCacheSerializer` Bean 仍可整体覆盖本默认装配。
