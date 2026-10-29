# simple-crm-aksk-feign-client-starter

CRM 契约的 Feign 接口层：声明式调用 CRM 商业事实服务（客户、目录、报价、订单、履约闭环），标注 AKSK 底座元注解 `@AkskClientFeignClient` 后由底座自动注入认证头，本模块不做任何传输或认证装配。

适合已使用 Spring Cloud OpenFeign、需要在服务端调用 CRM 的应用。当前版本为 `1.1.0`，对接 `simple-crm-server-starter:1.1.0` 的 `/api/crm` 接口，Java 契约来自 `simple-crm-client-core:1.1.0`。Jakarta / Spring Cloud 2024.0.0 应用使用 `simple-crm-aksk-feign-client-jakarta-starter`，同一应用不得同时引入两条线。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-crm-aksk-feign-client-starter:1.1.0'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:3.0.2'
    // AKSK 底座运行链的宿主义务件（Feign 3.x 用 feign-httpclient）：
    implementation 'io.github.openfeign:feign-httpclient'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

宿主启用 Feign 扫描后接口即可注入：

```java
@EnableFeignClients(basePackageClasses = CrmFeignClient.class)
```

## 配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `io.github.surezzzzzz.sdk.crm.client.base-url` | 无（必填） | CRM Server 地址（仅 origin；API 基路径 `/api/crm` 由接口注解固定） |

AKSK 底座凭据与 Redis 令牌缓存按底座文档配置，凭据通过部署环境注入，不要写入仓库。

## 使用

请求体为 `Map<String, Object>`：可选字段为 null 时请勿放入 Map（与契约省略语义一致）；金额与数量使用十进制字符串，时间使用带时区偏移的 ISO-8601 字符串。写命令的幂等键走 `Idempotency-Key` 请求头参数，同键同参数不重复执行业务；创建复用首资源并回读，已知目标命令恢复首次成功结果。

列表方法使用机器游标模式：首批传空 `cursor`，后续原样传回 `nextCursor`，为空时结束；`limit` 为 1～100，条件或授权范围变化后重新查首批。消费者方法返回完整列表。管理台的 `page/pageSize/total` 通过 HTTP 接口消费，本模块不发送页码参数。商机方法与报价关联使用 `simple-crm-client-core:1.1.0` 的同一份契约。

```java
@Autowired
private CrmFeignClient crm;

// 创建客户
Map<String, Object> body = new LinkedHashMap<>();
body.put("displayName", "示例客户");
CrmResourceResponse customer = crm.createCustomer("customer-create-0001", null, body);

// 履约消费者回写（首次 in_progress 即认领，序号严格递增）
Map<String, Object> report = new LinkedHashMap<>();
report.put("sequence", 1);
report.put("state", "in_progress");
crm.patchFulfillmentItem("fulfillment-0001", "fulfillment-report-0001", report,
        CrmPatchRouting.of(report));
```

## 响应模型

- `CrmResourceResponse`：单资源 wire 视图，全部顶层字段经 `get(key)` 读取；
- 客户合并的 `source/target/relocatedContacts` 与归属转移的 `customer/transferredQuotations` 保留嵌套对象/数组；商品的业务引用与商业/消费者能力字段均保留。需要严格治理结果校验时调用 `CrmResponseValidator.customerMerge(response.getFields())` 或 `customerTransfer(...)`。
- 联系人保留 `id/displayName/state/customerId/title`，所属客户为 `customerId`，职务可空。需要联系人字段校验时调用 `CrmResponseValidator.object(response.getFields(), CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT)`。报价版本保留每行 `obligation` 的 `subjectReference/fulfillmentScope/requiredConsumerCapability`；client-core 不递归校验该嵌套对象。
- `CrmPageResponse`：机器游标列表页，统一取 `items` 行数组与 `nextCursor` 续读游标；分页封装要求两个包络字段都出现，拒绝页码响应、未知顶层字段以及非字符串或空白游标，防止静默漏读。`CrmCompleteListResponse`：消费者完整列表，只允许 `items`，不带游标；行内资源均保持原始 Map。

非 2xx 响应按 Feign `FeignException` 透传状态码；非法分页封装在网络解码时失败。本模块不自动执行 `CrmResponseValidator` 的资源字段白名单校验，也不转换为 RestTemplate 线的异常分类；调用方需要严格资源校验时显式使用 client-core 的校验器，不能将成功解码视为完成资源契约校验。

## 运行环境

Java 8+，Spring Cloud OpenFeign 3.x，Spring Boot 2.7（验证基线 2.7.9）。首次接入前先读 `simple-crm-client-core` 的 README（方法面、命令元数据与错误分类在那里维护）。
