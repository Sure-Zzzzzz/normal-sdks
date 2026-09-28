package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import lombok.Value;

/**
 * 自助 AKU 操作可用的稳定身份源人员身份。
 */
@Value
public class AkskSelfServicePrincipal {

    String ownerSourceId;
    String ownerSubjectId;
    String requestId;
}
