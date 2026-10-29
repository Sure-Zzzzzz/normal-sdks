# simple-crm-aksk-resttemplate-client-starter

为 Spring Boot 2 应用提供 `CrmClient` 的 RestTemplate 传输装配：用 Java 方法调用 CRM 商业事实服务（客户、目录、报价、订单、履约闭环）。请求通过访问密钥凭据（AKSK）底座创建的 `akskClientRestTemplate` 发送，由底座获取访问令牌并添加认证头。本模块不创建第二套 HTTP 连接池、不管理 AKSK 凭据、不自带任何认证装配。

适合已使用 RestTemplate、需要在服务端调用 CRM 的应用（如履约消费者轮询待执行履约项）。当前版本为 `1.1.0`，对接 `simple-crm-server-starter:1.1.0` 的 `/api/crm` 接口，公开 Java 契约来自 `simple-crm-client-core:1.1.0`。Jakarta / Spring Boot 3 应用使用 `simple-crm-aksk-resttemplate-client-jakarta-starter`，两条线的 Java 包名相同，同一应用不得同时引入。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-crm-aksk-resttemplate-client-starter:1.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:3.0.2'
    // AKSK 底座运行链的宿主义务件（底座 compileOnly 不传递）：
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.apache.httpcomponents:httpclient'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

AKSK 底座负责令牌获取与续期，本模块将其 `akskClientRestTemplate` 按装配条件接入 `CrmClient`。Redis 连接、本地缓存与 HTTP 连接池依赖由宿主自备（底座按 compileOnly 口径不传递，见上方依赖片段）；凭据通过部署环境注入，不要写入仓库。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        crm:
          client:
            enable: true
            base-url: http://crm.example.test
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `io.github.surezzzzzz.sdk.crm.client.enable` | `true` | 模块总开关 |
| `io.github.surezzzzzz.sdk.crm.client.base-url` | 无（必填） | CRM Server 地址（仅 origin，API 基路径 `/api/crm` 由模块固定） |

## 使用

列表方法使用机器游标模式：首批传空 `cursor`，后续原样传回 `nextCursor`，为空时结束；`limit` 为 1～100，条件或授权范围变化后重新查首批。消费者方法返回完整列表。管理台的 `page/pageSize/total` 通过 HTTP 接口消费，本模块不发送页码参数。商机方法与报价关联使用 `simple-crm-client-core:1.1.0` 的同一份契约。

商品结果保留稳定业务引用与商业/消费者能力字段。`patchCustomer` 按动作校验结果：停用/复启为客户视图，合并为 `source/target/relocatedContacts`，归属转移为 `customer/transferredQuotations`；嵌套档案与数组行同样执行字段白名单及必填校验，不会把合法治理结果误按普通客户响应拒绝。报价同商品多行原样传递，各行独立计算，不按商品合并。

联系人创建、点查、列表及合并改挂结果使用 `id/displayName/state/customerId/title`，所属客户为 `customerId`，职务 `title` 可空。报价版本点查原样保留每行 `obligation` 的履约对象 `subjectReference`、范围 `fulfillmentScope` 与消费者能力 `requiredConsumerCapability`。

```java
@Autowired
private CrmClient crmClient;

// 写命令携带幂等键：创建复用首资源并回读，已知目标命令重放首次结果
CrmResourceView customer = crmClient.createCustomer(
        new CrmClientCommand("customer-create-0001", null), "示例客户");

// 履约消费者轮询待执行项并回写（首次 in_progress 即认领，序号严格递增）
CrmPageView items = crmClient.listFulfillmentItems("pending_dispatch", null, null, 20);
if (!items.getItems().isEmpty()) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("sequence", 1);
    report.put("state", "in_progress");
    report.put("note", "已认领");
    CrmResourceView claimed = crmClient.patchFulfillmentItem(
            new CrmClientCommand("fulfillment-report-0001", null),
            items.getItems().get(0).getString("fulfillmentId"), report);
}
```

应用自带 `CrmClient` Bean 时本模块默认装配让位（`@ConditionalOnMissingBean`）；AKSK 底座缺席时整链不装配。

## 错误处理

非 2xx 响应：4xx 抛 `CrmClientProtocolException`（不可重试，修正参数后重试），网络异常与 5xx 抛 `CrmClientRetryableException`（可重试；写命令重发安全由服务端幂等键保证）。响应构造前经 `CrmResponseValidator` 校验资源顶层字段，未知字段与必填缺失即拒绝；客户治理结果另执行嵌套资源校验。报价版本的 `lines/obligation`、订单行与履约义务保留网络对象，不递归执行字段白名单与类型校验。

## 运行环境

Java 8+，Spring Boot 2.7（验证基线 2.7.9），`spring-web` 的 `RestTemplate`。首次接入前先读 `simple-crm-client-core` 的 README（方法面、结果模型与错误分类在那里维护）。
