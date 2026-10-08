package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.*;

import java.util.List;
import java.util.Map;

/**
 * IAM 受委托角色族 openapi 契约（13 方法，对位 server /iam/api）。
 *
 * <p>消费方 AKP 需授对应 OPEN_* API 码；写操作走 If-Match 乐观并发——值形如
 * {@code open-role:<openRoleId>:<revision>}，revision 取自上次响应模型字段；
 * 缺条件 428、版本旧 412 透传，冲突后应重读重算不盲重发。分页为 server 自持形态
 * （items/total/page/size，页码 1 起），与用户族的 Spring Page 形态不同。</p>
 *
 * @author surezzzzzz
 */
public interface IamOpenRoleClient {

    /**
     * 创建受委托角色（幂等键 externalId；201）。
     *
     * @param externalId       调用方幂等 UUID
     * @param applicationId    固定目标应用 ID
     * @param rootDepartmentId 固定授权部门根 ID
     * @param name             角色名
     * @param description      描述（null=空）
     * @return 创建后的角色（含 revision）
     */
    IamOpenRole createOpenRole(String externalId, Long applicationId, Long rootDepartmentId, String name,
                               String description);

    /**
     * 分页查询受委托角色。
     *
     * @param page 页码（1 起）
     * @param size 页大小
     * @return 角色分页
     */
    IamOpenRolePage listOpenRoles(Integer page, Integer size);

    /**
     * 角色详情（含 revision——If-Match 值来源）。
     *
     * @param openRoleId 角色 UUID
     * @return 角色
     */
    IamOpenRole getOpenRole(String openRoleId);

    /**
     * 读取固定应用规则。
     *
     * @param openRoleId    角色 UUID
     * @param applicationId 目标应用 ID
     * @return 规则（含 revision）
     */
    IamOpenRoleRule getOpenRoleRule(String openRoleId, Long applicationId);

    /**
     * 写入/替换固定应用规则。
     *
     * @param openRoleId        角色 UUID
     * @param applicationId     目标应用 ID
     * @param ifMatch           乐观并发条件（null=首写；否则 open-role:&lt;id&gt;:&lt;revision&gt;）
     * @param manifestVersion   清单版本
     * @param manifestDigest    清单摘要
     * @param pagePermissions   页面权限码
     * @param apiPermissions    API 权限码
     * @param dataGrantTemplate DATA 授权模板
     * @return 写入后的规则（含新 revision）
     */
    IamOpenRoleRule putOpenRoleRule(String openRoleId, Long applicationId, String ifMatch, Long manifestVersion,
                                    String manifestDigest, List<String> pagePermissions,
                                    List<String> apiPermissions, Map<String, Object> dataGrantTemplate);

    /**
     * 删除固定应用规则（204；响应 ETag 头携带新 revision）。
     *
     * @param openRoleId    角色 UUID
     * @param applicationId 目标应用 ID
     * @param ifMatch       乐观并发条件（null=首写形态）
     */
    void deleteOpenRoleRule(String openRoleId, Long applicationId, String ifMatch);

    /**
     * 查询角色挂载的部门关系（含整体 revision）。
     *
     * @param openRoleId 角色 UUID
     * @return 部门挂载分页
     */
    IamOpenRoleDepartmentPage listOpenRoleDepartments(String openRoleId);

    /**
     * 挂载角色到部门（204）。
     *
     * @param departmentId 部门 ID
     * @param openRoleId   角色 UUID
     * @param ifMatch      乐观并发条件
     */
    void mountOpenRoleToDepartment(Long departmentId, String openRoleId, String ifMatch);

    /**
     * 从部门摘除角色（204）。
     *
     * @param departmentId 部门 ID
     * @param openRoleId   角色 UUID
     * @param ifMatch      乐观并发条件
     */
    void unmountOpenRoleFromDepartment(Long departmentId, String openRoleId, String ifMatch);

    /**
     * 组织目录（按授权根）。
     *
     * @param rootDepartmentId 授权根部门 ID
     * @return 组织目录
     */
    IamOrganizationDirectory listOrganizationDirectoryDepartments(Long rootDepartmentId);

    /**
     * 组织目录成员定位。
     *
     * @param rootDepartmentId 授权根部门 ID
     * @param subjectId        公开主体
     * @return 成员定位
     */
    IamOrganizationMember getOrganizationDirectoryMember(Long rootDepartmentId, String subjectId);

    /**
     * 目标应用信息。
     *
     * @param applicationId 应用 ID
     * @return 目标应用
     */
    IamTargetApplication getTargetApplication(Long applicationId);

    /**
     * 目标应用权限清单。
     *
     * @param applicationId 应用 ID
     * @return 权限清单
     */
    IamPermissionManifest getTargetApplicationManifest(Long applicationId);
}
