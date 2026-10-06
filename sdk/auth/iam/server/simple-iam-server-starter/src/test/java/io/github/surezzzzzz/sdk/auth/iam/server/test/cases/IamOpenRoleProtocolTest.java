package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.CreateOpenRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.PutOpenRoleRuleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleDataAccessPlanHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 强标记、规范化摘要及不可拆分 DATA 范围的协议边界。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamOpenRoleProtocolTest {
    @Test
    void integerFieldsKeepFullPrecisionAndRejectCoercionWithoutChangingMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CreateOpenRoleRequest request = mapper.readValue("{\"applicationId\":9223372036854775807,\"rootDepartmentId\":1}", CreateOpenRoleRequest.class);
        assertEquals(Long.valueOf(Long.MAX_VALUE), request.getApplicationId());
        assertEquals(Long.valueOf(1L), request.getRootDepartmentId());
        PutOpenRoleRuleRequest rule = mapper.readValue("{\"manifestVersion\":9223372036854775807}", PutOpenRoleRuleRequest.class);
        assertEquals(Long.valueOf(Long.MAX_VALUE), rule.getManifestVersion());
        for (String invalid : Arrays.asList("1.9", "1.0", "1e2", "\"1\"", "true", "[]", "{}", "9223372036854775808")) {
            assertThrows(JsonProcessingException.class, () -> mapper.readValue("{\"applicationId\":" + invalid + "}", CreateOpenRoleRequest.class));
            assertThrows(JsonProcessingException.class, () -> mapper.readValue("{\"rootDepartmentId\":" + invalid + "}", CreateOpenRoleRequest.class));
            assertThrows(JsonProcessingException.class, () -> mapper.readValue("{\"manifestVersion\":" + invalid + "}", PutOpenRoleRuleRequest.class));
        }
        assertEquals(Long.valueOf(1L), mapper.readValue("1.9", Long.class), "局部严格解析不能改变宿主全局规则");
    }

    @Test
    void strictEtagRejectsMissingStaleWeakAndOverflow() {
        IamOpenRoleBindingEntity binding = new IamOpenRoleBindingEntity();
        binding.setOpenRoleId("20000000-0000-4000-8000-000000000001");
        binding.setRevision(2L);
        String current = IamOpenRoleProtocolHelper.etag(binding.getOpenRoleId(), 2L);
        assertDoesNotThrow(() -> IamOpenRoleProtocolHelper.requireIfMatch(current, binding));
        assertEquals(ErrorCode.OPEN_ROLE_PRECONDITION_REQUIRED, assertThrows(IamOpenRoleException.class,
                () -> IamOpenRoleProtocolHelper.requireIfMatch(null, binding)).getErrorCode());
        assertEquals(ErrorCode.OPEN_ROLE_PRECONDITION_FAILED, assertThrows(IamOpenRoleException.class,
                () -> IamOpenRoleProtocolHelper.requireIfMatch(IamOpenRoleProtocolHelper.etag(binding.getOpenRoleId(), 1L), binding)).getErrorCode());
        for (String invalid : Arrays.asList("*", "W/" + current, current + "," + current,
                "\"open-role:" + binding.getOpenRoleId() + ":9223372036854775808\"")) {
            assertEquals(ErrorCode.OPEN_ROLE_INVALID, assertThrows(IamOpenRoleException.class,
                    () -> IamOpenRoleProtocolHelper.requireIfMatch(invalid, binding)).getErrorCode());
        }
    }

    @Test
    void normalizedCreationHasStableDigestAndStrictIdentifiers() {
        CreateOpenRoleRequest first = new CreateOpenRoleRequest();
        first.setExternalId("10000000-0000-4000-8000-00000000000A");
        first.setApplicationId(210L);
        first.setRootDepartmentId(310L);
        first.setName(" 示例角色 ");
        first.setDescription(" ");
        CreateOpenRoleRequest normalized = IamOpenRoleProtocolHelper.normalize(first);
        assertEquals("10000000-0000-4000-8000-00000000000a", normalized.getExternalId());
        assertNull(normalized.getDescription());
        assertEquals(IamOpenRoleProtocolHelper.digest(normalized),
                IamOpenRoleProtocolHelper.digest(IamOpenRoleProtocolHelper.normalize(normalized)));
        assertThrows(IamOpenRoleException.class, () -> IamOpenRoleProtocolHelper.uuid("1-1-1-1-1"));
        assertThrows(IamOpenRoleException.class, () -> IamOpenRoleProtocolHelper.positive(0L));
        first.setName(String.join("", Collections.nCopies(129, "a")));
        assertThrows(IamOpenRoleException.class, () -> IamOpenRoleProtocolHelper.normalize(first));
    }

    @Test
    void completeGrantsCannotCombineRootsAndApplications() {
        DataGrant first = grant("210", "310");
        DataGrant second = grant("211", "311");
        DataAccessPlan plan = DataAccessPlan.evaluate(new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Arrays.asList(first, second)), new DataPermissionRequest("iam:open-role", "create"));
        Map<String, String> target = new HashMap<>();
        target.put("applicationId", "210");
        target.put("rootDepartmentId", "311");
        assertThrows(IamOpenRoleException.class, () -> IamOpenRoleDataAccessPlanHelper.require(plan, target));
        target.put("rootDepartmentId", "310");
        assertDoesNotThrow(() -> IamOpenRoleDataAccessPlanHelper.require(plan, target));
        DataGrant unknown = new DataGrant("iam:open-role", Collections.singletonList("create"), false,
                Collections.singletonList(new DataConstraint("unknown", DataConstraintOperator.IN, Collections.singletonList("210"))));
        DataAccessPlan invalid = DataAccessPlan.evaluate(new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(unknown)), new DataPermissionRequest("iam:open-role", "create"));
        assertThrows(IamOpenRoleException.class, () -> IamOpenRoleDataAccessPlanHelper.require(invalid, target));
        assertThrows(IamOpenRoleException.class, () -> IamOpenRoleDataAccessPlanHelper.require(DataAccessPlan.deny(), target));
    }

    private DataGrant grant(String app, String root) {
        return new DataGrant("iam:open-role", Collections.singletonList("create"), false, Arrays.asList(
                new DataConstraint("applicationId", DataConstraintOperator.IN, Collections.singletonList(app)),
                new DataConstraint("rootDepartmentId", DataConstraintOperator.IN, Collections.singletonList(root))));
    }
}
