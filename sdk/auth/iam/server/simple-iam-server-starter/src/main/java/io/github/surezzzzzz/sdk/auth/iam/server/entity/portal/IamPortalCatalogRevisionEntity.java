package io.github.surezzzzzz.sdk.auth.iam.server.entity.portal;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * Portal 可信应用路由投影全局 revision 单例。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_portal_catalog_revision")
public class IamPortalCatalogRevisionEntity {

    /**
     * 单例主键。
     */
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private Long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
