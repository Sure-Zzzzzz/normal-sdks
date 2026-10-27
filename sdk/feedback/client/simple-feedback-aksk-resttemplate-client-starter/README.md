# simple-feedback-aksk-resttemplate-client-starter

为 Spring Boot 2 应用提供 Feedback `FeedbackClient` 的 RestTemplate 认证传输实现。请求使用宿主 AKSK 底座提供的 `akskClientRestTemplate`，本模块只负责 Feedback OpenAPI 1.0.1 的路径、请求转换和响应校验，不创建第二套连接池、不管理机器凭据。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-aksk-resttemplate-client-starter:1.0.1'
    implementation 'io.github.sure-zzzzzz:simple-feedback-client-core:1.0.1'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:3.0.2'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

AKSK 底座的 Redis、连接池和凭据配置由宿主按底座文档提供，机密值通过运行环境注入。宿主必须提供名为 `akskClientRestTemplate` 的认证传输 Bean；缺少该 Bean 时客户端不能装配。Feedback Server 会把 AKSK SERVICE 主体归一为 `aksk:{clientId}`，仍按 API 与 DATA 权限校验。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        feedback:
          client:
            enable: true
            base-url: http://feedback.example.test
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `io.github.surezzzzzz.sdk.feedback.client.enable` | `true` | 是否装配默认 `FeedbackClient` |
| `io.github.surezzzzzz.sdk.feedback.client.base-url` | 无（必填） | Feedback Server origin；客户端追加固定 API 基路径 `/api/feedback` |

## 使用

```java
import io.github.surezzzzzz.sdk.feedback.client.FeedbackClient;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Autowired
private FeedbackClient feedbackClient;

Map<String, Object> submission = new LinkedHashMap<>();
submission.put("applicationCode", "demo-app");
submission.put("kind", "proactive");
submission.put("type", "experience");
submission.put("score", 10);
submission.put("content", "提交反馈示例");
submission.put("contactEmail", "user@example.test");

FeedbackSubmission result = feedbackClient.createSubmission(
        submission, Collections.emptyList(), "feedback-create-0001", "request-0001");
```

查询方法把 `Map<String, ?>` 中的非 `null` 值编码为 URL 查询参数。提交、处理和应用登记使用 OpenAPI 定义的 JSON 或 multipart 请求；提交必须传入非空 `Idempotency-Key`，失败重试复用同键。

## 错误处理

4xx 和成功响应结构不完整时抛 `FeedbackClientProtocolException`；网络异常、429 和 5xx 时抛 `FeedbackClientRetryableException`。调用参数为空或空白时抛 `FeedbackClientValidationException`。异常不记录令牌、请求体或其他敏感字段。

## 版本边界与兼容性

本模块是 javax 线实现，基线为 Spring Boot 2.7.9 / Java 8。Feign 和 Spring Boot 3/Jakarta 传输件不属于 1.0.1 支持范围，不应与本模块同时引入。对接服务端契约为 `simple-feedback-server-starter:1.0.1`。

## 版本映射

| 本模块 | 契约包 | 服务端 |
|---|---|---|
| 1.0.1 | simple-feedback-client-core 1.0.1 | simple-feedback-server-starter 1.0.1 |
