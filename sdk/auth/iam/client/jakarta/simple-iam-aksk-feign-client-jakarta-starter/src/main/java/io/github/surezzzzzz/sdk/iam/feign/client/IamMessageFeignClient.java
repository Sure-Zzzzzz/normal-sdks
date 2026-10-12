package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 站内信 Feign 契约接口（3 方法，1.3.7）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-messages", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamMessageFeignClient {

    /**
     * 发送站内信（201；此族持 iam:message:api）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/messages")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamMessageSendResultResponse sendMessage(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    /**
     * 按收件人查信（subjectId 必填）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/messages")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamMessageResponse> listMessagesBySubject(
            @org.springframework.web.bind.annotation.RequestParam("subjectId") String subjectId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);

    /**
     * 发送批次详情。
     */
    @org.springframework.web.bind.annotation.GetMapping("/messages/{sendBatchId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamMessageBatchResponse getMessageBatch(
            @org.springframework.web.bind.annotation.PathVariable("sendBatchId") String sendBatchId);
}
