package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 受委托角色族 Feign 契约接口（13 方法）。If-Match 乐观并发经 @RequestHeader 携带；分页为 server 自持形态（页码 1 起）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-open-roles", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamOpenRoleFeignClient {

    @org.springframework.web.bind.annotation.PostMapping("/roles")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRoleResponse createOpenRole(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.GetMapping("/roles")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRolePageResponse listOpenRoles(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);

    @org.springframework.web.bind.annotation.GetMapping("/roles/{openRoleId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRoleResponse getOpenRole(
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId);

    @org.springframework.web.bind.annotation.GetMapping("/roles/{openRoleId}/authorization-rules/{applicationId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRoleRuleResponse getOpenRoleRule(
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId,
            @org.springframework.web.bind.annotation.PathVariable("applicationId") Long applicationId);

    /**
     * 写入/替换规则；If-Match 缺失=首写（428/412 由 FeignException 透传）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/roles/{openRoleId}/authorization-rules/{applicationId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRoleRuleResponse putOpenRoleRule(
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId,
            @org.springframework.web.bind.annotation.PathVariable("applicationId") Long applicationId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "If-Match", required = false) String ifMatch,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.DeleteMapping("/roles/{openRoleId}/authorization-rules/{applicationId}")
    void deleteOpenRoleRule(
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId,
            @org.springframework.web.bind.annotation.PathVariable("applicationId") Long applicationId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "If-Match", required = false) String ifMatch);

    @org.springframework.web.bind.annotation.GetMapping("/roles/{openRoleId}/departments")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRoleDepartmentPageResponse listOpenRoleDepartments(
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId);

    @org.springframework.web.bind.annotation.PutMapping("/departments/{departmentId}/roles/{openRoleId}")
    void mountOpenRoleToDepartment(
            @org.springframework.web.bind.annotation.PathVariable("departmentId") Long departmentId,
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "If-Match", required = false) String ifMatch);

    @org.springframework.web.bind.annotation.DeleteMapping("/departments/{departmentId}/roles/{openRoleId}")
    void unmountOpenRoleFromDepartment(
            @org.springframework.web.bind.annotation.PathVariable("departmentId") Long departmentId,
            @org.springframework.web.bind.annotation.PathVariable("openRoleId") String openRoleId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "If-Match", required = false) String ifMatch);

    @org.springframework.web.bind.annotation.GetMapping("/organization-directories/{rootDepartmentId}/departments")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOrganizationDirectoryResponse listOrganizationDirectoryDepartments(
            @org.springframework.web.bind.annotation.PathVariable("rootDepartmentId") Long rootDepartmentId);

    @org.springframework.web.bind.annotation.GetMapping("/organization-directories/{rootDepartmentId}/members/{subjectId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamOrganizationMemberResponse getOrganizationDirectoryMember(
            @org.springframework.web.bind.annotation.PathVariable("rootDepartmentId") Long rootDepartmentId,
            @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    @org.springframework.web.bind.annotation.GetMapping("/target-applications/{applicationId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamTargetApplicationResponse getTargetApplication(
            @org.springframework.web.bind.annotation.PathVariable("applicationId") Long applicationId);

    @org.springframework.web.bind.annotation.GetMapping("/target-applications/{applicationId}/permission-manifest")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamPermissionManifestResponse getTargetApplicationManifest(
            @org.springframework.web.bind.annotation.PathVariable("applicationId") Long applicationId);
}
