# Management 1.0.0 升级 2.0.0

1. 备份策略库与宿主配置。三张既有表结构不变；使用多维类型化规则（/v2/policy/**）的部署须补执行 docs/mysql-schema.sql 增建 smart_redis_limiter_typed_rule 与 smart_redis_limiter_typed_rule_limit 两表（幂等建表，既有数据不重写）；不使用类型化规则的部署可不执行。
2. 宿主显式提供 Web、Security、JDBC、数据源和事务管理。保留 Console 页面时补 Thymeleaf；旧 scope 对外 API 补旧 Provider 2.0.1、AOP 和 OAuth2 Resource Server/Jose，依赖示例见 README 的 Console 接入。2.0.0 不再代带这些可选运行件。
3. Console 设 mode=console，旧 UI/Session、scope 及显式固定 token 配置不变。仅关闭 UI 不代表 Portal。
4. Portal 设 mode=portal、api.enable=true、ui.enable=false，删除固定 token。显式提供公共 Resource 1.1.1 和人员/机器两个适配器，保护完整 API 路径并启用精确 API 的 MVC 校验；该资源链的 permit-all-paths 必须为空，公开入口用独立链。
5. 给调用方授予实际需要的 API 权限及 limiter-policy 的 serviceCode DATA read/write。页面用户另授 PAGE；API 不要求 PAGE，写权限不隐式授予读权限。
6. 更新自定义 Repository/Service 的范围重载，使列表和 count 一致、主键写入带范围。不得以 null 或忽略参数表示全量。
7. 停旧入口后启动新入口，验证匿名 401、越权 403/404、只读权限、跨服务写拒绝、304 前授权及事件提交后发布。不要让两套独立管理部署并行写同一策略库。

回滚：停止新入口，恢复 Console 宿主与配置，保留数据。普通管理事件仍遵循 Core 2.1.0 载荷，包含策略键与前后策略；外部存储或转发须由受控消费者脱敏，不承诺可靠审计投递。
