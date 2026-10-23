package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM Portal 机器路由目录 Feign 契约（server 1.3.8，Jakarta 宿主）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-portal-applications", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamPortalApplicationFeignClient {

    /**
     * 读取一页 Portal 机器路由投影；后续页必须携带首页返回的目录版本。
     */
    @org.springframework.web.bind.annotation.GetMapping("/portal/applications")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamPortalApplicationPageResponse listApplications(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size,
            @org.springframework.web.bind.annotation.RequestParam(value = "catalogRevision", required = false)
            Long catalogRevision);
}
