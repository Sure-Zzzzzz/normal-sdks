package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理 REST 数据范围匹配辅助。
 *
 * @author surezzzzzz
 */
public final class ManagementDataAccessPlanHelper {

    private ManagementDataAccessPlanHelper() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 判断计划是否允许访问指定 Client。
     *
     * @param plan 数据访问计划
     * @param client Client 实体
     * @return 是否允许
     */
    public static boolean isClientAllowed(DataAccessPlan plan, OAuth2RegisteredClientEntity client) {
        return isAllowed(plan, clientDimensions(client));
    }

    /**
     * 判断计划是否允许访问指定应用授权。
     *
     * @param plan 数据访问计划
     * @param authorization 应用授权投影
     * @return 是否允许
     */
    public static boolean isApplicationAuthorizationAllowed(
            DataAccessPlan plan, AkskApplicationAuthorizationEntity authorization,
            OAuth2RegisteredClientEntity client) {
        if (authorization == null || client == null) {
            return false;
        }
        Map<String, String> dimensions = clientDimensions(client);
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_APPLICATION_CODE,
                authorization.getApplicationCode());
        return isAllowed(plan, dimensions);
    }

    /**
     * 判断计划是否允许访问指定 Token。
     *
     * @param plan 数据访问计划
     * @param token Token 信息
     * @return 是否允许
     */
    public static boolean isTokenAllowed(DataAccessPlan plan, TokenInfo token) {
        if (token == null) {
            return false;
        }
        Map<String, String> dimensions = new LinkedHashMap<String, String>();
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_TOKEN_ID, token.getId());
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_ID, token.getClientId());
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                clientTypeValue(token.getClientType()));
        putOwnerUserId(dimensions, token.getOwnerUserId());
        return isAllowed(plan, dimensions);
    }

    /**
     * 判断计划是否允许为指定 Client 创建应用授权。
     *
     * @param plan 数据访问计划
     * @param client Client 实体
     * @param applicationCode 应用编码
     * @return 是否允许
     */
    public static boolean isApplicationAuthorizationCreateAllowed(
            DataAccessPlan plan, OAuth2RegisteredClientEntity client, String applicationCode) {
        Map<String, String> dimensions = clientDimensions(client);
        if (dimensions == null) {
            return false;
        }
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_APPLICATION_CODE, applicationCode);
        return isAllowed(plan, dimensions);
    }

    /**
     * 判断计划是否允许创建指定类型的 Client。
     *
     * @param plan 数据访问计划
     * @param clientType Client 类型
     * @param ownerUserId 所属用户标识
     * @return 是否允许
     */
    public static boolean isCreateAllowed(DataAccessPlan plan, ClientType clientType, String ownerUserId) {
        Map<String, String> dimensions = new LinkedHashMap<String, String>();
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                clientType == null ? null : clientType.getValue());
        putOwnerUserId(dimensions, ownerUserId);
        return isAllowed(plan, dimensions);
    }

    private static Map<String, String> clientDimensions(OAuth2RegisteredClientEntity client) {
        if (client == null) {
            return null;
        }
        Map<String, String> dimensions = new LinkedHashMap<String, String>();
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_ID, client.getClientId());
        dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                clientTypeValue(client.getClientType()));
        putOwnerUserId(dimensions, client.getOwnerUserId());
        return dimensions;
    }

    private static boolean isAllowed(DataAccessPlan plan, Map<String, String> dimensions) {
        if (plan == null || dimensions == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            return false;
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return true;
        }
        for (DataGrant grant : plan.getGrants()) {
            if (matchesGrant(grant, dimensions)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGrant(DataGrant grant, Map<String, String> dimensions) {
        for (DataConstraint constraint : grant.getConstraints()) {
            String targetValue = dimensions.get(constraint.getDimension());
            if (targetValue == null || !constraint.getValues().contains(targetValue)) {
                return false;
            }
        }
        return true;
    }

    private static void putOwnerUserId(Map<String, String> dimensions, String ownerUserId) {
        if (ownerUserId != null) {
            dimensions.put(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID, ownerUserId);
        }
    }

    private static String clientTypeValue(Integer clientType) {
        ClientType resolved = ClientType.fromCode(clientType);
        return resolved == null ? null : resolved.getValue();
    }
}
