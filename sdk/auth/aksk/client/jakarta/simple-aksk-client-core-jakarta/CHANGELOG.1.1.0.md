# 1.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：1.0.0

## 变更

- 新增 `ProfileTokenManager` 契约，支持 `getToken(profile)`、`clearToken(profile)` 和 `containsProfile(profile)`。
- 新增 `AkskClientProfile` 运行时模型与 `AkskClientProfileRegistry` 只读注册表。
- `SimpleAkskClientCoreProperties` 新增 `profiles` 映射；顶层配置继续表示默认 Profile。
- 启动期校验 Profile 名称、必需凭据、控制字符、保留名和重复凭据身份；默认端口、协议大小写与尾部斜杠按同一凭据身份归一化。
- Secret 不参与 `toString()`，也不写入日志、缓存元数据或异常消息。

## 兼容性

- 旧单 AKP 配置继续通过 `TokenManager.getToken()` 使用顶层凭据，调用方式与 Redis key 不变。
- 只使用命名 Profile 时，顶层凭据可以缺省；此时调用旧 `getToken()` 会返回明确的配置错误。
- Profile 是静态配置，修改后重启生效。
