package io.github.surezzzzzz.sdk.auth.iam.server.entity.user;

import lombok.Data;

import javax.persistence.*;

/**
 * 登录前置文档的不可变版本正文。
 *
 * <p>用户同意表只引用 {@code versionId}；正文和展示元数据在本表保留，便于复核历史版本。</p>
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_login_requirement_document", uniqueConstraints = {
        @UniqueConstraint(name = "uk_login_requirement_document_version", columnNames = "version_id")
})
public class IamLoginRequirementDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "requirements_revision", nullable = false, length = 128)
    private String requirementsRevision;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "url", nullable = true, length = 512)
    private String url;

    @Column(name = "required", nullable = false)
    private Boolean required;
}
