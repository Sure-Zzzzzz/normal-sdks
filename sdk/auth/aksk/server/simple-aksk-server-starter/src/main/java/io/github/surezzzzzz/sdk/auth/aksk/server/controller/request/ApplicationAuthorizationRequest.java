package io.github.surezzzzzz.sdk.auth.aksk.server.controller.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 应用授权完整替换请求。
 *
 * @author surezzzzzz
 */
@Data
public class ApplicationAuthorizationRequest {

    private String applicationCode;
    private Boolean admitted;
    private List<String> roles;
    private List<String> pagePermissions;
    private List<String> apiPermissions;
    private Map<String, Object> dataGrantDocument;
    private String manifestVersion;
    private String manifestDigest;
}
