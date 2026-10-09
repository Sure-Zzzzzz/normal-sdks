package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 部门族 Feign 契约接口（5 方法）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-departments", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamDepartmentFeignClient {

    @org.springframework.web.bind.annotation.GetMapping("/departments")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamDepartmentResponse> listDepartments();

    @org.springframework.web.bind.annotation.GetMapping("/departments/{departmentId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamDepartmentResponse getDepartment(
            @org.springframework.web.bind.annotation.PathVariable("departmentId") Long departmentId);

    @org.springframework.web.bind.annotation.PostMapping("/departments")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamDepartmentResponse createDepartment(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.PatchMapping("/departments/{departmentId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamDepartmentResponse updateDepartment(
            @org.springframework.web.bind.annotation.PathVariable("departmentId") Long departmentId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @org.springframework.web.bind.annotation.DeleteMapping("/departments/{departmentId}")
    void deleteDepartment(@org.springframework.web.bind.annotation.PathVariable("departmentId") Long departmentId);
}
