package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 可信应用目录 Feign 契约接口（1 方法，1.3.7）。请求查询参可选 null 不入；响应为 wire DTO。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-applications", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamApplicationFeignClient {

    /**
     * 分页列出全部可信应用（全局目录；此族持 iam:application:api 码）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/applications")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamApplicationPageResponse listApplications(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);
}
