package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 用户族 Feign 契约接口（12 方法）。请求体为 Map（可选字段 null 不放入）；响应为 wire DTO；非 2xx 透传 FeignException。分页为 Spring Page wire 形态（页码 0 起）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-users", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamUserFeignClient {

    /**
     * 分页查询用户（null 可选参不产生查询项）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/users")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserPageResponse listUsers(
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) Integer status,
            @org.springframework.web.bind.annotation.RequestParam(value = "departmentId", required = false) Long departmentId,
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);

    @org.springframework.web.bind.annotation.GetMapping("/users/{subjectId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserResponse getUser(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    /**
     * 角色编码裸列表（字符串数组契约）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/users/{subjectId}/roles")
    java.util.List<String> getUserRoles(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    /**
     * 页面准入应用编码裸列表（字符串数组契约）；此端点单独持 iam:portal:api 码。
     */
    @org.springframework.web.bind.annotation.GetMapping("/users/{subjectId}/page-admitted-applications")
    java.util.List<String> listPageAdmittedApplications(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    @org.springframework.web.bind.annotation.PostMapping("/users")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserResponse createUser(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PatchMapping("/users/{subjectId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserResponse updateUser(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{subjectId}")
    void deleteUser(@org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    @org.springframework.web.bind.annotation.PutMapping("/users/{subjectId}/enable")
    void enableUser(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    @org.springframework.web.bind.annotation.PutMapping("/users/{subjectId}/disable")
    void disableUser(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    @org.springframework.web.bind.annotation.PutMapping("/users/{subjectId}/reset-password")
    void resetPassword(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PutMapping("/users/{subjectId}/roles/{roleId}")
    void assignRole(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId,
            @org.springframework.web.bind.annotation.PathVariable("roleId") Long roleId);

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{subjectId}/roles/{roleId}")
    void revokeRole(
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId,
            @org.springframework.web.bind.annotation.PathVariable("roleId") Long roleId);
}
