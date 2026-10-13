# 产品反馈-Feedback

统一门户环境下的通用产品反馈服务：任意已登记应用的门户用户提交反馈（满意度打分/文本/选填邮箱/截图），应用管理侧按 DATA 应用范围查看与处理并答复，提交人在"我的反馈"查看回复。纯人员通道（浏览器 PKCE）；机器 openapi 面与客户端三件套属首发边界外。

## 分层模块表

| 模块 | 坐标 | javax | 一句话 |
|---|---|---|---|
| 领域层 | `io.github.sure-zzzzzz:simple-feedback-server-core` | 1.0.0 | 模型/三态状态机/校验/错误码/出站 SPI（纯 Java 8） |
| 装配与 REST | `io.github.sure-zzzzzz:simple-feedback-server-starter` | 1.0.0 | 三表 MySQL+S3 附件+三权协作+幂等快照+双档频控，12 端点 |
| IAM 适配件 | `io.github.surezzzzzz:simple-feedback-iam-adaptor-starter` | 1.0.0 | hint 聚形态（iam-client 1.0.3 页面准入详情+邮箱预填+提交人展示名快照）与注册表目录核对（修正㉔） |
| Web | `github.com/Sure-Zzzzzz/simple-feedback-web` | — | 独立前端仓（四页面 qiankun 子应用，PKCE+契约组件） |

模块 README：[core](../../sdk/feedback/simple-feedback-server-core/README.md) / [starter](../../sdk/feedback/simple-feedback-server-starter/README.md) / [adaptor](../../sdk/feedback/simple-feedback-iam-adaptor-starter/README.md)；REST 契约 [openapi](../../sdk/feedback/contract/openapi/simple-feedback-server.openapi.yaml)。

## 依赖关系

- starter → `simple-feedback-server-core:1.0.0`（api 坐标，10-13 起 Central 解析）、`simple-application-authorization-core`+`simple-data-permission-core`（协议层最小集，均 api：宿主注解代码免声明即可编译）+`simple-resource-server-core`（compileOnly，桥在场权归宿主）、`simple-mysql-route-starter`+`s3-client-starter`（部署契约）；数据面 MVC 强制件由宿主自引。
- 协作装配件（`simple-resource-server-starter`+`simple-iam-resource-server-starter`）由宿主自引：引即全链验证 Bearer（聚），不引则宿主自备 `FeedbackPrincipalResolver`（散）；双缺席拒启。
- adaptor → `simple-iam-client-core`+`simple-iam-aksk-resttemplate-client-starter`（client-core≥1.0.3，"只引不改传输"）；宿主持 AKP 出站（`iam:user:api`+`iam:portal:api`+`iam:application:api` 三码，iam:user/iam:application DATA read all，不给 PAGE）。
- 被依赖：无（叶子业务服务）。

## 核心特性

- **三权**：8 API 码+4 PAGE 码+2 DATA 资源（`feedback:submission`/`feedback:application`，维度 application IN）；注册表创建同样受 DATA 范围约束，读列表范围进 SQL，范围外一律安全 404/空集。
- **幂等**：提交收 `Idempotency-Key`（前端表单会话级）；摘要覆盖主体+contextPath+附件元数据；并发同键唯一键收敛；首成功响应快照随行持久化，处理过后重放仍返回首成功形态。
- **边界**：同 owner 双档计数窗频控（DB 计数，不引 limiter）；附件开关/数量/单件/总量/类型全部走运行配置；分页上限截断生效。
- **聚散**：见依赖关系——附件、adaptor、协作件均可缺席成合法形态，由依赖表达。
- **数据来源与同源锁（修正㉔）**：注册表行级 `source` 徽标（IAM 同源/本表登记/未核对）；同源行展示名登记时自动抄录 IAM 官方名、编辑被拒（409），`syncFromIam=true` 显式重同步；受理门（启用/停用）与处理组始终本表自主（D6）。提交人 displayName 随行快照（聚形态经档案 SPI，降级回退裸 subjectId 展示）。

## 版本映射

| starter | core | adaptor | iam-client | Web |
|---|---|---|---|---|
| 1.0.0 | 1.0.0 | 1.0.0 | 1.0.3 | 1.0.0 |
