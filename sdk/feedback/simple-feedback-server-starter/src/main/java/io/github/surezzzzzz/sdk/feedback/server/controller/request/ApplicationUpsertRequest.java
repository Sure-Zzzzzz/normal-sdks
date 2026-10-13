package io.github.surezzzzzz.sdk.feedback.server.controller.request;

import lombok.Data;

/**
 * 应用注册/更新请求（编码创建后不可变；更新仅 displayName/description/enabled/ownerGroup）。
 *
 * <p>同源锁（修正㉔）：编码命中 IAM 目录的行 displayName 不可改写，重同步须显式传
 * syncFromIam=true（服务端抄录 IAM 官方名）。</p>
 *
 * @author surezzzzzz
 */
@Data
public class ApplicationUpsertRequest {

    /**
     * 应用编码（创建必填；更新忽略）
     */
    private String applicationCode;

    /**
     * 门户展示名
     */
    private String displayName;

    /**
     * 应用说明（可选）
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 期望处理组名（备注）
     */
    private String ownerGroup;

    /**
     * 期望行版本（更新必填；创建忽略）
     */
    private Integer expectedRowVersion;

    /**
     * 从 IAM 目录重同步展示名（仅更新生效；同源行专用，散形态/非同源行忽略）
     */
    private Boolean syncFromIam;
}
