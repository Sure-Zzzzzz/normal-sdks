package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 管理面排序抽屉中的 Portal 应用项。
 *
 * <p>位置仅由响应数组顺序表达，不向浏览器暴露数据库 BIGINT 排序值。
 */
@Getter
@Builder
public class PortalApplicationOrderItem {

    private final Long applicationId;
    private final String applicationCode;
    private final String applicationName;
    private final String icon;
    private final boolean enabled;
}
