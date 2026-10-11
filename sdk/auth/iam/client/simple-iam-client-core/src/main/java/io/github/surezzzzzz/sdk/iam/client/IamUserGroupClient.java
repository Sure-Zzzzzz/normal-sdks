package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamUserGroup;
import io.github.surezzzzzz.sdk.iam.client.model.IamUserGroupMember;

import java.util.List;

/**
 * IAM 协作组 openapi 契约（7 方法，对位 server /iam/api/user-groups，1.3.7）。
 *
 * <p>组织同步维护协作组全管理面；码 {@code iam:user-group:api}；无 DATA 面。</p>
 *
 * @author surezzzzzz
 */
public interface IamUserGroupClient {

    /**
     * 分页列组。
     *
     * @param status  状态过滤（null=不过滤）
     * @param keyword 关键字（null=不过滤）
     * @param page    页码（1 起）
     * @param size    页大小
     * @return 协作组列表
     */
    List<IamUserGroup> listGroups(Integer status, String keyword, Integer page, Integer size);

    /**
     * 建组（201）。
     *
     * @param code        组编码（唯一）
     * @param name        组名称
     * @param description 描述（null=不填）
     * @return 创建后的组
     */
    IamUserGroup createGroup(String code, String name, String description);

    /**
     * 单组详情。
     *
     * @param groupId 组 ID
     * @return 协作组
     */
    IamUserGroup getGroup(Long groupId);

    /**
     * 改组（整体替换）。
     *
     * @param groupId     组 ID
     * @param name        新名称（null=不动）
     * @param description 新描述（null=不动；空串=清空）
     * @return 更新后的组
     */
    IamUserGroup updateGroup(Long groupId, String name, String description);

    /**
     * 删组（204）。
     *
     * @param groupId 组 ID
     */
    void deleteGroup(Long groupId);

    /**
     * 组成员分页。
     *
     * @param groupId 组 ID
     * @param page    页码（1 起）
     * @param size    页大小
     * @return 成员列表（subjectId 定位）
     */
    List<IamUserGroupMember> listGroupUsers(Long groupId, Integer page, Integer size);

    /**
     * 加成员（204）。
     *
     * @param groupId   组 ID
     * @param subjectId 成员公开主体
     */
    void assignUser(Long groupId, String subjectId);

    /**
     * 移成员（204）。
     *
     * @param groupId   组 ID
     * @param subjectId 成员公开主体
     */
    void revokeUser(Long groupId, String subjectId);
}
