# simple-crm-aksk-feign-client-jakarta-starter

`simple-crm-aksk-feign-client-starter` 的 Jakarta 孪生：为 Spring Cloud 2024.0.0 / Java 17+ 应用提供同一份 CRM Feign 契约接口，标注 AKSK Jakarta 底座元注解后由底座自动注入认证头；本模块不做任何传输或认证装配。

与 javax 线的 Java 包名完全相同，同一应用不得同时引入两条线。功能面、配置键、使用示例与响应模型见 javax 线 README；本文件只写差异。

机器列表保持 `cursor/limit` 续读，结果为严格的 `items/nextCursor`；消费者方法返回 `CrmCompleteListResponse` 完整列表，只包含 `items`，不带游标。首批传空游标，后续原样使用响应游标，`nextCursor=null` 表示末页；条件或授权范围变化后从首批重查，每页条数为 1～100。管理台的 `page/pageSize/total` 通过 HTTP 接口消费，本模块不发送页码参数。

响应直接解码为 wire（网络传输）模型；`CrmPageResponse` 拒绝页码响应、未知顶层字段、缺少包络字段以及非字符串或空白游标，`CrmCompleteListResponse` 拒绝分页游标、未知字段和缺少 `items`，非法封装在网络解码时失败。行内资源保持原始 Map，不自动执行 client-core 资源字段白名单校验；非 2xx 响应以 `FeignException` 透传，调用方按 HTTP 状态处理。

商品业务引用与能力字段、客户合并的 `source/target/relocatedContacts`、归属转移的 `customer/transferredQuotations` 均完整保留。治理结果需要严格校验时，调用 `CrmResponseValidator.customerMerge(response.getFields())` 或 `customerTransfer(...)`，不按旧扁平字段读取。同商品多行报价原样传递，各行独立计算。

联系人保留 `id/displayName/state/customerId/title`，所属客户为 `customerId`，职务可空。需要联系人字段校验时调用 `CrmResponseValidator.object(response.getFields(), CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT)`。报价版本保留每行 `obligation` 的 `subjectReference/fulfillmentScope/requiredConsumerCapability`；client-core 不递归校验该嵌套对象。

## 与 javax 线的差异

| 项 | javax 线 | 本模块 |
|---|---|---|
| Spring Cloud | OpenFeign 3.x | OpenFeign 4.2.0（`spring-cloud-dependencies:2024.0.0`） |
| Java | 8+ | 17+ |
| Spring Boot | 2.7（javax 线验证基线） | 3.4.2 基线 |
| AKSK 底座 | `simple-aksk-feign-redis-client-starter:3.0.2` | `simple-aksk-feign-redis-client-jakarta-starter:1.0.0` |

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-crm-aksk-feign-client-jakarta-starter:1.1.0'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-jakarta-starter:1.0.0'
    // AKSK 底座运行链的宿主义务件（Jakarta/Feign 4.x 线用 feign-hc5）：
    implementation 'io.github.openfeign:feign-hc5'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

配置键与 javax 线相同（`io.github.surezzzzzz.sdk.crm.client.base-url`），使用示例与错误处理见 javax 线 README，此处不重复。 方法面与错误分类的维护处在 `simple-crm-client-core` 的 README。
