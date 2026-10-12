package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 角色目录 Feign 契约接口（1 方法，1.3.7）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-role-catalog", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamRoleCatalogFeignClient {

    /**
     * 分页列角色（只读目录；此族持 iam:role:api）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/role-catalog")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamRoleCatalogEntryResponse> listRoleCatalog(
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);
}
