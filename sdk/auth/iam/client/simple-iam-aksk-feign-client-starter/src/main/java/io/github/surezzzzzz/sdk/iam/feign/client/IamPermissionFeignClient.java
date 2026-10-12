package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 权限清单 Feign 契约接口（1 方法，1.3.7）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-permissions", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamPermissionFeignClient {

    /**
     * 分页列权限（只读目录；此族持 iam:permission:api）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/permissions")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamPermissionResponse> listPermissions(
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);
}
