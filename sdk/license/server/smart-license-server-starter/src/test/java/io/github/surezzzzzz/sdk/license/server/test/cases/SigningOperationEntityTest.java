package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 冻结载荷的激活索引：Base64URL 解码、部署联合绑定与非部署无归属。
 *
 * @author surezzzzzz
 */
@Slf4j
class SigningOperationEntityTest {

    @Test
    void deploymentProjectionDecodesPayloadWithoutChangingSigningBytes() {
        String payload = CompactJws.encode(("{\"subjectBinding\":{\"kind\":\"DEPLOYMENT\","
                + "\"activationId\":\"act-一\",\"activationGeneration\":2}}")
                .getBytes(StandardCharsets.UTF_8));
        SigningOperationEntity operation = operation(payload);
        assertThat(operation.getActivationId()).isEqualTo("act-一");
        assertThat(operation.getFrozenPayload()).isEqualTo(payload);
    }

    @Test
    void nonDeploymentAndLegacyPlaceholderHaveNoActivationProjection() {
        String personPayload = CompactJws.encode(("{\"subjectBinding\":{\"kind\":\"PERSON\","
                + "\"sourceId\":\"source-1\",\"subjectId\":\"subject-1\",\"activationId\":\"injected\"}}")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(operation(personPayload).getActivationId()).isNull();
        assertThat(operation("p").getActivationId()).isNull();
        assertThat(operation("{\"protocolVersion\":2}").getActivationId()).isNull();
        assertThat(operation(CompactJws.encode("null".getBytes(StandardCharsets.UTF_8)))
                .getActivationId()).isNull();
    }

    @Test
    void readableDeploymentBindingRequiresTextualNonblankIdentifier() {
        for (String binding : new String[]{"{}", "{\"activationId\":null}",
                "{\"activationId\":42}", "{\"activationId\":\"   \"}"}) {
            String json = "{\"subjectBinding\":{\"kind\":\"DEPLOYMENT\""
                    + (binding.length() > 2 ? "," + binding.substring(1) : "}") + "}";
            String payload = CompactJws.encode(json.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> operation(payload)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("部署凭据必须包含有效的激活编号");
        }
    }

    @Test
    void deploymentProjectionRejectsIdentifierBeyondDatabaseColumnBudget() {
        String identifier = new String(new char[129]).replace('\0', 'a');
        String payload = CompactJws.encode(("{\"subjectBinding\":{\"kind\":\"DEPLOYMENT\","
                + "\"activationId\":\"" + identifier + "\"}}")
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> operation(payload)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部署凭据必须包含有效的激活编号");
    }

    private static SigningOperationEntity operation(String payload) {
        return new SigningOperationEntity("op-1", "cred-1", "gr-1", "profile-1", "header", payload, "idem-1");
    }
}
