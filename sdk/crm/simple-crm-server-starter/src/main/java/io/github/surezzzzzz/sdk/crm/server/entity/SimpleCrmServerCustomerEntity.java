package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState;
import lombok.Getter;

import java.time.Instant;

/**
 * 客户档案 JDBC 映射实体（仅服务于数据库映射，不承载领域行为）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerCustomerEntity {

    private final String customerId;
    private final String displayName;
    private final String stateCode;
    private final String owner;
    private final String mergedIntoCustomerId;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerCustomerEntity。
     *
     * @param customerId           客户唯一标识
     * @param displayName          展示名称
     * @param stateCode            状态稳定 code
     * @param owner                归属主体（iam:/aksk: 前缀）
     * @param mergedIntoCustomerId 并入目标客户标识（仅 merged 态非空）
     * @param aggregateVersion     聚合版本
     * @param createdAt            创建时间
     * @param updatedAt            变更时间
     */
    public SimpleCrmServerCustomerEntity(String customerId, String displayName, String stateCode, String owner,
                          String mergedIntoCustomerId, long aggregateVersion, Instant createdAt,
                          Instant updatedAt) {
        this.customerId = customerId;
        this.displayName = displayName;
        this.stateCode = stateCode;
        this.owner = owner;
        this.mergedIntoCustomerId = mergedIntoCustomerId;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 实体转领域事实。
     *
     * @return 客户领域事实
     */
    public Customer toDomain() {
        return new Customer(customerId, displayName, CustomerState.fromCode(stateCode), owner,
                mergedIntoCustomerId, aggregateVersion, createdAt, updatedAt);
    }

    /**
     * 领域事实转实体。
     *
     * @param customer 客户领域事实
     * @return JDBC 映射实体
     */
    public static SimpleCrmServerCustomerEntity from(Customer customer) {
        return new SimpleCrmServerCustomerEntity(customer.getCustomerId(), customer.getDisplayName(),
                customer.getState().getCode(), customer.getOwner(), customer.getMergedIntoCustomerId(),
                customer.getAggregateVersion(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
