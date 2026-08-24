# smart-middleware-ops-server-starter 1.1.1

发布日期：2026-08-24

类型：Feature（易用性增强）

## 本次发布

MySQL 受控 SELECT 与 Explain 控制台输入新增浏览器内 SQL 联想，帮助操作者更快写出符合服务端白名单的单表只读语句。本版本不改变 HTTP 契约、审计边界、服务端校验规则或 Route 所有权。

## MySQL 控制台增强

- 受控 SELECT/Explain 输入框基于操作者已显式加载的当前数据源表候选和精确表字段候选，在浏览器内提供表名、字段名和固定关键字集合的联想；联想只在已加载对应目录后才出现，输入、移动光标和切换工作区不会自动触发表或字段目录请求。
- 联想关键字集合（`SELECT`/`FROM`/`WHERE`/`AND`/`OR`/`LIKE`/`IN`/`BETWEEN`/`IS NULL`/`IS NOT NULL`/`ORDER BY`/`ASC`/`DESC`）与服务端 `MysqlControlledSelectPolicy` AST 白名单一一对应；不提供 `LIMIT`、聚合函数、JOIN 或其他服务端会拒绝的关键字。
- 候选列表固定最多 10 项，按输入前缀过滤；表候选、字段候选和关键字候选按当前光标上下文合并去重后展示。
- 字段候选区新增“全选字段”/“取消全选”操作，用于快速填入受控 SELECT 的多字段草稿。
- 联想候选不持久化：切换数据源、切换工作区、登出或会话失效时随既有目录清理逻辑一并清除，不写入浏览器 `localStorage` 草稿。
- 联想仅辅助编写 SQL，不执行 SQL、不扩大服务端白名单、不替代服务端 AST 校验；最终结果仍以服务端 `MysqlControlledSelectPolicy` 校验为准。

## 验证

- 在 Spring Boot `2.7.9`、Java 11、Gradle 8.5 基线执行完整 Starter 测试，确认既有 LDAP 认证、四类中间件受控观察与审计脱敏行为未受影响。
- 使用本机 Chrome 通道的 Playwright CLI 完成真实登录后的 MySQL 控制台 SQL 联想交互验收。

## 向后兼容性

- 本版本仅新增前端交互，不改变任何已发布 HTTP 路径、请求参数、响应 JSON 字段、状态码、配置前缀或 Spring 自动配置注册。
- Elasticsearch、Redis、Kafka、MySQL Route 边界与 1.1.0 保持一致。

## 升级指南

1. 将依赖版本从 `1.1.0` 升级到 `1.1.1`。
2. 无需修改配置或 Java 代码；升级后刷新页面即可使用 MySQL 控制台的 SQL 联想。
