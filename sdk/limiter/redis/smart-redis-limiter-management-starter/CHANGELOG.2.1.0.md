# 2.1.0

类型：契约归属调整（目录 SPI 下沉至 core）+ 依赖升级。

## 变更

- 类型化目录 SPI 三件（`SmartRedisLimiterDirectoryProvider` /
  `SmartRedisLimiterServiceDeclaration` / `SmartRedisLimiterDirectoryObject`）自本模块迁出，
  由 `smart-redis-limiter-core:2.3.0` 持有（新包
  `io.github.surezzzzzz.sdk.limiter.redis.smart.directory`）；本模块内部引用（装配、类型化服务、
  Portal 控制器与测试）同步切换 import。
- 配置式默认实现 `ConfigurationSmartRedisLimiterDirectoryProvider` 留在本模块
  （数据源是 `management.typed.services` 部署配置，属实现不属契约），默认装配与
  宿主自有 Bean 覆盖语义不变。
- 依赖 `smart-redis-limiter-core` 2.2.0 → 2.3.0；其余依赖不变。
- 契约下沉后扩展件实现目录提供方只需依赖 core，不再拖入管理面实现
  （首个第三方实现：`smart-redis-limiter-management-iam-directory-starter`，USER 维度经
  IAM openapi 实时检索）。

## 覆盖与兼容性

- 本模块全量测试以固定坐标形态（Core 2.3.0 取自 Maven Central）`--rerun-tasks` 重跑，
  67 用例全部通过；配置式目录默认实现行为由既有用例覆盖，无逻辑改动。
- 编译兼容性：直接 import 旧包 `...smart.management.directory` 下三类的自定义代码需把
  import 改到新包；配置项、HTTP 契约与运行行为不变，按固定坐标使用已发布 2.0.0 及更早
  版本的部署不受影响。
- 无数据库结构迁移；2.0.0 部署升级仅需替换坐标版本。
