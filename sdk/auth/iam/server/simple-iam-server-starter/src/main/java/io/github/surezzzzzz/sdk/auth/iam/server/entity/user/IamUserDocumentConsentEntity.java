package io.github.surezzzzzz.sdk.auth.iam.server.entity.user;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * 用户对登录要求文档版本的同意记录。
 *
 * <p>文档正文由 {@code iam_login_requirement_document} 保存；此表只保存稳定版本标识、
 * 要求集合版本和同意时间，不复制正文、凭据或完整请求内容。</p>
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_user_document_consent", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_document_consent", columnNames = {"user_id", "document_version_id"})
})
public class IamUserDocumentConsentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Column(name = "requirements_revision", nullable = false, length = 128)
    private String requirementsRevision;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;
}
