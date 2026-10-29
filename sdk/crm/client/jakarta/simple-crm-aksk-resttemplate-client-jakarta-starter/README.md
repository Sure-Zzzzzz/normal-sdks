# simple-crm-aksk-resttemplate-client-jakarta-starter

`simple-crm-aksk-resttemplate-client-starter` 的 Jakarta 孪生：为 Spring Boot 3 / Java 17+ 应用提供同一份 `CrmClient` 契约的 RestTemplate 传输装配。请求通过 AKSK Jakarta 底座的 `akskClientRestTemplate` 发送并自动携带认证头；本模块不创建第二套 HTTP 连接池、不管理凭据、不自带认证装配。

与 javax 线的 Java 包名完全相同，同一应用不得同时引入两条线。功能面、配置键、错误分类与使用方式见 javax 线 README；本文件只写差异。

机器列表保持 `cursor/limit` 续读，结果为 `items/nextCursor`；消费者方法返回完整列表。首批传空游标，后续原样使用响应游标，为空即结束；条件或授权范围变化后从首批重查，每页条数为 1～100。管理台的 `page/pageSize/total` 通过 HTTP 接口消费，本模块不发送页码参数。

商品视图包含稳定业务引用与能力字段。客户合并返回 `source/target/relocatedContacts`，归属转移返回 `customer/transferredQuotations`；本模块按动作自动校验顶层与嵌套字段。同商品多行报价原样传递，各行独立计算。

联系人创建、点查、列表及合并改挂结果使用 `id/displayName/state/customerId/title`，所属客户为 `customerId`，职务 `title` 可空。报价版本点查保留每行 `obligation` 的 `subjectReference/fulfillmentScope/requiredConsumerCapability`。资源顶层及客户治理嵌套资源执行字段白名单与必填校验；报价版本的 `lines/obligation`、订单行与履约义务不递归执行字段白名单与类型校验。

## 与 javax 线的差异

| 项 | javax 线 | 本模块 |
|---|---|---|
| Spring Boot | 2.7（javax 线验证基线） | 3.4.2 基线 |
| Java | 8+ | 17+ |
| AKSK 底座 | `simple-aksk-resttemplate-redis-client-starter:3.0.2` | `simple-aksk-resttemplate-redis-client-jakarta-starter:1.0.0` |
| 自动配置注册 | `META-INF/spring.factories` | `META-INF/spring/...AutoConfiguration.imports`，注解形态 `@AutoConfiguration` |

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-crm-aksk-resttemplate-client-jakarta-starter:1.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-jakarta-starter:1.0.0'
    // AKSK 底座运行链的宿主义务件（底座 compileOnly 不传递；Jakarta 线 HTTP 池用 httpclient5）：
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.apache.httpcomponents.client5:httpclient5'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

配置键与 javax 线相同（`io.github.surezzzzzz.sdk.crm.client.enable` / `base-url`），使用示例、让位语义与错误处理见 javax 线 README，此处不重复。 方法面与错误分类的维护处在 `simple-crm-client-core` 的 README。
