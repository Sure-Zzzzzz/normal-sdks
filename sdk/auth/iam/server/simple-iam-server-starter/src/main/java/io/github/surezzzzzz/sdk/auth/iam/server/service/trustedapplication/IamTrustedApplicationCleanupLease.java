package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 删除 worker 领取到的不可公开租约凭据。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class IamTrustedApplicationCleanupLease {

    private final Long operationId;
    private final String leaseOwner;
}
