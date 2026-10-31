package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * License 自有履约关联：一条外部履约只能建立一个授权，一个授权只承接一条履约。
 *
 * <p>与授权草稿同事务插入，数据库唯一键承担跨实例去重。关联保留独立业务编号，
 * 不依赖 CRM 数据库；目标授权被异常移除时保留关联并拒绝重建，避免静默换发。</p>
 *
 * @author surezzzzzz
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "license_fulfillment_grant", uniqueConstraints =
        @UniqueConstraint(name = "uk_license_fulfillment_grant", columnNames = "grant_id"))
public class FulfillmentGrantEntity {

    @Id
    @Column(name = "fulfillment_id", length = 128, nullable = false)
    private String fulfillmentId;

    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 可空包装类型用于识别新实体，确保分配主键时执行 INSERT，不能 merge 覆盖赢家关联。 */
    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    /** 建立不可改绑关联；实际保存须与授权创建位于同一事务。 */
    public FulfillmentGrantEntity(String fulfillmentId, String grantId) {
        this.fulfillmentId = fulfillmentId;
        this.grantId = grantId;
        this.createdAt = Instant.now();
    }
}
