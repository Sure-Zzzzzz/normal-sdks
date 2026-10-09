# 2.3.0

类型：契约归属调整（目录 SPI 下沉）。

## 变更

- 类型化目录 SPI 三件自 `smart-redis-limiter-management-starter` 迁入本模块，新包
  `io.github.surezzzzzz.sdk.limiter.redis.smart.directory`：
  - `SmartRedisLimiterDirectoryProvider`（目录提供方接口：服务清单 / 单服务声明 / 按维度检索对象目录）；
  - `SmartRedisLimiterServiceDeclaration`（服务目录声明：控制模式、资源与维度、命名空间、自定义类型）；
  - `SmartRedisLimiterDirectoryObject`（对象目录条目：稳定 ID + 展示名称）。
- 三件为原样平移（除包声明外无任何逻辑改动）；目录 SPI 描述的是服务与对象的接入事实，
  与管理端实现无关，归属契约层后扩展件（如 IAM 用户目录适配件）只依赖 core 即可实现，
  不再拖入管理面。
- 配置式默认实现 `ConfigurationSmartRedisLimiterDirectoryProvider` 仍留在 management-starter
  （其数据源是 management 部署配置，属实现不属契约）。
- core 其余契约（v1/v2 策略、事件、Helper、常量）零改动。

## 覆盖与兼容性

- 目录模型行为由消费方测试覆盖：management-starter 2.1.0（同批迁移 import）全量测试与
  smart-redis-limiter-management-iam-directory-starter（SPI 首个第三方实现）单测。
- 编译兼容性：直接 import 旧包 `...smart.management.directory` 下三类的代码需改 import 到
  新包；HTTP 契约与运行行为不变，按固定坐标使用已发布 2.2.0 及更早版本的消费方不受影响。
