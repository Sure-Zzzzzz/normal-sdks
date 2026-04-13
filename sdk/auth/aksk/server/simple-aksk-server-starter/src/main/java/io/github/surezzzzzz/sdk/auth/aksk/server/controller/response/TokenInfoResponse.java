package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Token Information Response
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TokenInfoResponse extends TokenInfo {
    // 可以在此添加额外的响应字段，如meta、timestamp等
}
