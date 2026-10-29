# simple-crm-client-core

CRM 机器客户端契约层：`CrmClient` 接口提供 CRM Server 公开 HTTP 契约的类型化方法面、命令元数据、wire（网络传输）结果模型、响应校验器和错误分类。纯 Java 实现，不依赖 Spring、HTTP 客户端或任何认证组件；javax 与 Jakarta 两条传输线共用本模块的同一份契约。

适合在服务端以编程方式调用 CRM 的商业事实接口（客户、目录、报价、订单、履约闭环）。如果你只需要在浏览器页面调用 CRM，使用 `smart-crm-admin-web`，不引本模块。

## 引入依赖

```groovy
implementation 'io.github.sure-zzzzzz:simple-crm-client-core:1.1.0'
```

本模块不发送 HTTP 请求、不装配 Spring、不管理任何凭据。传输与认证由两条传输线之一提供：

本模块的 `CrmWireCodec` 在实现内部使用 Jackson，但 Jackson 仅作为 `compileOnly` 依赖，不会进入本模块发布 POM，也不会替调用方锁定版本。使用任一传输 starter 时由宿主的 Spring Boot 依赖提供 Jackson；不使用传输 starter、直接调用 `CrmWireCodec` 时，宿主必须自行提供 `jackson-databind` 运行时依赖。

| 传输线 | 模块 | 运行环境 |
|---|---|---|
| RestTemplate | `simple-crm-aksk-resttemplate-client-starter` | Spring Boot 2.x / javax |
| RestTemplate（Jakarta） | `simple-crm-aksk-resttemplate-client-jakarta-starter` | Spring Boot 3.4.2 / Java 17+ |
| OpenFeign | `simple-crm-aksk-feign-client-starter` | Spring Cloud OpenFeign 3.x / javax |
| OpenFeign（Jakarta） | `simple-crm-aksk-feign-client-jakarta-starter` | Spring Cloud OpenFeign 4.x / Java 17+ |

## 命令元数据

每个写命令必须携带 `CrmClientCommand`：`idempotencyKey` 必填（每次业务写命令唯一），`correlationId` 可选，留空时由传输层生成并回显。同键同参数不重复产生业务事实：创建命令复用首资源 ID 并按当前权限回读，状态、治理和履约命令重放返回首次成功结果；当前状态通过查询方法读取。同键异参数返回 409。

```java
CrmClientCommand command = new CrmClientCommand("order-confirm-0001", "checkout-flow-42");
```

## 方法面

`CrmClient` 的方法按 CRM Server 资源族组织；列表方法保持机器游标续读契约，浏览器管理台的页码与关键字查询直接使用公开 HTTP 接口：

- **客户**：创建、点查、列表、停用、复启、合并、归属转移；
- **联系人**：创建、点查、按客户列表；
- **目录**：创建（DRAFT 录入）、点查、列表、上架、下架；
- **商机**：创建、点查、列表、阶段推进/输单、归属转移；阶段 `won` 只能由关联报价确认成交派生；
- **报价**：创建草稿、编辑草稿（整单替换）、点查、版本点查、列表、签发、确认、撤回、重议派生、归属转移；创建与重议可携带 `opportunityId`；
- **订单与履约**：订单点查与列表、履约项点查与列表、履约结果回写（首次 `in_progress` 即认领，序号严格递增）、人工重置；
- **消费者注册与审计**：履约消费者注册/查询、审计记录查询。

金额与数量一律使用十进制字符串（如 `{ "amount": "12.34", "currency": "CNY" }`），时间使用带时区偏移的 ISO-8601 字符串，状态与类型只使用服务端稳定 code。

## 结果模型

- `CrmResourceView`：单资源视图，字段键与服务端契约对齐；`get`/`getString`/`getInt` 读取，缺失字段抛 `IllegalArgumentException`；
- `CrmPageView`：列表页，`items` 为当页行、`nextCursor` 为不透明续读游标（`null` 即末页）。

商品视图包含 `id/displayName/state/owner` 和 `offeringReference/capabilityType/requiredConsumerCapability`，创建、点查、列表及上下架返回相同形状。客户 `patchCustomer` 的结果按动作区分：停用/复启返回客户视图；合并返回 `source`、`target` 两个客户视图与 `relocatedContacts` 联系人数组；归属转移返回 `customer` 客户视图与 `transferredQuotations` 报价摘要数组（每项含 `quotationId/owner`）。两个数组都可为空，不能按旧版扁平字段读取。RestTemplate 两条线会自动校验这些结果及嵌套字段；Feign 保留网络响应的嵌套形状，调用方显式校验。

联系人视图为 `id/displayName/state/customerId/title`；`customerId` 表示所属客户，`title` 为可空职务，不使用 `owner` 代替客户归属。创建、点查、列表以及合并后的 `relocatedContacts` 均使用这一形状。`title` 可通过 `getNullable("title")` 读取。报价版本点查保留每行 `obligation` 中的 `subjectReference/fulfillmentScope/requiredConsumerCapability`，分别表示履约对象、履约范围及消费者能力。

创建和编辑报价允许同商品出现多行，每行独立保留数量、价格、授权对象和履约范围；不要在调用端按商品去重或合并业务行。服务端仅在加载商品目录事实时去重，再按原始行计算和冻结结果。

机器列表首页传空 `cursor`，服务端按固定排序返回当页；只有实际存在更多记录时才返回 `nextCursor`。续读须原样传回该游标，并保持调用主体、资源族、过滤条件和授权范围；条件变化后从首页重新查询。`limit` 默认为 20、合法范围为 1～100；不能从游标推算页码或总数。消费者列表不带分页参数，返回完整 `items`，`CrmPageView.nextCursor` 为空。

服务端另为管理台提供 `page/pageSize` 请求与 `items/page/pageSize/total` 响应。本模块的机器方法不发送页码参数，`CrmPageView` 不表示页码响应；不要将管理台响应传入机器列表解码流程，也不要把游标为空理解为页码总数为零。

## 响应校验

`CrmResponseValidator` 在构造资源视图和列表行前校验顶层字段白名单与必填字段，违反即抛 `CrmClientProtocolException`，不构造半成品结果：资源顶层未知字段拒绝（服务端新增资源字段未同步契约时立即暴露）、必填字段缺失拒绝；状态必须为字符串，客户、联系人、商品及转移报价摘要的字符串字段不得使用数字或对象代替，联系人职务允许为空。客户合并与归属转移的嵌套资源另按专用字段契约校验；报价版本的 `lines/obligation`、订单行与履约义务目前保留网络对象，不递归执行字段白名单与类型校验。机器列表封装只接受行数组和可空字符串 `nextCursor`，拒绝未知顶层字段及页码响应，避免把未消费完的数据误判为末页；消费者完整列表允许省略游标。CRM 属事务型消费，本模块不提供快照缓存或降级兜底。

## 错误分类

| 异常 | 语义 | 处置 |
|---|---|---|
| `CrmClientProtocolException` | 4xx 语义错误或响应校验失败，含 HTTP 状态码 | 不可重试：修正参数或核对契约 |
| `CrmClientRetryableException` | 网络、超时或 5xx | 可重试：写命令重发安全由服务端幂等键保证 |

## JSON 编解码

`CrmWireCodec` 使用模块私有静态 `ObjectMapper`，不读取应用 Spring 容器中的全局 `ObjectMapper` Bean。
