package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 在线判定请求联合结构断言：部署必须 activationId、人员/服务禁止、消费必须 businessOperationId。
 *
 * @author surezzzzzz
 */
class OnlineRequestTest {

    @Test
    void deploymentRequiresActivationId() {
        assertThatThrownBy(() -> OnlineAuthorizationRequest.deployment(
                "p", "g", "act", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.deployment(
                "p", "g", "act", "activation-1", null, null);
        assertThat(request.getSubjectKind()).isEqualTo(SubjectKind.DEPLOYMENT);
        assertThat(request.getActivationId()).isEqualTo("activation-1");
    }

    @Test
    void personOrServiceRejectsDeploymentKind() {
        assertThatThrownBy(() -> OnlineAuthorizationRequest.personOrService(
                "p", "g", SubjectKind.DEPLOYMENT, "act", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.personOrService(
                "p", "g", SubjectKind.PERSON, "act", "assignment-1", null, null);
        assertThat(request.getActivationId()).isNull();
        assertThat(request.getAssignmentId()).isEqualTo("assignment-1");
    }

    @Test
    void consumptionRequiresBusinessOperationId() {
        OnlineAuthorizationRequest base = OnlineAuthorizationRequest.personOrService(
                "p", "g", SubjectKind.SERVICE, "consume.action", null, null, null);
        assertThatThrownBy(() -> OnlineAuthorizationRequest.consumption(base, null))
                .isInstanceOf(IllegalArgumentException.class);
        OnlineAuthorizationRequest consumption = OnlineAuthorizationRequest.consumption(base, "op-123");
        assertThat(consumption.getBusinessOperationId()).isEqualTo("op-123");
    }

    @Test
    void factsAndReferencesDefensivelyCopied() {
        Map<String, Object> mutableFacts = new HashMap<String, Object>();
        mutableFacts.put("count", 1L);
        java.util.List<String> mutableRefs = new java.util.ArrayList<String>();
        mutableRefs.add("r1");
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.personOrService(
                "p", "g", SubjectKind.PERSON, "act", null, mutableFacts, mutableRefs);
        mutableFacts.put("count", 999L);
        mutableRefs.add("r2");
        assertThat(request.getFacts().get("count")).isEqualTo(1L);
        assertThat(request.getReservationReferences()).hasSize(1);
    }
}
