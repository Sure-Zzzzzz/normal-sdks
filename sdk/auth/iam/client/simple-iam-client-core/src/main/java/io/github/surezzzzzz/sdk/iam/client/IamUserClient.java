package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;

import java.util.List;

/**
 * IAM 用户族 openapi 契约（11 方法，对位 server /iam/api/users）。
 *
 * <p>消费方 AKP 需授 {@code iam:user:api} API 码与 iam:user DATA 范围；非 2xx 透传
 * Spring/Feign 标准异常。分页为 Spring Page wire 形态（content/totalElements，页码 0 起）。</p>
 *
 * @author surezzzzzz
 */
public interface IamUserClient {

    /**
     * 分页查询用户。
     *
     * @param status       状态过滤（null=不过滤）
     * @param departmentId 部门过滤（null=不过滤）
     * @param keyword      关键字过滤（null=不过滤）
     * @param page         页码（0 起）
     * @param size         页大小
     * @return 用户分页
     */
    IamSpringPage<IamUser> listUsers(Integer status, Long departmentId, String keyword, Integer page, Integer size);

    /**
     * 用户详情（含角色编码列表）。
     *
     * @param subjectId 公开主体
     * @return 用户
     */
    IamUser getUser(String subjectId);

    /**
     * 用户角色编码列表。
     *
     * @param subjectId 公开主体
     * @return 角色编码列表（wire 为字符串数组，非对象数组）
     */
    List<String> getUserRoles(String subjectId);

    /**
     * 创建用户。
     *
     * @param username     登录名
     * @param password     初始密码（服务端密码策略校验）
     * @param displayName  显示名
     * @param email        邮箱（null=不登记）
     * @param phone        手机号（null/空串=不登记；非空走 E.164 规范化，非法 400）
     * @param departmentId 部门（null=不挂）
     * @return 创建后的用户（201）
     */
    IamUser createUser(String username, String password, String displayName, String email, String phone,
                       Long departmentId);

    /**
     * 更新用户资料。
     *
     * @param subjectId   公开主体
     * @param displayName 显示名（null=不动）
     * @param email       邮箱（null=不动；空串=清空）
     * @param phone       手机号（null=不动；空串=清空；非空规范化）
     * @return 更新后的用户
     */
    IamUser updateUser(String subjectId, String displayName, String email, String phone);

    /**
     * 删除用户（204）。
     *
     * @param subjectId 公开主体
     */
    void deleteUser(String subjectId);

    /**
     * 启用用户（204）。
     *
     * @param subjectId 公开主体
     */
    void enableUser(String subjectId);

    /**
     * 停用用户（204）。
     *
     * @param subjectId 公开主体
     */
    void disableUser(String subjectId);

    /**
     * 重置用户密码（204；服务端按策略校验并吊销该用户会话）。
     *
     * @param subjectId   公开主体
     * @param newPassword 新密码
     */
    void resetPassword(String subjectId, String newPassword);

    /**
     * 挂角色（204）。
     *
     * @param subjectId 公开主体
     * @param roleId    角色数字 ID
     */
    void assignRole(String subjectId, Long roleId);

    /**
     * 摘角色（204）。
     *
     * @param subjectId 公开主体
     * @param roleId    角色数字 ID
     */
    void revokeRole(String subjectId, Long roleId);
}
