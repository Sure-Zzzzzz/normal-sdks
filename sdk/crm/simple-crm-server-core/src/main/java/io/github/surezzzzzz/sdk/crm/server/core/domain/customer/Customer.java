package io.github.surezzzzzz.sdk.crm.server.core.domain.customer;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * CRM 交易关系主体。
 *
 * @author surezzzzzz
 */
@Getter
public final class Customer {

    private final String customerId;
    private final String displayName;
    private final CustomerState state;
    private final String owner;
    /**
     * 并入目标客户标识（仅 MERGED 态非空）。
     */
    private final String mergedIntoCustomerId;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Customer。
     *
     * @param customerId           客户唯一标识
     * @param displayName          展示名称
     * @param state                业务状态
     * @param owner                归属主体（iam:/aksk: 前缀）
     * @param mergedIntoCustomerId 并入目标客户标识（仅 MERGED 态必填）
     * @param aggregateVersion     聚合版本
     * @param createdAt            创建时间
     * @param updatedAt            变更后的更新时间
     *
     */
    public Customer(String customerId, String displayName, CustomerState state,
                    String owner, String mergedIntoCustomerId, long aggregateVersion,
                    Instant createdAt, Instant updatedAt) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.owner = CrmValidationHelper.required(owner, "owner");
        if (state == CustomerState.MERGED) {
            this.mergedIntoCustomerId = CrmValidationHelper.required(mergedIntoCustomerId,
                    "mergedIntoCustomerId");
        } else {
            this.mergedIntoCustomerId = CrmValidationHelper.optional(mergedIntoCustomerId);
        }
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
    }


    /**
     * 停用：停用后不进新报价、不新增联系人，历史不受影响。
     *
     * @param updatedAt 变更后的更新时间
     * @return 停用后的客户事实
     */
    public Customer deactivated(Instant updatedAt) {
        if (state == CustomerState.INACTIVE) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer already inactive");
        }
        return new Customer(customerId, displayName, CustomerState.INACTIVE, owner, mergedIntoCustomerId,
                aggregateVersion + 1L, createdAt, updatedAt);
    }

    /**
     * 复启：停用后可重新启用。
     *
     * @param updatedAt 变更后的更新时间
     * @return 复启后的客户事实
     */
    public Customer activated(Instant updatedAt) {
        if (state == CustomerState.ACTIVE) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer already active");
        }
        return new Customer(customerId, displayName, CustomerState.ACTIVE, owner, mergedIntoCustomerId,
                aggregateVersion + 1L, createdAt, updatedAt);
    }

    /**
     * 并入目标客户：终态 MERGED，记录指向目标；联系人/未完结报价/已确认事实的迁移由调用方同事务编排。
     *
     * @param targetCustomerId 目标客户唯一标识
     * @param updatedAt        变更后的更新时间
     * @return 合并后的源客户事实
     */
    public Customer mergedInto(String targetCustomerId, Instant updatedAt) {
        if (state == CustomerState.MERGED || targetCustomerId == null
                || targetCustomerId.equals(customerId)) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer cannot be merged");
        }
        return new Customer(customerId, displayName, CustomerState.MERGED, owner, targetCustomerId,
                aggregateVersion + 1L, createdAt, updatedAt);
    }

    /**
     * 归属转移：owner 改指新归属主体（已确认历史事实不追改，由调用方编排未完结报价连带）。
     *
     * @param newOwner  新归属主体（iam:/aksk: 前缀）
     * @param updatedAt 变更后的更新时间
     * @return 转移后的客户事实
     */
    public Customer transferredTo(String newOwner, Instant updatedAt) {
        CrmValidationHelper.required(newOwner, "newOwner");
        if (state == CustomerState.MERGED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "merged customer cannot be transferred");
        }
        return new Customer(customerId, displayName, state, newOwner, mergedIntoCustomerId,
                aggregateVersion + 1L, createdAt, updatedAt);
    }
}
