# simple-kafka-outbox-management-starter

Kafka Outbox 的人工排障页面 starter，提供状态查看、记录定位和单条毒消息重置。

## 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-outbox-management-starter:1.0.0'
```

它只依赖 `simple-kafka-outbox-core:1.0.0`，不依赖 runtime starter、Kafka client 或 worker。

## 快速接入

Management 必须作为独立的 Spring Boot Web 应用运行，并与 Runtime 配置到同一个 MySQL 数据库和同一张 Outbox 表。它只读取记录和受控重置 `POISON`，不发送 Kafka 消息，也不启动 Runtime worker。

在应用配置中启用 Management；管理员凭据使用环境变量或 gitignored 本地配置提供，不能提交真实凭据。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        messaging:
          kafka:
            outbox:
              management:
                enable: true
                table-name: simple_kafka_outbox
                ui:
                  base-path: /outbox-management
                admin:
                  username: ${KAFKA_OUTBOX_MANAGEMENT_ADMIN_USERNAME}
                  password: ${KAFKA_OUTBOX_MANAGEMENT_ADMIN_PASSWORD}
```

启动后访问 `${ui.base-path}/login`，使用配置的管理员账号登录。页面提供：

- 五种 Outbox 状态的数量总览。
- 按状态浏览记录，并使用游标加载更多。
- 按记录 ID 或 messageId 精确定位；messageId 查询自动忽略首尾空白。
- 不含消息内容的安全详情查看。
- 单条 `POISON -> PENDING` 受控重置。

重置仅接受带 CSRF 防护的 POST 请求，固定清除租约和错误、归零 attempt，后续由 Runtime worker 自然领取。

## 本地查看

仓库测试源码提供 `SimpleKafkaOutboxManagementTestApplication`，可用于连接本地隔离 MySQL 表查看页面。测试配置默认监听 `http://127.0.0.1:18081/outbox/login`；本地连接信息只放在 gitignored 的 `application-local.yml`、`application-local.yaml` 或 `application-local.properties`，不要提交真实凭据。

该测试应用不会创建或发送 Outbox 消息。需要查看记录时，使用已有 Runtime 记录或本地隔离表中的测试数据。

## 配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enable` | `false` | 启用 Management。 |
| `table-name` | `simple_kafka_outbox` | Runtime 使用的 Outbox 表名。 |
| `data-source-bean-name` | 空 | 目标 DataSource Bean 名；为空时必须唯一。 |
| `transaction-manager-bean-name` | 空 | 目标 DataSourceTransactionManager Bean 名；为空时必须唯一，且必须对应同一 DataSource。 |
| `page.default-size` | `20` | 状态列表默认条数。 |
| `page.max-size` | `100` | 状态列表最大条数。 |
| `ui.enable` | `true` | 注册页面能力。 |
| `ui.base-path` | `/outbox-management` | 管理页面根路径。 |
| `ui.redirect-root` | `true` | 将应用根路径重定向到 Management 页面。 |
| `admin.username` | 无 | 固定管理员用户名。 |
| `admin.password` | 无 | 固定管理员密码。 |

应用已有 `PasswordEncoder` Bean 时，Management 直接复用；不存在时才提供默认 delegating encoder。

## 部署边界

- Management 与 Runtime 是独立应用，不支持在同一个 Spring 应用中组合部署。
- 与 Core 组合 Runtime 时必须使用已适配 Core 的 Runtime `1.0.1+`；Runtime `1.0.0` 与 Core 有重复 FQN，不能进入同一 classpath。
- 不提供 REST API、批量操作、审计、worker 控制、Kafka 操作、消息内容查看或编辑。
- 页面查询不读取 payload、headers、attributes、owner token 或乐观锁版本。
