package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.ClientInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Client Information Response
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClientInfoResponse extends ClientInfo {
    /**
     * 自助 AKU 绑定的身份源目标业务应用。
     *
     * <p>仅自助接口填充，管理接口保持原有 ClientInfo 形态。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long targetApplicationId;

    /**
     * OWNER_INHERITED AKU 的并发生命版本。
     *
     * <p>仅自助接口填充；后续写入必须以该值作为 If-Match，避免旧页面覆盖新状态。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long lifecycleVersion;
}
