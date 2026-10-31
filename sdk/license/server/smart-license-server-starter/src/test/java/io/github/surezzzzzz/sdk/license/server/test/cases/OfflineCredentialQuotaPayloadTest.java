package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.service.OfflineCredentialComposer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 离线凭据必须回读部署额度分配和固定窗口锚，不能从权益或签发时刻临时推导。
 *
 * @author surezzzzzz
 */
class OfflineCredentialQuotaPayloadTest {

    @Test
    void payloadContainsPersistedAllocationAndWindowAnchor() throws Exception {
        GrantRevisionEntity grant = new GrantRevisionEntity("gr-local-payload", "customer-example",
                "product-example", "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null,
                "mv-local", "{\"channelLimit\":5}")
                .withValidity("FIXED", 1234L, 9999L, 10000L);
        Map<String, Object> allocation = new LinkedHashMap<String, Object>();
        allocation.put("allocationId", "alloc-1");
        allocation.put("metric", "localSeats");
        allocation.put("quantity", 2L);
        allocation.put("grantRevision", grant.getGrantRevision());
        allocation.put("retention", "FIXED");
        allocation.put("retainUntil", 9000L);
        Map<String, Long> anchors = Collections.singletonMap("localSeats", 1234L);

        JsonNode payload = io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(
                OfflineCredentialComposer.compose(grant, product(), localModel(),
                        "{\"kind\":\"DEPLOYMENT\",\"activationId\":\"act-1\","
                                + "\"activationGeneration\":2}", "cred-local", 1500L,
                        Collections.emptyList(), Arrays.asList(allocation), anchors));

        assertThat(payload.path("quotaAllocations").get(0).path("allocationId").asText())
                .isEqualTo("alloc-1");
        assertThat(payload.path("quotaAllocations").get(0).path("quantity").asLong()).isEqualTo(2L);
        assertThat(payload.path("quotaPolicy").path("windowAnchors").path("localSeats").asLong())
                .isEqualTo(1234L);
    }

    @Test
    void fixedWindowAllocationWithoutPersistedAnchorFailsClosed() {
        GrantRevisionEntity grant = new GrantRevisionEntity("gr-local-missing-anchor", "customer-example",
                "product-example", "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null,
                "mv-local", "{\"channelLimit\":5}");
        Map<String, Object> allocation = new LinkedHashMap<String, Object>();
        allocation.put("allocationId", "alloc-1");
        allocation.put("metric", "localSeats");
        allocation.put("quantity", 1L);
        allocation.put("grantRevision", grant.getGrantRevision());
        allocation.put("retention", "PERPETUAL");

        assertThatThrownBy(() -> OfflineCredentialComposer.compose(grant, product(), localModel(),
                "{\"kind\":\"DEPLOYMENT\",\"activationId\":\"act-1\","
                        + "\"activationGeneration\":1}", "cred-missing-anchor", 1500L,
                Collections.emptyList(), Arrays.asList(allocation), Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("固定窗口额度缺少或多出真实窗口锚点");
    }

    private static ProductEntity product() {
        return new ProductEntity("product-example", "issuer-example", "license-example",
                "授权产品", "[\"channel.create\"]", "[\"license-condition/1\",\"subject-binding/1\",\"clock-state/1\",\"quota-local/1\"]");
    }

    private static io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity localModel() {
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity model =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-local", "model-local", "product-example", 1, localSnapshot());
        model.publish();
        return model;
    }

    private static String localSnapshot() {
        return LicenseTestModels.publishedSnapshot()
                .replace("\"quotaDefinitions\":{},\"quotaConstraints\":{}",
                        "\"quotaDefinitions\":{\"localSeats\":{\"limitRight\":\"channelLimit\","
                                + "\"kind\":\"CONSUMPTION\",\"enforcement\":\"LOCAL\","
                                + "\"window\":\"FIXED_WINDOW\",\"periodSeconds\":3600}},"
                                + "\"quotaConstraints\":{}")
                .replace("\"clock-state/1\"]", "\"clock-state/1\",\"quota-local/1\"]");
    }
}
