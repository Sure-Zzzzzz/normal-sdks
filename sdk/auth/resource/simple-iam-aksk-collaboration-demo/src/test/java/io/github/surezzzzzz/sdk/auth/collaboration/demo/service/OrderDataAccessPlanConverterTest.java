package io.github.surezzzzzz.sdk.auth.collaboration.demo.service;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 订单访问计划转换器单元测试。
 *
 * @author surezzzzzz
 */
class OrderDataAccessPlanConverterTest {

    private static final String ORDER_RESOURCE = "order";
    private static final String READ_ACTION = "read";
    private static final String TENANT_ID = "tenantId";
    private static final String DEPARTMENT_ID = "departmentId";

    @Test
    void shouldRecognizeDeniedPlan() {
        DataGrantDocument unrelated = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(
                new DataGrant("other-resource", Collections.singletonList(READ_ACTION), false,
                        Collections.singletonList(constraint(TENANT_ID, "t1")))));
        DataAccessPlan denied = DataAccessPlan.evaluate(unrelated, request());
        assertThat(denied.getOutcome()).isEqualTo(DataAccessOutcome.DENY);
        assertThat(OrderDataAccessPlanConverter.isDenied(denied)).isTrue();
        assertThat(OrderDataAccessPlanConverter.isDenied(null)).isTrue();
    }

    @Test
    void shouldReturnNullSpecificationForAllowAll() {
        DataGrantDocument all = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(
                new DataGrant(ORDER_RESOURCE, Collections.singletonList(READ_ACTION), true,
                        Collections.<DataConstraint>emptyList())));
        DataAccessPlan plan = DataAccessPlan.evaluate(all, request());
        assertThat(plan.getOutcome()).isEqualTo(DataAccessOutcome.ALLOW_ALL);
        assertThat(OrderDataAccessPlanConverter.toSpecification(plan)).isNull();
    }

    @Test
    void shouldFailClosedOnUnknownDimension() {
        DataConstraint unknownDimension = new DataConstraint("regionId", DataConstraintOperator.IN,
                Collections.singletonList("north"));
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(
                new DataGrant(ORDER_RESOURCE, Collections.singletonList(READ_ACTION), false,
                        Collections.singletonList(unknownDimension))));
        DataAccessPlan plan = DataAccessPlan.evaluate(document, request());
        assertThatIllegalStateException().isThrownBy(() -> OrderDataAccessPlanConverter.toSpecification(plan));
    }

    @Test
    void shouldFailClosedWhenGrantHasNoConstraint() {
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Arrays.asList(
                new DataGrant(ORDER_RESOURCE, Collections.singletonList(READ_ACTION), false,
                        Collections.singletonList(constraint(TENANT_ID, "t1"))),
                new DataGrant(ORDER_RESOURCE, Collections.singletonList(READ_ACTION), false,
                        Collections.singletonList(constraint(DEPARTMENT_ID, "d1")))));
        DataAccessPlan plan = DataAccessPlan.evaluate(document, request());
        assertThat(OrderDataAccessPlanConverter.toSpecification(plan)).isNotNull();
    }

    private static DataConstraint constraint(String dimension, String value) {
        return new DataConstraint(dimension, DataConstraintOperator.IN, Collections.singletonList(value));
    }

    private static DataPermissionRequest request() {
        return new DataPermissionRequest(ORDER_RESOURCE, READ_ACTION);
    }
}
