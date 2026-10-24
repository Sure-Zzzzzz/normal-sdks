package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Portal 可信应用机器路由投影（1.3.8）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class PortalMachineApplicationResponse {

    String applicationCode;
    String applicationName;
    Integer status;
    Boolean portalEnabled;
    String routePrefix;
    String entry;
    String apiBase;
    String targetOrigin;
    Long configVersion;
    String forwardAuthorization;
    List<String> forwardCookieNames;
}
