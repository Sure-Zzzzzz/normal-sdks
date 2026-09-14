package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Portal 应用根节点全量排序快照。
 */
@Getter
@Builder
public class PortalApplicationOrderResponse {

    private final Long version;
    private final List<PortalApplicationOrderItem> applications;
}
