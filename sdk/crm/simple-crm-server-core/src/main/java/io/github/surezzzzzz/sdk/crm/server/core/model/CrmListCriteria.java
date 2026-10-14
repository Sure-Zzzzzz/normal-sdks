package io.github.surezzzzzz.sdk.crm.server.core.model;

import lombok.Getter;

import java.time.Instant;

/**
 * 列表结构化过滤条件：状态稳定 code、关联资源标识（联系人按客户、履约按订单）、创建时间窗。
 *
 * <p>全部字段可空（空=不过滤）；行级归属范围由实现按 DATA 计划并入 SQL，不在本条件表达。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmListCriteria {

    private final String stateCode;
    private final String relatedResourceId;
    private final Instant createdFrom;
    private final Instant createdTo;
    private final String keyword;

    /**
     * 创建CrmListCriteria。
     *
     * @param stateCode         状态稳定 code 过滤（可空）
     * @param relatedResourceId 关联资源标识（联系人=客户 ID、履约项=订单 ID，其余资源忽略，可空）
     * @param createdFrom       创建时间下界（含，可空）
     * @param createdTo         创建时间上界（不含，可空）
     */
    public CrmListCriteria(String stateCode, String relatedResourceId, Instant createdFrom, Instant createdTo) {
        this(stateCode, relatedResourceId, createdFrom, createdTo, null);
    }

    /**
     * 创建CrmListCriteria（带关键字）。
     *
     * @param keyword 名称/单号关键字包含匹配（客户=显示名或ID、目录=名称或业务键、报价=单号或客户显示名，可空）
     */
    public CrmListCriteria(String stateCode, String relatedResourceId, Instant createdFrom,
                           Instant createdTo, String keyword) {
        this.stateCode = stateCode;
        this.relatedResourceId = relatedResourceId;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
        this.keyword = keyword;
    }

    /**
     * 空条件（不过滤）。
     *
     * @return 空条件实例
     */
    public static CrmListCriteria none() {
        return new CrmListCriteria(null, null, null, null);
    }
}
