package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 会话 Feign 契约接口（2 方法，1.3.7）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-sessions", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamSessionFeignClient {

    /**
     * 按用户列在线会话（subjectId 必填；此族持 iam:session:api）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/sessions")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamSessionResponse> listSessionsBySubject(
            @org.springframework.web.bind.annotation.RequestParam("subjectId") String subjectId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);

    /**
     * 踢指定会话（204）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/sessions/{sessionId}")
    void revokeSession(@org.springframework.web.bind.annotation.PathVariable("sessionId") String sessionId);
}
