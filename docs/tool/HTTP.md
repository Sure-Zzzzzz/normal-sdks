# HTTP 工具（xff 请求事实捕获）

HTTP 侧审计基础设施：XFF（X-Forwarded-For）事件契约、Servlet 零侵入采集与审计落库链。网络地址不冒充认证身份。

## 组件

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-xff-capture-core](../../sdk/http/xff/simple-xff-capture-core) | 1.1.1 | — | 纯 JDK 事件契约、不可变请求数据快照与 IP 分类 | [README](../../sdk/http/xff/simple-xff-capture-core/README.md) |
| [simple-xff-capture-starter](../../sdk/http/xff/simple-xff-capture-starter) | 1.1.2 | 1.0.0 | Servlet Filter 零侵入采集完整 XFF 事实链并发布事件；默认关闭的入口诊断 DEBUG | [README](../../sdk/http/xff/simple-xff-capture-starter/README.md) |
| [simple-xff-capture-audit-core](../../sdk/audit/http/xff/simple-xff-capture-audit-core) | 1.1.1 | — | 审计不可变文档、请求数据投影与 Persistence Provider SPI | [README](../../sdk/audit/http/xff/simple-xff-capture-audit-core/README.md) |
| [simple-xff-capture-audit-listener-starter](../../sdk/audit/http/xff/simple-xff-capture-audit-listener-starter) | 1.1.1 | 1.0.0 | Capture 事件监听、快照与多 Provider 广播 | [README](../../sdk/audit/http/xff/simple-xff-capture-audit-listener-starter/README.md) |
| [simple-xff-capture-audit-es-persistence-provider-starter](../../sdk/audit/http/xff/simple-xff-capture-audit-es-persistence-provider-starter) | 1.1.1 | — | 可选 ES 审计投影 Provider（依赖 [Elasticsearch 工具链](../middleware/Elasticsearch.md)） | [README](../../sdk/audit/http/xff/simple-xff-capture-audit-es-persistence-provider-starter/README.md) |

## 依赖关系

- capture-starter 传递 capture-core；ES 投影经 provider 传递 elasticsearch-persistence-starter（业务方无需重复声明）
- 接入 ES 审计时，同一行版本组合使用（见下表）

## 版本映射（唯一事实源）

| capture-starter | capture-core | audit-core | audit-listener | es-provider | es-persistence-starter |
|-----------------|--------------|------------|----------------|-------------|------------------------|
| 1.1.2 | 1.1.1 | 1.1.1 | 1.1.1 | 1.1.1 | 1.1.1 |
| 1.1.1 / 1.1.0 | 1.1.0 | 1.1.0 | 1.1.0 | 1.1.0 | 1.1.1 |
| 1.0.1 / 1.0.0 | 1.0.0 | 1.0.0 | 1.0.0 | 1.0.0 | 1.1.1 |
