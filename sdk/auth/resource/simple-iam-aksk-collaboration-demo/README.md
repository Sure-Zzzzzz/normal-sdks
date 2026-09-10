# Simple IAM AKSK Collaboration Demo

业务资源服务同时接受 IAM 的人员身份和 AKSK 的服务身份，访问同一个真实业务边界（orders 订单表）。它是独立 Resource Consumer：自己启动、自己的业务库、自己的 YAML，不嵌入、不代管 IAM Server 或 AKSK Server。

> 本模块不发布到 Maven Central，仅作协作验收参考，不是生产部署模板。

```text
IAM Server(:8180 起，可多实例) ──受控验证──┐
                                           ├── Resource Demo(:8090) ── GET/POST/DELETE /api/orders
AKSK Server(:8280，独立闭环) ──认证内省───┘
```

IAM Server 支持分布式多实例部署（共享 MySQL 与 Redis、无粘性路由前提）：资源端的 `verification-endpoint` 指向任意实例或入口负载地址行为一致——token 校验走共享存储，不依赖实例亲和。多实例启动方式见 `sdk/auth/iam/server/simple-iam-server-starter/LOCAL_TEST_COMMANDS.md` 的「多实例手工验收」一节。

设计基线见 [DESIGN.IAM-AKSK-RESOURCE-COLLABORATION.md](DESIGN.IAM-AKSK-RESOURCE-COLLABORATION.md)。

## 接入

四个 Starter 显式并列引入，全部使用已发布固定坐标。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
    implementation project(':sdk:auth:iam:resource:simple-iam-resource-server-starter')
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.0.1'
    implementation 'io.github.sure-zzzzzz:simple-data-permission-spring-mvc-starter:1.0.1'
}
```

Provider 不传递公共层：AKSK Resource Server 3.0 与 IAM Resource Starter 均只传契约模块（resource-server-core），公共 Starter 必须由宿主显式声明，版本由宿主自己控制。

## 业务与 DATA 边界

orders 表自带 `tenant_id`、`department_id` 两个授权维度列。四种操作全部执行 `DataAccessPlan`：

| 接口 | API permission | DATA 行为 |
| --- | --- | --- |
| `GET /api/orders` | `order.read` | 计划转真实 WHERE 条件，只返回授权范围内真实结果集 |
| `GET /api/orders/{id}` | `order.read` | 计划 + id 联合查询，越权一律 `404`，不泄露存在性 |
| `POST /api/orders` | `order.write` | 目标维度必须落在计划范围内，越界 `403` |
| `DELETE /api/orders/{id}` | `order.write` | 计划 + id 联合查询，越权一律 `404` |

同一 grant 内约束 AND，跨 grant OR；未知维度、非 IN 操作符、无法完整执行的计划一律失败关闭。缺 DATA 授权时 `403`。

Controller 不读取身份来源、Token payload，不判断"这是 IAM 还是 AKSK"；IAM 人员身份与 AKSK 服务身份进入同一个 Controller、Service、Repository 与数据表。

## 安全边界

| 场景 | 结果 |
| --- | --- |
| 受保护路径缺失、畸形或未知来源凭据 | `401` |
| 被选 Provider 拒绝凭据 | `401`，不调用另一 Provider |
| 认证成功但缺少 API permission | `403` |
| API 权限在但 DATA 计划为拒绝 | `403` |
| `/public/**` | 公开，不采集 Bearer 凭据 |

公共资源层只读 compact token 外层 JOSE header 的 `kid` 前缀路由：`iam/` 选 IAM，`aksk/` 选 AKSK。`kid` 不替代完整验证；认证失败不 fallback、不合并权限。角色、PAGE、scope、URL、HTTP method、Controller 名称都不能授予 API/DATA 权限。

## 配置

复制 `src/test/resources/application.yml` 的结构到运行环境，或直接使用 `src/main/resources/application.yml` 的环境变量占位：

- `RESOURCE_DB_PASSWORD`：业务库 `iam_aksk_resource` 密码（首次执行 `docs/schema.sql` 建库建表）
- `IAM_RESOURCE_VERIFICATION_ENDPOINT` / `IAM_RESOURCE_CLIENT_ID` / `IAM_RESOURCE_CLIENT_SECRET`
- `AKSK_INTROSPECT_ENDPOINT` / `AKSK_INTROSPECT_CLIENT_ID` / `AKSK_INTROSPECT_CLIENT_SECRET`

Resource 配置禁止包含 IAM/AKSK 的数据库、Redis、内部密钥或 schema 初始化内容；AKSK local cache 与 stale fallback 保持关闭。

## 测试分层

- `src/test` 集成回归（`IamAkskCollaborationDemoApplicationTest`）：Mock 身份 + 真实 MySQL 测试库，覆盖双身份同一业务链、真实结果集裁剪、跨 grant OR、越界 403/404、缺 DATA 授权 403、无凭据/未知来源 401。
- `OrderDataAccessPlanConverterTest`：计划转换单元测试（失败关闭、ALLOW_ALL、DENY）。
- `IamAkskCollaborationHttpTest`：外部编排验收，读 `IAM_BASE_URL`/`AKSK_BASE_URL`/`RESOURCE_BASE_URL` 环境变量，只测已启动服务，不构建、不启动、不清理任何服务；由独立 `collaborationAcceptance` 任务运行，环境变量缺失或服务不可达直接失败，不做静默跳过，日常 `test` 任务不含它。

协作验收不能替代 IAM 与 AKSK 各自闭环测试；三个服务必须按各自 local test 入口独立启动。
