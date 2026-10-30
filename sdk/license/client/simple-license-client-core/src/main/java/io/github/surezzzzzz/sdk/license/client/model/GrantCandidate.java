package io.github.surezzzzzz.sdk.license.client.model;

/**
 * 候选授权描述：发现资料不是许可，后续 authorize 必须明确选一份。
 *
 * @author surezzzzzz
 */
public final class GrantCandidate {

    private final String grantId;
    private final String mode;
    private final long modelVersion;
    private final String commercialPhase;

    public GrantCandidate(String grantId, String mode, long modelVersion, String commercialPhase) {
        this.grantId = grantId;
        this.mode = mode;
        this.modelVersion = modelVersion;
        this.commercialPhase = commercialPhase;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getMode() {
        return mode;
    }

    public long getModelVersion() {
        return modelVersion;
    }

    public String getCommercialPhase() {
        return commercialPhase;
    }
}
