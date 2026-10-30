package io.github.surezzzzzz.sdk.license.core.spi;

import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import java.util.List;

/**
 * 在线判定客户端端口：Verifier 在线路径的可替换出口。
 *
 * <p>将自有值对象映射到认证 HTTP（经客户端三件套 client-core），请求临时证明不写日志/缓存。
 * 返回的 Decision 必须匹配产品/授权/真实对象/action/版本/围栏，否则协议失败且业务不执行。</p>
 *
 * @author surezzzzzz
 */
public interface OnlineDecisionClient {

    /** 在线判定：每次敏感操作调 Server，不缓存结果跨请求复用。 */
    Decision authorize(OnlineAuthorizationRequest request);

    /** 候选发现：返回权限/资格交集内候选编号与版本。 */
    List<GrantCandidate> discoverGrants(String productId, String subjectKind, String action);

    /** 候选授权描述。 */
    final class GrantCandidate {

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
}
