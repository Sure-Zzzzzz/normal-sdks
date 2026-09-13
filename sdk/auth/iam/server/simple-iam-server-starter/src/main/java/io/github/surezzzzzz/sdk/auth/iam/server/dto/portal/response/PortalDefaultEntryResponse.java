package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Portal 读模型中的应用默认入口。
 */
@Getter
@Builder
public class PortalDefaultEntryResponse {

    private final String pageMenuCode;
    private final String path;
}
