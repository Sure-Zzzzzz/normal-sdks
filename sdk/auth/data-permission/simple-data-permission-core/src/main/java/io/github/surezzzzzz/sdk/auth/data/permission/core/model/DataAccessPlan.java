package io.github.surezzzzzz.sdk.auth.data.permission.core.model;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 数据访问计划。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class DataAccessPlan {

    /**
     * 访问结果。
     */
    @EqualsAndHashCode.Include
    private final DataAccessOutcome outcome;
    /**
     * 不可拆分的命中授权项。
     */
    @EqualsAndHashCode.Include
    private final List<DataGrant> grants;

    private DataAccessPlan(DataAccessOutcome outcome, Collection<DataGrant> grants) {
        this.outcome = outcome;
        this.grants = grants == null ? Collections.<DataGrant>emptyList() : normalizeRestrictedGrants(grants);
    }

    /**
     * 评估授权文档对资源动作的访问计划。
     *
     * @param document 已验证的授权文档
     * @param request  资源动作请求
     * @return 不可拆分子句组成的访问计划
     */
    public static DataAccessPlan evaluate(DataGrantDocument document, DataPermissionRequest request) {
        if (document == null) {
            throw validation(SimpleDataPermissionConstant.DETAIL_DOCUMENT_CANNOT_BE_NULL);
        }
        if (request == null) {
            throw validation(SimpleDataPermissionConstant.DETAIL_PERMISSION_REQUEST_CANNOT_BE_NULL);
        }
        List<DataGrant> matchedGrants = new ArrayList<DataGrant>();
        for (DataGrant grant : document.getGrants()) {
            if (grant.getResource().equals(request.getResource()) && grant.getActions().contains(request.getAction())) {
                if (grant.isAll()) {
                    return new DataAccessPlan(DataAccessOutcome.ALLOW_ALL, null);
                }
                matchedGrants.add(grant);
            }
        }
        if (matchedGrants.isEmpty()) {
            return deny();
        }
        return new DataAccessPlan(DataAccessOutcome.ALLOW_RESTRICTED, matchedGrants);
    }

    /**
     * 创建拒绝访问计划。
     *
     * @return 命中授权项为空的拒绝计划
     */
    public static DataAccessPlan deny() {
        return new DataAccessPlan(DataAccessOutcome.DENY, null);
    }

    private static List<DataGrant> normalizeRestrictedGrants(Collection<DataGrant> grants) {
        return DataPermissionValidationHelper.normalizeObjects(grants, SimpleDataPermissionConstant.FIELD_GRANTS,
                SimpleDataPermissionConstant.MAX_GRANT_COUNT, DataPermissionValidationHelper.GRANT_COMPARATOR,
                ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT);
    }

    private static DataPermissionValidationException validation(String detail) {
        return DataPermissionValidationHelper.validation(ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT, detail);
    }
}
