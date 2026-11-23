# 3.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：3.0.2

## 变更

- 升级 `simple-aksk-client-core:3.1.0`；自动配置创建 `AkskClientProfileRegistry` Bean，启动期完成全部 AKP Profile 校验。
- `RedisTokenManager` 实现 `ProfileTokenManager`：`getToken(profile)` 按命名 AKP 换取 Token，`clearToken(profile)` 只清理该 Profile 在当前安全上下文下的缓存；请求未配置的 Profile 抛出 `ConfigurationException`，不回落到默认凭据。
- Token 缓存 key 增加 Profile 与凭据身份维度；命名 Profile 的 L1/L2 缓存、分布式锁、预刷新和清理互相隔离。
- `TokenWithExpiry` 新增 `profile` 与 `credentialHash` 元数据，保留旧三参构造器。
- 预刷新按缓存值中的 Profile 与凭据身份选择凭据；命名 Profile 的缓存值元数据与当前配置不一致时抛出配置异常。
- 默认端口、协议大小写和 AKSK Server 尾部斜杠按同一凭据身份归一化；同一 Server 与同一 `clientId` 重复配置会在启动期失败。
- Token 获取、刷新与清理的诊断日志携带 Profile 维度。

## 兼容性

- 默认单 AKP 的调用接口和 Redis key 保持不变。
- 只使用命名 Profile 时，顶层凭据可以缺省；此时调用无参 `getToken()` 抛出配置异常。
- Secret 不进入日志、Redis key、缓存值或异常消息。
- 旧版本写入的无元数据缓存值按未命中处理：默认链路刷新后补写元数据；等待 L2 的请求在等待超时后改用当前实例的锁刷新。
- 组件装配：`RedisTokenManager` 的 `@Autowired` 构造器由 6 参改为 7 参，必须注入 `AkskClientProfileRegistry`。自行排除自动配置又扫描该组件的宿主会在启动期失败，不再静默退化为默认凭据；手工构造用的 6 参构造器保留。
