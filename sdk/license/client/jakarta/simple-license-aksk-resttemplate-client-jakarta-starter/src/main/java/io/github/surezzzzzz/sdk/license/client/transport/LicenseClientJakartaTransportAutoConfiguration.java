package io.github.surezzzzzz.sdk.license.client.transport;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * License AKSK RestTemplate 传输装配，固定复用官方认证 RestTemplate。
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnProperty(name = "io.github.surezzzzzz.sdk.license.client.enabled", havingValue = "true")
public class LicenseClientJakartaTransportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LicenseServerClient.class)
    public LicenseServerClient licenseServerClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            @Value("${io.github.surezzzzzz.sdk.license.client.base-url}") String baseUrl) {
        return new JakartaRestTemplateLicenseServerClient(akskClientRestTemplate, baseUrl);
    }

    /** 默认传输；失败传播给调用方，不把网络异常改写为业务拒绝。 */
    public static final class JakartaRestTemplateLicenseServerClient implements LicenseServerClient {

        private static final Log LOGGER = LogFactory.getLog(JakartaRestTemplateLicenseServerClient.class);
        private final RestTemplate restTemplate;
        private final String baseUrl;

        public JakartaRestTemplateLicenseServerClient(RestTemplate restTemplate, String baseUrl) {
            this.restTemplate = restTemplate;
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
            Map<String, Object> response = exchange("/api/license/v1/decisions", body);
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
            return ClientResponseHelper.grantSelection(exchange("/api/license/v1/grant-selections", body), request);
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
            return ClientResponseHelper.quotaReservation(exchange("/api/license/v1/quota-reservations", body), request, "CONCURRENT");
        }

        @Override
        public ClientReservation consume(QuotaRequest request) {
            Map<String, Object> body = ClientRequestHelper.quota(request, "CONSUMPTION");
            LOGGER.debug("License request operation=quota-consume");
            return ClientResponseHelper.quotaReservation(exchange("/api/license/v1/quota-reservations", body), request, "CONSUMPTION");
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
            restTemplate.delete(baseUrl + "/api/license/v1/quota-reservations/{reservationId}", reservationId);
        }

        @Override
        public ClientReservation renewReservation(String reservationId, long expectedFence) {
            ClientResponseHelper.requireSafeSequence(expectedFence);
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("expectedFence", expectedFence);
            LOGGER.debug("License request operation=quota-renewal");
            return ClientResponseHelper.renewedReservation(renew(reservationId, body), reservationId, expectedFence);
        }

        @Override
        public TrustMaterialSnapshot fetchTrustMaterial(String issuerId, String productId, long knownSequence) {
            ClientResponseHelper.requireSafeSequence(knownSequence);
            LOGGER.debug("License request operation=trust-material");
            Map<String, Object> response = restTemplate.getForObject(baseUrl
                    + "/api/license/v1/products/{productId}/trust-material?knownSequence={knownSequence}",
                    Map.class, productId, knownSequence);
            TrustMaterialSnapshot snapshot = ClientResponseHelper.trustMaterial(response, issuerId, productId);
            LOGGER.debug("License response validated operation=trust-material");
            return snapshot;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> renew(String reservationId, Map<String, Object> body) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/api/license/v1/quota-reservations/{reservationId}/renewal",
                    HttpMethod.PUT, entity(body), Map.class, reservationId);
            return requireResponse(response.getBody());
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> exchange(String path, Map<String, Object> body) {
            ResponseEntity<Map> response = restTemplate.exchange(baseUrl + path, HttpMethod.POST,
                    entity(body), Map.class);
            return requireResponse(response.getBody());
        }

        private HttpEntity<Object> entity(Map<String, Object> body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<Object>(body, headers);
        }

        private Map<String, Object> requireResponse(Map<String, Object> response) {
            if (response == null) {
                throw new ClientResponseValidator.ProtocolViolationException("License HTTP 响应正文缺失");
            }
            return response;
        }
    }
}
