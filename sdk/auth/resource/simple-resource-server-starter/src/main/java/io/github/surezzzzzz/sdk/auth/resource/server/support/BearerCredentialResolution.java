package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import lombok.Getter;

/**
 * Bearer凭据解析结果。
 *
 * @author surezzzzzz
 */
@Getter
public final class BearerCredentialResolution {

    private final BearerResourceCredential credential;
    private final ResourceAuthenticationFailureCategory failureCategory;

    private BearerCredentialResolution(BearerResourceCredential credential,
                                       ResourceAuthenticationFailureCategory failureCategory) {
        this.credential = credential;
        this.failureCategory = failureCategory;
    }

    public static BearerCredentialResolution resolved(BearerResourceCredential credential) {
        return new BearerCredentialResolution(credential, null);
    }

    public static BearerCredentialResolution rejected(ResourceAuthenticationFailureCategory failureCategory) {
        return new BearerCredentialResolution(null, failureCategory);
    }

    public boolean isResolved() {
        return credential != null;
    }
}
