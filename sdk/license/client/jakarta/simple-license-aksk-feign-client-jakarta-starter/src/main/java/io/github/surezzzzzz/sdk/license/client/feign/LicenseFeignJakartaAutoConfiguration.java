package io.github.surezzzzzz.sdk.license.client.feign;

import feign.Feign;
import feign.RequestInterceptor;
import feign.Retryer;
import io.github.surezzzzzz.sdk.license.client.constant.ClientProtocolConstant;
import io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.ClientLease;
import io.github.surezzzzzz.sdk.license.client.model.ClientReservation;
import io.github.surezzzzzz.sdk.license.client.model.GrantCandidate;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionPage;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionRequest;
import io.github.surezzzzzz.sdk.license.client.model.QuotaRequest;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import io.github.surezzzzzz.sdk.license.client.support.ClientRequestHelper;
import io.github.surezzzzzz.sdk.license.client.support.ClientResponseHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * License AKSK Feign 传输装配：严格映射公开 HTTP 契约并复用官方认证拦截器。
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnProperty(name = "io.github.surezzzzzz.sdk.license.client.feign.enabled", havingValue = "true")
public class LicenseFeignJakartaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean({LicenseServerClient.class, LicenseContractFeign.class})
    public LicenseContractFeign licenseContractFeign(
            @Qualifier("akskFeignRequestInterceptor") RequestInterceptor authentication,
            ObjectFactory<HttpMessageConverters> converters,
            @Value("${io.github.surezzzzzz.sdk.license.client.base-url}") String baseUrl) {
        // 缺少宿主转换器或官方认证底座时启动失败，不能注册匿名或空接口的假客户端。
        converters.getObject();
        return Feign.builder().contract(new SpringMvcContract())
                .encoder(new SpringEncoder(converters)).decoder(new SpringDecoder(converters))
                .requestInterceptor(authentication).retryer(Retryer.NEVER_RETRY)
                .target(LicenseContractFeign.class, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(LicenseServerClient.class)
    public LicenseServerClient licenseFeignClient(LicenseContractFeign contractFeign) {
        return new JakartaFeignLicenseServerClient(contractFeign);
    }

    /** 真实 HTTP 契约；认证只作用于本客户端，不污染宿主的其他 Feign 客户端。 */
    public interface LicenseContractFeign {

        @PostMapping("/api/license/v1/decisions")
        Map<String, Object> decisions(@RequestBody Map<String, Object> body);

        @PostMapping("/api/license/v1/grant-selections")
        Map<String, Object> grantSelections(@RequestBody Map<String, Object> body);

        @PostMapping("/api/license/v1/quota-reservations")
        Map<String, Object> quotaReservations(@RequestBody Map<String, Object> body);

        @DeleteMapping("/api/license/v1/quota-reservations/{reservationId}")
        void release(@PathVariable("reservationId") String reservationId);

        @PutMapping("/api/license/v1/quota-reservations/{reservationId}/renewal")
        Map<String, Object> renewal(@PathVariable("reservationId") String reservationId,
                                   @RequestBody Map<String, Object> body);

        @GetMapping("/api/license/v1/products/{productId}/trust-material")
        Map<String, Object> trustMaterial(@PathVariable("productId") String productId,
                                         @RequestParam("knownSequence") long knownSequence);
    }

    /** 默认传输；失败传播给调用方，不把网络异常改写为业务拒绝。 */
    public static final class JakartaFeignLicenseServerClient implements LicenseServerClient {

        private static final Log LOGGER = LogFactory.getLog(JakartaFeignLicenseServerClient.class);
        private final LicenseContractFeign contractFeign;

        public JakartaFeignLicenseServerClient(LicenseContractFeign contractFeign) {
            this.contractFeign = contractFeign;
        }

        @Override
        public ClientDecision authorize(AuthorizationRequest request) {
            Map<String, Object> body = ClientRequestHelper.decision(request.getProductId(),
                    request.getGrantId(), request.getSubjectKind(), request.getAction(), request.getActivationId(),
                    request.getAssignmentId(), request.getBusinessOperationId(), request.getFacts(),
                    request.getReservationReferences());
            return authorizeBody(body, request);
        }

        @Override
        public ClientDecision authorize(String productId, String grantId, String subjectKind,
                                        String action, String activationId, String assignmentId,
                                        String businessOperationId, Map<String, Object> facts) {
            AuthorizationRequest request = requestContext(productId, grantId, subjectKind, action,
                    activationId, assignmentId, businessOperationId, facts);
            return authorizeBody(ClientRequestHelper.decision(productId, grantId, subjectKind, action,
                    activationId, assignmentId, businessOperationId, facts, Collections.<String>emptyList()), request);
        }

        private ClientDecision authorizeBody(Map<String, Object> body, AuthorizationRequest request) {
            LOGGER.debug("License request operation=decisions");
            Map<String, Object> response = contractFeign.decisions(body);
            return ClientResponseValidator.validateDecision(ClientResponseValidator.parseDecision(response), request);
        }

        private AuthorizationRequest requestContext(String productId, String grantId, String subjectKind,
                                                    String action, String activationId, String assignmentId,
                                                    String businessOperationId, Map<String, Object> facts) {
            AuthorizationRequest request;
            if ("DEPLOYMENT".equals(subjectKind)) {
                request = AuthorizationRequest.deployment(productId, grantId, action, activationId, facts);
            } else if (assignmentId != null) {
                request = AuthorizationRequest.assigned(productId, grantId, subjectKind, action, assignmentId, facts);
            } else {
                request = AuthorizationRequest.of(productId, grantId, subjectKind, action, facts);
            }
            return businessOperationId == null ? request : request.withBusinessOperationId(businessOperationId);
        }

        @Override
        public List<GrantCandidate> discoverGrants(String productId, String subjectKind, String action) {
            return ClientResponseHelper.allSelections(new GrantSelectionRequest(productId, subjectKind, action,
                    null, 1, ClientProtocolConstant.SELECTION_SIZE_DEFAULT), this::discoverGrants);
        }

        @Override
        public GrantSelectionPage discoverGrants(GrantSelectionRequest request) {
            Map<String, Object> body = ClientRequestHelper.selection(request);
            LOGGER.debug("License request operation=grant-selections");
            return ClientResponseHelper.grantSelection(contractFeign.grantSelections(body), request);
        }

        @Override
        public ClientLease leaseRenew(String grantId, String subjectKind) {
            // 旧便捷签名无法提供授权修订和绑定，服务端租约任务契约尚未接线，禁止发出假请求。
            throw new UnsupportedOperationException("租约必须使用完整绑定与修订上下文，当前传输不支持此契约");
        }

        @Override
        public ClientReservation reserve(QuotaRequest request) {
            Map<String, Object> body = ClientRequestHelper.quota(request, "CONCURRENT");
            LOGGER.debug("License request operation=quota-reserve");
            return ClientResponseHelper.quotaReservation(contractFeign.quotaReservations(body), request, "CONCURRENT");
        }

        @Override
        public ClientReservation consume(QuotaRequest request) {
            Map<String, Object> body = ClientRequestHelper.quota(request, "CONSUMPTION");
            LOGGER.debug("License request operation=quota-consume");
            return ClientResponseHelper.quotaReservation(contractFeign.quotaReservations(body), request, "CONSUMPTION");
        }

        @Override
        public ClientReservation reserve(String grantId, String metric, long quantity, String businessOperationId) {
            return reserve(new QuotaRequest(grantId, metric, quantity, businessOperationId));
        }

        @Override
        public ClientReservation consume(String grantId, String metric, long quantity, String businessOperationId) {
            return consume(new QuotaRequest(grantId, metric, quantity, businessOperationId));
        }

        @Override
        public void release(String reservationId) {
            LOGGER.debug("License request operation=quota-release");
            contractFeign.release(reservationId);
        }

        @Override
        public ClientReservation renewReservation(String reservationId, long expectedFence) {
            ClientResponseHelper.requireSafeSequence(expectedFence);
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("expectedFence", expectedFence);
            LOGGER.debug("License request operation=quota-renewal");
            return ClientResponseHelper.renewedReservation(contractFeign.renewal(reservationId, body), reservationId, expectedFence);
        }

        @Override
        public TrustMaterialSnapshot fetchTrustMaterial(String issuerId, String productId, long knownSequence) {
            ClientResponseHelper.requireSafeSequence(knownSequence);
            LOGGER.debug("License request operation=trust-material");
            Map<String, Object> response = contractFeign.trustMaterial(productId, knownSequence);
            TrustMaterialSnapshot snapshot = ClientResponseHelper.trustMaterial(response, issuerId, productId);
            LOGGER.debug("License response validated operation=trust-material");
            return snapshot;
        }

    }
}
