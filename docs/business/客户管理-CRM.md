# 客户管理-CRM

客户建档、商机推进、报价签发与确认领域的服务端核心：幂等可重放的结果契约。

## 分层结构

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-crm-server-core](../../sdk/crm/simple-crm-server-core) | 2.1.0 | — | CRM 领域核心（商机销售管线/报价六态/履约状态机/幂等可重放） | [README](../../sdk/crm/simple-crm-server-core/README.md) |
| [simple-crm-server-starter](../../sdk/crm/simple-crm-server-starter) | 1.1.0 | — | RESTful v2.1 服务（商机、PATCH 动作分派，OpenAPI v2.1.0 冻结于 contract/openapi） | [README](../../sdk/crm/simple-crm-server-starter/README.md) |
| [simple-crm-client-core](../../sdk/crm/client/simple-crm-client-core) | 1.1.0 | — | 客户端契约 | [README](../../sdk/crm/client/simple-crm-client-core/README.md) |
| [simple-crm-aksk-resttemplate-client-starter](../../sdk/crm/client/simple-crm-aksk-resttemplate-client-starter) | 1.1.0 | 1.1.0 | RestTemplate 客户端 | [README](../../sdk/crm/client/simple-crm-aksk-resttemplate-client-starter/README.md) |
| [simple-crm-aksk-feign-client-starter](../../sdk/crm/client/simple-crm-aksk-feign-client-starter) | 1.1.0 | 1.1.0 | Feign 客户端 | [README](../../sdk/crm/client/simple-crm-aksk-feign-client-starter/README.md) |
| [simple-crm-audit-listener-starter](../../sdk/audit/crm/simple-crm-audit-listener-starter) | 1.0.0 | — | 审计事件监听 | [README](../../sdk/audit/crm/simple-crm-audit-listener-starter/README.md) |
