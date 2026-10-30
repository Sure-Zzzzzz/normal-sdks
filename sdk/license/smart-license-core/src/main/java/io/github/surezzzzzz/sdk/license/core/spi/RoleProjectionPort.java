package io.github.surezzzzzz.sdk.license.core.spi;

import java.util.List;
import lombok.Value;

/**
 * License 专用角色的协作端口；只有宿主适配知道身份服务客户端与传输。
 * 所有写入均携带身份服务实际修订，不以 License 本地修订替代远端围栏。
 *
 * @author surezzzzzz
 */
public interface RoleProjectionPort {

    /** 回读启用的目标应用及其批准权限清单。 */
    Manifest manifest(long applicationId);

    /** 回读授权根下完整目录，包含根；缺失、重复、环由业务拒绝。 */
    List<Department> departments(long rootDepartmentId);

    /** 回读专用角色、规则及全部挂载，观察期间修订变化须明确报冲突。 */
    Role read(String openRoleId);

    /** 以持久 externalId 创建；上游必须支持同内容幂等，不重置已有规则。 */
    Role create(String externalId, long applicationId, long rootDepartmentId, String name);

    /** 替换已批准规则；rulesJson 为精确 PAGE/API/DATA 快照，不猜商业动作。 */
    void putRules(String openRoleId, long applicationId, long revision,
                  long manifestVersion, String manifestDigest, String rulesJson);

    /** 清空本专用角色的应用贡献，不删除角色历史。 */
    void deleteRules(String openRoleId, long applicationId, long revision);

    /** 在批准根子树挂载，每次写均使用最新实际修订。 */
    void mount(String openRoleId, long departmentId, long revision);

    /** 摘除本专用角色的部门挂载，包括迁出后的清理。 */
    void unmount(String openRoleId, long departmentId, long revision);

    /** 已验证的目标应用清单；身份服务没有目录代际证明，环境仍需人工确认。 */
    @Value
    class Manifest {
        long applicationId;
        String applicationCode;
        long version;
        String digest;
        List<String> pagePermissions;
        List<String> apiPermissions;
    }

    /** 当前目录事实，不使用路径前缀推断归属。 */
    @Value
    class Department {
        long id;
        Long parentId;
        boolean active;
    }

    /** 专用身份、远端修订与真实贡献；rulesJson 为 null 表示无规则。 */
    @Value
    class Role {
        String openRoleId;
        String externalId;
        String code;
        long applicationId;
        long rootDepartmentId;
        long revision;
        String rulesJson;
        List<Long> departmentIds;
    }

    /** 失败分支必须区别确定拒绝与写入结果未知，不携带原始上游报文。 */
    enum FailureKind {
        REVISION_CONFLICT, MANIFEST_CONFLICT, REJECTED, UNAVAILABLE, UNCERTAIN, INVALID_RESPONSE
    }

    /** 纯契约异常：宿主将官方客户端异常分类，业务决定是否停止执行。 */
    @lombok.Getter
    class Failure extends RuntimeException {
        private final FailureKind kind;

        /** 保留异常原因供内部诊断，HTTP 面只返回固定业务原因码。 */
        public Failure(FailureKind kind, Throwable cause) {
            super(kind.name(), cause);
            this.kind = kind;
        }
    }
}
