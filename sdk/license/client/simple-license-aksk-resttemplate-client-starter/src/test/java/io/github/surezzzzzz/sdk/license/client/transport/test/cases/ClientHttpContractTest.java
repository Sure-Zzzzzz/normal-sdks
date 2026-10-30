package io.github.surezzzzzz.sdk.license.client.transport.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest;
import io.github.surezzzzzz.sdk.license.client.model.ClientReservation;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionPage;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionRequest;
import io.github.surezzzzzz.sdk.license.client.model.QuotaRequest;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import io.github.surezzzzzz.sdk.license.client.transport.LicenseClientTransportAutoConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

/**
 * 真实回环 HTTP 请求/响应核验，不使用 mock 传输来证明端点、方法和字段透传。
 *
 * @author surezzzzzz
 */
@Slf4j
class ClientHttpContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private LicenseServerClient client;
    private volatile String response = "{\"reservationId\":\"reservation\",\"grantId\":\"grant\","
            + "\"metric\":\"capacity\",\"quantity\":2,\"kind\":\"CONCURRENT\",\"action\":\"run\","
            + "\"grantRevision\":7,\"businessOperationId\":\"operation\",\"fence\":3,\"validUntil\":500,"
            + "\"state\":\"ACTIVE\"}";
    private volatile String method;
    private volatile String path;
    private volatile String query;
    private volatile JsonNode body;
    private volatile boolean authenticated;
    private volatile boolean paginateCandidates;
    private volatile boolean duplicateCandidates;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                method = exchange.getRequestMethod();
                path = exchange.getRequestURI().getPath();
                query = exchange.getRequestURI().getRawQuery();
                authenticated = "present".equals(exchange.getRequestHeaders().getFirst("X-Test-Authentication"));
                ByteArrayOutputStream received = new ByteArrayOutputStream();
                try (InputStream input = exchange.getRequestBody()) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        received.write(buffer, 0, count);
                    }
                }
                byte[] input = received.toByteArray();
                body = input.length == 0 ? null : mapper.readTree(input);
                requests.incrementAndGet();
                String outgoing = response;
                if (paginateCandidates) {
                    int selectedPage = body.path("page").intValue();
                    int selectedSize = body.path("size").intValue();
                    ObjectNode selected = mapper.createObjectNode();
                    selected.put("total", 41).put("page", selectedPage).put("size", selectedSize);
                    selected.put("totalPages", (41 + selectedSize - 1) / selectedSize);
                    ArrayNode items = selected.putArray("items");
                    int first = (selectedPage - 1) * selectedSize + 1;
                    for (int index = first; index <= Math.min(41, first + selectedSize - 1); index++) {
                        int id = duplicateCandidates && index == 21 ? 1 : index;
                        items.addObject().put("grantId", "grant-" + id).put("mode", "ONLINE")
                                .put("modelVersion", 1).put("commercialPhase", "ACTIVE");
                    }
                    outgoing = mapper.writeValueAsString(selected);
                }
                byte[] output = outgoing.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, output.length);
                exchange.getResponseBody().write(output);
            } finally {
                exchange.close();
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        RestTemplate template = new RestTemplate();
        template.getInterceptors().add((request, input, execution) -> {
            request.getHeaders().add("X-Test-Authentication", "present");
            return execution.execute(request, input);
        });
        client = new LicenseClientTransportAutoConfiguration.RestTemplateLicenseServerClient(template, baseUrl);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void decisionPreservesOperationReferencesAndActualBinding() {
        response = "{\"allowed\":true,\"action\":\"run\",\"modelVersion\":1,"
                + "\"commercialPhase\":\"ACTIVE\",\"evaluatedAt\":1780000000,"
                + "\"recheckAt\":null,\"reasonCategory\":\"ENTITLED\","
                + "\"requestId\":\"request-test\",\"reservationFenceReferences\":[],"
                + "\"productId\":\"product\",\"grantId\":\"grant\","
                + "\"subjectBinding\":{\"kind\":\"SERVICE\",\"subjectRef\":{"
                + "\"kind\":\"SERVICE\",\"sourceId\":\"iam\",\"subjectId\":\"service-1\"},"
                + "\"assignmentId\":\"assignment\",\"assignmentGeneration\":1},\"grantRevision\":1,\"grantStateVersion\":1}";
        AuthorizationRequest request = AuthorizationRequest.assigned("product", "grant", "SERVICE",
                "run", "assignment", Collections.<String, Object>emptyMap())
                .withBusinessOperationId("operation").withReservationReferences(Collections.singletonList("reservation"));
        log.info("验证真实HTTP判定携带资格、操作编号和额度引用");
        assertThat(client.authorize(request).isAllowed()).isTrue();
        assertThat(method).isEqualTo("POST");
        assertThat(path).isEqualTo("/api/license/v1/decisions");
        assertThat(body.path("assignmentId").asText()).isEqualTo("assignment");
        assertThat(body.path("businessOperationId").asText()).isEqualTo("operation");
        assertThat(body.path("reservationReferences").size()).isEqualTo(1);
        assertThat(body.path("reservationReferences").get(0).asText()).isEqualTo("reservation");
        assertThat(body.has("activationId")).isFalse();
        assertThat(authenticated).isTrue();
        response = "{\"allowed\":true,\"action\":\"run\",\"modelVersion\":1,"
                + "\"commercialPhase\":\"ACTIVE\",\"evaluatedAt\":1780000000,"
                + "\"recheckAt\":null,\"reasonCategory\":\"ENTITLED\","
                + "\"requestId\":\"request-test\",\"reservationFenceReferences\":[],"
                + "\"productId\":\"product\",\"grantId\":\"grant\","
                + "\"subjectBinding\":{\"kind\":\"DEPLOYMENT\",\"activationId\":\"activation\",\"activationGeneration\":1},"
                + "\"grantRevision\":1,\"grantStateVersion\":1}";
        client.authorize(AuthorizationRequest.deployment("product", "grant", "run",
                "activation", Collections.<String, Object>emptyMap()));
        assertThat(body.path("activationId").asText()).isEqualTo("activation");
        assertThat(body.path("reservationReferences").isArray()).isTrue();
        assertThat(body.path("reservationReferences").size()).isZero();
        assertThat(body.has("assignmentId")).isFalse();
        response = response.replace("\"modelVersion\":1", "\"modelVersion\":1.5");
        assertThatThrownBy(() -> client.authorize(request))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void quotaPreservesCompleteServiceAndDeploymentContext() {
        QuotaRequest request = new QuotaRequest("product", "grant", "SERVICE", "run",
                "capacity", 2, "operation", 7, "assignment", null);
        log.info("验证真实HTTP额度请求的完整上下文和联合绑定");
        ClientReservation reservation = client.reserve(request);
        assertThat(reservation.getFence()).isEqualTo(3);
        assertThat(reservation.getGrantId()).isEqualTo("grant");
        assertThat(reservation.getAction()).isEqualTo("run");
        assertThat(reservation.getKind()).isEqualTo("CONCURRENT");
        assertThat(reservation.getGrantRevision()).isEqualTo(7);
        assertThat(reservation.getBusinessOperationId()).isEqualTo("operation");
        assertThat(reservation.getState()).isEqualTo("ACTIVE");
        assertThat(reservation.isActive()).isTrue();
        assertThat(path).isEqualTo("/api/license/v1/quota-reservations");
        assertThat(method).isEqualTo("POST");
        assertThat(body.path("productId").asText()).isEqualTo("product");
        assertThat(body.path("grantId").asText()).isEqualTo("grant");
        assertThat(body.path("subjectKind").asText()).isEqualTo("SERVICE");
        assertThat(body.path("action").asText()).isEqualTo("run");
        assertThat(body.path("metric").asText()).isEqualTo("capacity");
        assertThat(body.path("quantity").longValue()).isEqualTo(2);
        assertThat(body.path("businessOperationId").asText()).isEqualTo("operation");
        assertThat(body.path("expectedGrantRevision").longValue()).isEqualTo(7);
        assertThat(body.path("assignmentId").asText()).isEqualTo("assignment");
        assertThat(body.path("kind").asText()).isEqualTo("CONCURRENT");
        assertThat(body.has("activationId")).isFalse();
        assertThat(authenticated).isTrue();
        response = response.replace("\"kind\":\"CONCURRENT\"", "\"kind\":\"CONSUMPTION\"")
                .replace("\"businessOperationId\":\"operation\"", "\"businessOperationId\":\"operation-two\"");
        client.consume(new QuotaRequest("product", "grant", "DEPLOYMENT", "run",
                "capacity", 2, "operation-two", 7, null, "activation"));
        assertThat(body.path("kind").asText()).isEqualTo("CONSUMPTION");
        assertThat(body.path("activationId").asText()).isEqualTo("activation");
        assertThat(body.has("assignmentId")).isFalse();
    }

    @Test
    void incompleteConvenienceCallsFailBeforeAnyHttpRequest() {
        log.info("验证旧额度便捷入口及缺上下文租约不发出HTTP请求");
        assertThatThrownBy(() -> client.reserve("grant", "capacity", 2, "operation"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.consume(new QuotaRequest("grant", "capacity", 2, "operation")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.leaseRenew("grant", "SERVICE"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> client.discoverGrants("product", "DEPLOYMENT", "run"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }

    @Test
    void releaseUsesDeleteAndRenewalUsesPutWithOnlyExpectedFence() {
        log.info("验证释放DELETE和续占PUT契约及围栏原值");
        client.release("reservation");
        assertThat(method).isEqualTo("DELETE");
        assertThat(path).isEqualTo("/api/license/v1/quota-reservations/reservation");
        assertThat(body).isNull();
        client.renewReservation("reservation", 3);
        assertThat(method).isEqualTo("PUT");
        assertThat(path).isEqualTo("/api/license/v1/quota-reservations/reservation/renewal");
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.path("expectedFence").longValue()).isEqualTo(3);
        assertThatThrownBy(() -> client.renewReservation("reservation", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void discoveryRetainsDeploymentBindingAndConvenienceLoadsEveryPage() {
        paginateCandidates = true;
        log.info("验证部署完整候选分页和旧入口自动获取所有页");
        GrantSelectionPage selected = client.discoverGrants(
                new GrantSelectionRequest("product", "DEPLOYMENT", "run", "activation", 2, 2));
        assertThat(selected.getItems()).extracting(item -> item.getGrantId()).containsExactly("grant-3", "grant-4");
        assertThat(selected.getTotal()).isEqualTo(41);
        assertThat(selected.getPage()).isEqualTo(2);
        assertThat(selected.getSize()).isEqualTo(2);
        assertThat(selected.getTotalPages()).isEqualTo(21);
        assertThat(body.path("activationId").asText()).isEqualTo("activation");
        assertThat(path).isEqualTo("/api/license/v1/grant-selections");
        assertThat(method).isEqualTo("POST");
        assertThat(authenticated).isTrue();
        assertThat(client.discoverGrants("product", "SERVICE", "run")).hasSize(41)
                .extracting(item -> item.getGrantId()).startsWith("grant-1", "grant-2").endsWith("grant-40", "grant-41");
        assertThat(requests.get()).isEqualTo(4);
        assertThat(body.path("page").intValue()).isEqualTo(3);
        assertThat(body.has("activationId")).isFalse();
    }

    @Test
    void discoveryRejectsMalformedPagesEnumsAndDuplicateIdentifiers() {
        GrantSelectionRequest request = new GrantSelectionRequest("product", "SERVICE", "run", null, 1, 20);
        response = "{\"items\":[],\"total\":0,\"page\":1,\"size\":20,\"totalPages\":0}";
        log.info("验证空候选合法，坏分页、坏枚举和跨页重复编号均拒绝");
        assertThat(client.discoverGrants(request).getItems()).isEmpty();
        String valid = "{\"items\":[{\"grantId\":\"grant\",\"mode\":\"ONLINE\",\"modelVersion\":1,"
                + "\"commercialPhase\":\"ACTIVE\"}],\"total\":1,\"page\":1,\"size\":20,\"totalPages\":1}";
        String[] invalid = {
                valid.replace("\"page\":1", "\"page\":2"),
                valid.replace("\"totalPages\":1", "\"totalPages\":2"),
                valid.replace("\"total\":1", "\"total\":2"),
                valid.replace("\"modelVersion\":1", "\"modelVersion\":1.5"),
                valid.replace("\"mode\":\"ONLINE\"", "\"mode\":\"UNKNOWN\""),
                valid.replace("\"commercialPhase\":\"ACTIVE\"", "\"commercialPhase\":\"UNKNOWN\"")
        };
        for (String malformed : invalid) {
            response = malformed;
            assertThatThrownBy(() -> client.discoverGrants(request))
                    .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        }
        paginateCandidates = true;
        duplicateCandidates = true;
        assertThatThrownBy(() -> client.discoverGrants("product", "SERVICE", "run"))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class)
                .hasMessageContaining("重复编号");
    }

    @Test
    void permanentQuotaDeadlineAcceptsOnlyTheExactDefinedIntegerMarker() {
        QuotaRequest request = new QuotaRequest("product", "grant", "SERVICE", "run",
                "capacity", 2, "operation", 7, "assignment", null);
        String initial = response.replace("\"kind\":\"CONCURRENT\"", "\"kind\":\"CONSUMPTION\"");
        response = initial.replace("\"validUntil\":500", "\"validUntil\":9223372036854775807");
        log.info("验证永久截止只接受精确最大long标记，其他不安全数和错误类型拒绝");
        assertThat(client.consume(request).getValidUntil()).isEqualTo(Long.MAX_VALUE);
        String[] invalid = {"9007199254740992", "9223372036854775806", "9223372036854775808",
                "9.223372036854776E18", "\"9223372036854775807\""};
        for (String deadline : invalid) {
            response = initial.replace("\"validUntil\":500", "\"validUntil\":" + deadline);
            assertThatThrownBy(() -> client.consume(request))
                    .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        }
    }

    @Test
    void quotaReceiptPreservesTerminalStatesAndRejectsOtherOperationContexts() {
        QuotaRequest request = new QuotaRequest("product", "grant", "SERVICE", "run",
                "capacity", 2, "operation", 7, "assignment", null);
        String initial = response;
        log.info("验证额度幂等终态保留及请求关联、完整字段和安全整数");
        for (String state : new String[]{"RELEASED", "EXPIRED"}) {
            response = initial.replace("\"state\":\"ACTIVE\"", "\"state\":\"" + state + "\"");
            ClientReservation terminal = client.reserve(request);
            assertThat(terminal.getState()).isEqualTo(state);
            assertThat(terminal.isActive()).isFalse();
        }
        String[] invalid = {
                initial.replace("\"grantId\":\"grant\"", "\"grantId\":\"other\""),
                initial.replace("\"metric\":\"capacity\"", "\"metric\":\"other\""),
                initial.replace("\"quantity\":2", "\"quantity\":3"),
                initial.replace("\"kind\":\"CONCURRENT\"", "\"kind\":\"CONSUMPTION\""),
                initial.replace("\"action\":\"run\"", "\"action\":\"other\""),
                initial.replace("\"grantRevision\":7", "\"grantRevision\":8"),
                initial.replace("\"businessOperationId\":\"operation\"", "\"businessOperationId\":\"other\""),
                initial.replace("\"state\":\"ACTIVE\"", "\"state\":\"UNKNOWN\""),
                initial.replace("\"kind\":\"CONCURRENT\"", "\"kind\":\"UNKNOWN\""),
                initial.replace("\"quantity\":2", "\"quantity\":0"),
                initial.replace("\"quantity\":2", "\"quantity\":2.5"),
                initial.replace("\"grantRevision\":7", "\"grantRevision\":9007199254740992"),
                initial.replace("\"fence\":3", "\"fence\":9007199254740992"),
                initial.replace(",\"state\":\"ACTIVE\"", ""),
                initial.replace("\"state\":\"ACTIVE\"", "\"state\":\"ACTIVE\",\"unknown\":true")
        };
        for (String malformed : invalid) {
            response = malformed;
            assertThatThrownBy(() -> client.reserve(request))
                    .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        }
    }

    @Test
    void renewalRejectsAnotherReceiptTerminalStateAndFenceRollback() {
        String initial = response;
        log.info("验证续期只采用同一ACTIVE并发占用及不回退围栏");
        response = initial.replace("\"fence\":3", "\"fence\":4");
        assertThat(client.renewReservation("reservation", 3).getFence()).isEqualTo(4);
        String[] invalid = {
                initial.replace("\"reservationId\":\"reservation\"", "\"reservationId\":\"other\""),
                initial.replace("\"kind\":\"CONCURRENT\"", "\"kind\":\"CONSUMPTION\""),
                initial.replace("\"state\":\"ACTIVE\"", "\"state\":\"RELEASED\""),
                initial.replace("\"state\":\"ACTIVE\"", "\"state\":\"EXPIRED\""),
                initial.replace("\"fence\":3", "\"fence\":2")
        };
        for (String malformed : invalid) {
            response = malformed;
            assertThatThrownBy(() -> client.renewReservation("reservation", 3))
                    .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        }
    }

    private String trustResponse() {
        return "{\"issuerId\":\"issuer\",\"productId\":\"product\",\"sequence\":2,"
                + "\"issuedAt\":100,\"snapshotId\":\"snapshot\",\"manifestJws\":\"manifest\","
                + "\"revocationPartJws\":[\"part\"],\"trustedPublicKeysJson\":[\"key\"]}";
    }

    @Test
    void trustMaterialChecksDomainAndDoesNotCoerceResponseTypes() {
        response = trustResponse();
        log.info("验证真实HTTP信任资料范围关联和严格整数、文本类型");
        assertThat(client.fetchTrustMaterial("issuer", "product", 1).getSequence()).isEqualTo(2);
        assertThat(method).isEqualTo("GET");
        assertThat(path).isEqualTo("/api/license/v1/products/product/trust-material");
        assertThat(query).isEqualTo("knownSequence=1");
        assertThat(authenticated).isTrue();
        String[] invalid = {
                trustResponse().replace("\"productId\":\"product\"", "\"productId\":\"other\""),
                trustResponse().replace("\"issuerId\":\"issuer\"", "\"issuerId\":\"other\""),
                trustResponse().replace("\"sequence\":2", "\"sequence\":2.5"),
                trustResponse().replace("\"sequence\":2", "\"sequence\":\"2\""),
                trustResponse().replace("\"sequence\":2", "\"sequence\":9007199254740992"),
                trustResponse().replace("\"issuedAt\":100", "\"issuedAt\":100.5"),
                trustResponse().replace("\"snapshotId\":\"snapshot\"", "\"snapshotId\":7")
        };
        for (String invalidResponse : invalid) {
            response = invalidResponse;
            assertThatThrownBy(() -> client.fetchTrustMaterial("issuer", "product", 1))
                    .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
        }
    }
}
