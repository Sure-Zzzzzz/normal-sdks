# CHANGELOG - simple-aksk-server-core 3.0.4

## 发布信息

- 版本：`3.0.4`
- 类型：Patch / 配置归位与措辞中性化
- 基线版本：`3.0.3`

## 版本定位

聚散原则收官：身份源专属的连接配置（endpoint、SERVICE 凭据、超时、重试）归协作适配器模块承载，本模块只保留与厂商无关的投影与租约策略；同时将 OWNER_INHERITED 相关说明措辞从"IAM"统一为协作 SPI 的正式术语"身份源"。

## 主要变更

- 配置块改名与瘦身：`IamOwnerAuthorizationReaderConfig` → `OwnerAuthorizationProjectionConfig`。保留开关、身份源标识（`ownerSourceId`）、HUMAN 认证来源标识（`humanResourceSourceId`，默认值 `iam` → `human`）、同步模式、拉取间隔与本地授权租约；移除 tokenUri、baseUri、clientId、clientSecret、连接/读取超时与重试次数（由协作适配器自带）。
- 措辞中性化：`SimpleAkskServerProperties`、`AkskOwnerAuthorizationSynchronizationMode`、`SimpleAkskServerConstant` 中 OWNER_INHERITED 相关 javadoc 与描述统一为"身份源"。
- 新增 `SimpleAkskServerConstant.OWNER_SUBJECT_ID_MAX_LENGTH = 128`：身份源稳定主体 subjectId 在 AKSK owner binding 中的最大长度契约。

## 配置迁移

- 配置键前缀 `...iam-owner-authorization-reader.*` → `...owner-authorization.*`；被移除字段的配置位置见所用协作适配器的文档。
- 3.0.3 的 reader 配置没有已发布消费方（协作适配器尚未发布，`simple-aksk-server-starter` 3.2.0 才首次启用该链路），本次改名对外零影响，按 patch 发布。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
| --- | --- | --- |
| `simple-aksk-core` | `3.0.1` | `3.0.2` |

`simple-aksk-core` 3.0.2 为协议零变化的措辞中性化 patch，见其 CHANGELOG.3.0.2.md。

## 新增或扩展测试

- `SimpleAkskServerPropertiesTest` 新增 `OwnerAuthorizationProjectionConfig` 默认值断言（默认关闭、身份源标识空、HUMAN 来源默认 `human`、EVENTUAL_WITH_LEASE、1000ms 拉取间隔、30s 租约）。
- 新增反证断言：`IamOwnerAuthorizationReaderConfig` 嵌套类与其 getter 不再存在（身份源连接配置不得回流本模块）。
