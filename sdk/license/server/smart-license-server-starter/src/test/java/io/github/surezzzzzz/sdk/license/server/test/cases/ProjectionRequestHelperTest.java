package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.support.ProjectionRequestHelper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 幂等输入只允许对象字段排列变化；主体/目标与真实值变化不能合并。 */
@lombok.extern.slf4j.Slf4j
class ProjectionRequestHelperTest {
    @Test
    void objectFieldOrderIsIgnoredButArrayAndScalarMeaningArePreserved() {
        ObjectNode one = StrictJson.parse("{\"a\":1,\"b\":{\"c\":[false,\"\"],\"d\":2}}", ObjectNode.class);
        ObjectNode two = StrictJson.parse("{\"b\":{\"d\":2,\"c\":[false,\"\"]},\"a\":1}", ObjectNode.class);
        assertThat(ProjectionRequestHelper.inputDigest(one)).isEqualTo(ProjectionRequestHelper.inputDigest(two));
        two.put("a", 2);
        assertThat(ProjectionRequestHelper.inputDigest(one)).isNotEqualTo(ProjectionRequestHelper.inputDigest(two));
    }

    @Test
    void principalAndTargetHaveIndependentRequestNamespaces() {
        String first = ProjectionRequestHelper.requestDigest("iam:a", "/product/p1", "same-key");
        assertThat(first).isNotEqualTo(ProjectionRequestHelper.requestDigest("iam:b", "/product/p1", "same-key"));
        assertThat(first).isNotEqualTo(ProjectionRequestHelper.requestDigest("iam:a", "/product/p2", "same-key"));
        assertThat(first).doesNotContain("same-key");
    }
}
