package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenDepartmentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenOrganizationDirectoryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenOrganizationMemberResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 组织事实只从数据库读取；挂载前按有序行锁重新确认祖先链。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamOpenDirectoryService {
    private static final String SQL_NODE = "SELECT id, code, parent_id, name, status FROM iam_department WHERE id = ?";
    private static final String SQL_NODE_LOCK = SQL_NODE + " FOR UPDATE";
    private static final String SQL_CHILDREN = "SELECT id, code, parent_id, name, status FROM iam_department "
            + "WHERE parent_id IN (:parents) ORDER BY id LIMIT :limit";
    private static final String SQL_MEMBER = "SELECT subject_id, status, department_id FROM iam_user WHERE subject_id = ?";
    // JDBC 结果列与具名参数集中管理，避免组织字段在多条读取路径中漂移。
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_CODE = "code";
    private static final String COLUMN_PARENT_ID = "parent_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_SUBJECT_ID = "subject_id";
    private static final String COLUMN_DEPARTMENT_ID = "department_id";
    private static final String PARAMETER_PARENTS = "parents";
    private static final String PARAMETER_LIMIT = "limit";
    private static final RowMapper<OpenDepartmentResponse> NODE_MAPPER = (row, index) -> {
        long parent = row.getLong(COLUMN_PARENT_ID);
        Long parentId = row.wasNull() ? null : parent;
        return new OpenDepartmentResponse(row.getLong(COLUMN_ID), row.getString(COLUMN_CODE), parentId,
                row.getString(COLUMN_NAME), row.getInt(COLUMN_STATUS));
    };
    private final JdbcTemplate jdbcTemplate;

    /**
     * 完整读取批准根及后代；同一事务快照内排序、摘要并执行预算。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public OpenOrganizationDirectoryResponse readDirectory(Long rootDepartmentId) {
        IamOpenRoleProtocolHelper.positive(rootDepartmentId);
        OpenDepartmentResponse root = node(rootDepartmentId, false);
        if (root == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND);
        requireRootStructure(root);
        List<OpenDepartmentResponse> result = new ArrayList<>();
        result.add(root);
        Set<Long> visited = new HashSet<>();
        visited.add(rootDepartmentId);
        List<Long> parents = Collections.singletonList(rootDepartmentId);
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbcTemplate);
        int depth = 0;
        while (!parents.isEmpty()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put(PARAMETER_PARENTS, parents);
            parameters.put(PARAMETER_LIMIT, SimpleIamServerConstant.OPEN_DIRECTORY_NODE_BUDGET - result.size() + 1);
            List<OpenDepartmentResponse> children = named.query(SQL_CHILDREN, parameters, NODE_MAPPER);
            if (children.isEmpty()) break;
            depth++;
            if (depth >= SimpleIamServerConstant.OPEN_DIRECTORY_DEPTH_BUDGET
                    || result.size() + children.size() > SimpleIamServerConstant.OPEN_DIRECTORY_NODE_BUDGET) {
                throw new IamOpenRoleException(ErrorCode.OPEN_DIRECTORY_BUDGET_EXCEEDED);
            }
            parents = new ArrayList<>();
            for (OpenDepartmentResponse child : children) {
                if (!visited.add(child.getDepartmentId())) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
                result.add(child);
                parents.add(child.getDepartmentId());
            }
        }
        result.sort(Comparator.comparing(OpenDepartmentResponse::getDepartmentId));
        log.debug("开放目录完整读取：rootDepartmentId={}, nodes={}", rootDepartmentId, result.size());
        return new OpenOrganizationDirectoryResponse(rootDepartmentId, true,
                IamOpenRoleProtocolHelper.digest(result), result);
    }

    /**
     * 指定成员不在批准根内时与不存在统一 404，停用事实不伪装为可用。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public OpenOrganizationMemberResponse readMember(Long rootDepartmentId, String subjectId) {
        IamOpenRoleProtocolHelper.positive(rootDepartmentId);
        IamOpenRoleProtocolHelper.text(subjectId, SimpleIamServerConstant.OPEN_ROLE_SUBJECT_MAX_LENGTH, true);
        List<OpenOrganizationMemberResponse> members = jdbcTemplate.query(SQL_MEMBER, (row, index) -> {
            long department = row.getLong(COLUMN_DEPARTMENT_ID);
            Long departmentId = row.wasNull() ? null : department;
            return new OpenOrganizationMemberResponse(row.getString(COLUMN_SUBJECT_ID), row.getInt(COLUMN_STATUS), departmentId, true);
        }, subjectId);
        if (members.size() != 1 || !isInRoot(rootDepartmentId, members.get(0).getDepartmentId())) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND);
        }
        return members.get(0);
    }

    /**
     * 查询当前真实祖先关系；残留关系允许报告不在根内，不返回外部部门详情。
     */
    public boolean isInRoot(Long rootDepartmentId, Long departmentId) {
        return chain(rootDepartmentId, departmentId, false) != null;
    }

    /**
     * 创建前锁住并确认根部门，避免与根删除并发产生悬空引用。
     */
    public void lockActiveRoot(Long rootDepartmentId) {
        OpenDepartmentResponse root = node(rootDepartmentId, true);
        if (root == null || !Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(root.getStatus())) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        requireRootStructure(root);
    }

    /**
     * 挂载前读取链、按编号有序取锁、再以数据库当前读核对全部父关系和状态。
     * 不用 JPA 一级缓存或重复的旧快照证明范围；所有锁保持至宿主事务提交。
     */
    public void lockActiveChain(Long rootDepartmentId, Long departmentId) {
        Map<Long, OpenDepartmentResponse> discovered = chain(rootDepartmentId, departmentId, true);
        // 目标不在批准子树内属于结构冲突（409），不是主体权限缺失（403）；
        // 主体权限由 API/DATA 校验先行把关，此处只剩范围事实。
        if (discovered == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        List<Long> ids = new ArrayList<>(discovered.keySet());
        Collections.sort(ids);
        for (Long id : ids) {
            OpenDepartmentResponse current = node(id, true);
            OpenDepartmentResponse expected = discovered.get(id);
            if (current == null || !Objects.equals(expected.getParentId(), current.getParentId())
                    || !Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(current.getStatus())) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
            }
        }
        log.debug("开放目录祖先链锁定：rootDepartmentId={}, departmentId={}, nodes={}", rootDepartmentId, departmentId, ids.size());
    }

    private Map<Long, OpenDepartmentResponse> chain(Long rootDepartmentId, Long departmentId, boolean activeOnly) {
        Map<Long, OpenDepartmentResponse> result = new LinkedHashMap<>();
        Long currentId = departmentId;
        while (currentId != null) {
            if (result.containsKey(currentId)) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
            }
            if (result.size() >= SimpleIamServerConstant.OPEN_DIRECTORY_DEPTH_BUDGET) {
                throw new IamOpenRoleException(ErrorCode.OPEN_DIRECTORY_BUDGET_EXCEEDED);
            }
            OpenDepartmentResponse current = node(currentId, false);
            if (current == null) return null;
            if (activeOnly && !Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(current.getStatus())) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_FORBIDDEN);
            }
            result.put(currentId, current);
            if (rootDepartmentId.equals(currentId)) {
                requireRootStructure(current);
                return result;
            }
            currentId = current.getParentId();
        }
        return null;
    }

    /**
     * 根锚点也必须处于可解析的组织结构中，不能在到达根时漏掉其自身环或悬空父链。
     */
    private void requireRootStructure(OpenDepartmentResponse root) {
        Set<Long> visited = new HashSet<>();
        OpenDepartmentResponse current = root;
        while (current != null) {
            if (!visited.add(current.getDepartmentId()) || visited.size() > SimpleIamServerConstant.OPEN_DIRECTORY_NODE_BUDGET) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
            }
            if (current.getParentId() == null) return;
            current = node(current.getParentId(), false);
            if (current == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
    }

    private OpenDepartmentResponse node(Long id, boolean lock) {
        if (id == null) return null;
        List<OpenDepartmentResponse> nodes = jdbcTemplate.query(lock ? SQL_NODE_LOCK : SQL_NODE, NODE_MAPPER, id);
        return nodes.isEmpty() ? null : nodes.get(0);
    }
}
