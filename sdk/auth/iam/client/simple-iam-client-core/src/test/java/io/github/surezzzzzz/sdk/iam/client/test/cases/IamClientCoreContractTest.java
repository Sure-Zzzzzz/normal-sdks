package io.github.surezzzzzz.sdk.iam.client.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient;
import io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamOpenRole;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * client-core 契约静态测试：常量值逐字对位 server rest 契约；三接口方法数=30。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamClientCoreContractTest {

    private static int countMethods(Class<?> contract) {
        int count = 0;
        for (Method method : contract.getMethods()) {
            if (method.getDeclaringClass() == contract) {
                count++;
            }
        }
        return count;
    }

    @Test
    void shouldHoldContractConstantsVerbatim() {
        assertEquals("/iam/api", SimpleIamClientConstant.API_BASE_PATH, "openapi 基路径");
        assertEquals("users", SimpleIamClientConstant.RESOURCE_USERS, "用户族资源段");
        assertEquals("departments", SimpleIamClientConstant.RESOURCE_DEPARTMENTS, "部门族资源段");
        assertEquals("roles", SimpleIamClientConstant.RESOURCE_ROLES, "受委托角色族资源段");
        assertEquals("authorization-rules", SimpleIamClientConstant.RESOURCE_AUTHORIZATION_RULES, "规则资源段");
        assertEquals("organization-directories", SimpleIamClientConstant.RESOURCE_ORGANIZATION_DIRECTORIES);
        assertEquals("target-applications", SimpleIamClientConstant.RESOURCE_TARGET_APPLICATIONS);
        assertEquals("If-Match", SimpleIamClientConstant.HEADER_IF_MATCH, "乐观并发条件头");
        assertEquals("ETag", SimpleIamClientConstant.HEADER_ETAG, "乐观并发版本头");
        assertEquals("content", SimpleIamClientConstant.FIELD_CONTENT, "Spring Page 条目字段");
        assertEquals("totalElements", SimpleIamClientConstant.FIELD_TOTAL_ELEMENTS, "Spring Page 总数字段");
        assertEquals("items", SimpleIamClientConstant.FIELD_ITEMS, "自持分页条目字段");
        assertEquals("total", SimpleIamClientConstant.FIELD_TOTAL, "自持分页总数字段");
        log.info("契约常量逐字对位通过");
    }

    @Test
    void shouldExposeExactlyThirtyMethodsAcrossThreeClients() {
        assertEquals(12, countMethods(IamUserClient.class), "用户族 12 方法");
        assertEquals(5, countMethods(IamDepartmentClient.class), "部门族 5 方法");
        assertEquals(13, countMethods(IamOpenRoleClient.class), "受委托角色族 13 方法");
        assertEquals(30, countMethods(IamUserClient.class) + countMethods(IamDepartmentClient.class)
                + countMethods(IamOpenRoleClient.class), "全 openapi 共 30 方法");
        log.info("三接口 30 方法对位通过");
    }

    @Test
    void shouldBuildProjectionModelsWithContractFields() {
        IamUser user = IamUser.builder()
                .id(3L).username("e2e-user").displayName("成员").departmentId(1L).departmentName("root")
                .status(1).email(null).phone("+8613800000001")
                .roles(java.util.Collections.singletonList("iam_user")).build();
        assertEquals("+8613800000001", user.getPhone(), "手机号投影字段");
        assertEquals("iam_user", user.getRoles().get(0), "角色编码列表投影");
        assertNull(user.getEmail(), "null 字段保持 null");

        IamSpringPage<IamUser> page = IamSpringPage.<IamUser>builder()
                .content(java.util.Collections.singletonList(user)).totalElements(1)
                .totalPages(1).number(0).size(20).build();
        assertEquals(1, page.getContent().size(), "分页条目");
        assertEquals(0, page.getNumber(), "Spring Page 页码 0 起（与自持分页 1 起不同，非直观契约）");

        IamOpenRole role = IamOpenRole.builder()
                .openRoleId("uuid-1").revision(4L).state("ACTIVE").rulePresent(false).build();
        assertEquals(4L, role.getRevision(), "revision 字段=If-Match 值来源");
        log.info("投影模型 builder 契约字段通过");
    }
}
