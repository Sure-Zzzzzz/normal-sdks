# Simple Ticket Server Core

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

统一工单平台的领域内核：聚合、状态机、SPI（`spi/` 包）、错误码与 DTO 契约。零 Spring Web 依赖，javax（SB2/JDK8）与 jakarta（SB3/JDK17+）双宿主通用。

## 定位

`simple-ticket-*` 家族的契约层：**core 是契约、starter 是装配件**（依赖分层规矩）。调用方对装配件零编译依赖——要类型引本 core，本 core 没有的（如配置 Properties）用反射探测。

## 核心能力

### 1. 工单聚合（`model/Ticket`）

- 富领域方法承载全部状态机动作（claim/assign/transfer/request-info/supply/resolve/confirm/reject/withdraw/confirmTimeout），非法转移抛 `TicketStateException`（409）
- 五种主流程状态：`SUBMITTED → PROCESSING ⇄ PENDING_INFO → RESOLVED → CLOSED`（另有 `WITHDRAWN` 终态）
- SLA 暂停账唯一口径：`办结到期判定 = now > resolutionDeadline + 暂停累计`；`PENDING_INFO` 期间恒暂缓（`effectiveResolutionDeadline` 随 now 同速延伸）
- 人员快照（ownerName/assigneeName）受理/分派时固化，离职不追改

### 2. 状态机表（`statemachine/TicketStateMachine`）

动作 × 前置状态集 × 目标状态的纯函数表；与 `Ticket` 富方法**双向对齐**（测试断言逐格一致）。`COMMENT` 不改状态不在此表。

### 3. SPI（`spi/`，feedback 同构）

| SPI | 职责 | Noop 默认 | 聚形态适配 |
| --- | --- | --- | --- |
| `NotificationProvider` | 站内信投递（十类通知点） | 日志降级 | IAM 站内信（simple-ticket-iam-adaptor-starter） |
| `FileProvider` | 附件预签名/校验/清理 | 禁用附件 | S3（server-starter 内建适配） |
| `SubmitterProfileProvider` | subjectId → 姓名解析 | 显示 subjectId | IAM 用户查询（同上 adaptor） |

实现规则：Noop 经配置类 `@ConditionalOnMissingBean` 注册（**不经组件扫描**——扫描件上的条件注解对同类 Bean 缺席判断不可靠）；真实现提供时整体让位。

### 4. 错误码与异常族

- `ErrorCode`/`ErrorMessage` 一一对应 13 对（形状测试锁定数量，含统一内部处理失败映射）
- HTTP 语义：404 掩蔽越权（`TICKET_NOT_ACCESSIBLE`）/ 403 角色不符 / 409 状态冲突 / 400 校验失败
- 错误响应只返回安全的 `{message, time}`；具体失败类别由 HTTP 状态码表达，内部错误码不对外暴露

### 5. DTO 契约（`dto/`）

三面 API 的请求/响应类型（`TicketSummaryResponse`、`TicketDetailResponse`、`PageResponse`、`TimelineResponse` 等 13 类）+ `TicketResponseMapper`（时间统一 ISO-8601 字符串）。openapi 客户端三件套从此派生。

## 依赖说明

- **零依赖**：build.gradle 为空（Jackson 由使用方提供，`TicketJson` 不在本模块）
- Java 8 字节码；lombok 编译期

## 快速上手（消费方）

```gradle
implementation 'io.github.sure-zzzzzz:simple-ticket-server-starter:1.0.1'  // 宿主装配
// 编译期需要 core 类型时（一般经 starter api 传递，无需显式声明）：
// compileOnly 'io.github.sure-zzzzzz:simple-ticket-server-core:1.0.0'
```

状态机直用示例（测试/工具场景）：

```java
Ticket ticket = Ticket.newTicket("TK261014000001", "fault", "标题", "描述", "{}",
        TicketPriority.NORMAL, "user-1", "用户一", TicketSource.PORTAL,
        null, null, null, 60, 480, Instant.now());
ticket.claim("grp-ops", "agent-1", "处理人", Instant.now());   // SUBMITTED → PROCESSING
ticket.requestInfo(RequestInfoReason.AWAIT_MATERIAL, Instant.now()); // 计时暂停
```

## 设计文档

完整设计（状态机全枚举、三权登记、部署形态、完备性矩阵）见 [家族设计文档](../DESIGN.1.0.0.md)；审阅台账见 [1.0.0 审阅台账](../LOCAL_REVIEW_50_1.0.0.md)。
