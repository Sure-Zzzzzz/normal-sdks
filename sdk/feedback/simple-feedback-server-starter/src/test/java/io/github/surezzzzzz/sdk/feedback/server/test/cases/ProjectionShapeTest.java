package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * verify 桩投影与 IAM claim 映射器契约对齐测试（协作测试的投影 JSON 形状在此维护：
 * 字段精确集 15 项、Instant 为 epoch 秒、constraint 含 operator——任何一项不符映射即拒）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication.class)
class ProjectionShapeTest {

    private static final String GRANTS_TEMPLATE =
            "{\"protocol\":\"simple-data-permission\",\"version\":\"1.0\",\"grants\":[%s]}";

    private static String authBody(String subject, String codes, String grants) {
        return "{\"sub\":\"" + subject + "\",\"iam_authorization\":{"
                + "\"protocol\":\"simple-application-authorization\",\"version\":\"1.0\","
                + "\"subjectType\":\"HUMAN\",\"subjectId\":\"" + subject + "\","
                + "\"applicationCode\":\"simple-feedback\",\"admitted\":true,\"roles\":[],"
                + "\"pagePermissions\":[],\"apiPermissions\":[" + codes + "],"
                + "\"dataGrantDocument\":" + grants + ","
                + "\"authorizationVersion\":1,\"manifestVersion\":\"1.0\","
                + "\"manifestDigest\":\"stub-digest\",\"issuedAt\":1760000000,"
                + "\"expiresAt\":1823000000}}";
    }

    @Test
    @DisplayName("全权/受限/空授权三种投影都能通过 claim 映射器（失败时输出精确 detail）")
    void testProjectionPassesClaimMapper() throws Exception {
        String allGrants = String.format(GRANTS_TEMPLATE,
                "{\"resource\":\"feedback:submission\",\"actions\":[\"read\",\"manage\"],\"all\":true,"
                        + "\"constraints\":[]},{\"resource\":\"feedback:application\","
                        + "\"actions\":[\"read\",\"manage\"],\"all\":true,\"constraints\":[]}");
        String scopedGrants = String.format(GRANTS_TEMPLATE,
                "{\"resource\":\"feedback:submission\",\"actions\":[\"read\",\"manage\"],\"all\":false,"
                        + "\"constraints\":[{\"dimension\":\"application\",\"operator\":\"IN\","
                        + "\"values\":[\"app-a\"]}]}");
        String emptyGrants = "null";

        String[] projections = {
                authBody("collab-full", "\"feedback.application.read\"", allGrants),
                authBody("collab-scoped", "", scopedGrants),
                authBody("collab-nocodes", "", emptyGrants)
        };
        ObjectMapper mapper = new ObjectMapper();
        for (String projection : projections) {
            JsonNode root = mapper.readTree(projection);
            ApplicationAuthorizationContextClaimMapper.fromClaim(mapper.convertValue(root.get("iam_authorization"), java.util.Map.class));
            log.info("投影映射通过 sub={}", root.get("sub").asText());
        }
    }
}
