package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import io.github.surezzzzzz.sdk.auth.aksk.server.model.TokenStatistics;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Token Statistics Response
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TokenStatisticsResponse extends TokenStatistics {
    // 可以在此添加额外的响应字段，如meta、timestamp等
}
