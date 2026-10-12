package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 协作组 Feign 契约接口（7 方法，1.3.7）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-user-groups", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamUserGroupFeignClient {

    /**
     * 分页列协作组（此族持 iam:user-group:api）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/user-groups")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserGroupResponse> listUserGroups(
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) Integer status,
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);

    /**
     * 建协作组（201）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/user-groups")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserGroupResponse createUserGroup(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    /**
     * 协作组详情。
     */
    @org.springframework.web.bind.annotation.GetMapping("/user-groups/{groupId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserGroupResponse getUserGroup(
            @org.springframework.web.bind.annotation.PathVariable("groupId") Long groupId);

    /**
     * 改协作组（整体替换）。
     */
    @org.springframework.web.bind.annotation.PutMapping("/user-groups/{groupId}")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserGroupResponse updateUserGroup(
            @org.springframework.web.bind.annotation.PathVariable("groupId") Long groupId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    /**
     * 删协作组（204）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/user-groups/{groupId}")
    void deleteUserGroup(@org.springframework.web.bind.annotation.PathVariable("groupId") Long groupId);

    /**
     * 组成员分页。
     */
    @org.springframework.web.bind.annotation.GetMapping("/user-groups/{groupId}/users")
    java.util.List<io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserGroupMemberResponse> listUserGroupUsers(
            @org.springframework.web.bind.annotation.PathVariable("groupId") Long groupId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size);

    /**
     * 加成员（204）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/user-groups/{groupId}/users/{subjectId}")
    void assignUserGroupUser(@org.springframework.web.bind.annotation.PathVariable("groupId") Long groupId,
                             @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);

    /**
     * 移成员（204）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/user-groups/{groupId}/users/{subjectId}")
    void revokeUserGroupUser(@org.springframework.web.bind.annotation.PathVariable("groupId") Long groupId,
                             @org.springframework.web.bind.annotation.PathVariable("subjectId") String subjectId);
}
