package io.github.surezzzzzz.sdk.iam.client.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamPortalApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamOpenRole;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * client-core 契约静态测试：常量值逐字对位 server rest 契约；Portal 可信应用路由投影与旧应用目录分离。
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
        assertEquals("portal", SimpleIamClientConstant.RESOURCE_PORTAL, "Portal 可信应用路由投影资源段");
        assertEquals("applications", SimpleIamClientConstant.RESOURCE_APPLICATIONS, "Portal 应用资源段");
        assertEquals("catalogRevision", SimpleIamClientConstant.QUERY_CATALOG_REVISION, "目录 revision 查询参数");
        assertEquals("catalogRevision", SimpleIamClientConstant.FIELD_CATALOG_REVISION, "目录 revision 字段");
        assertEquals("portalEnabled", SimpleIamClientConstant.FIELD_PORTAL_ENABLED, "Portal 启用字段");
        assertEquals("routePrefix", SimpleIamClientConstant.FIELD_ROUTE_PREFIX, "路由前缀字段");
        assertEquals("entry", SimpleIamClientConstant.FIELD_ENTRY, "页面入口字段");
        assertEquals("apiBase", SimpleIamClientConstant.FIELD_API_BASE, "API 入口字段");
        assertEquals("targetOrigin", SimpleIamClientConstant.FIELD_TARGET_ORIGIN, "Portal 单一机器目标字段");
        assertEquals("configVersion", SimpleIamClientConstant.FIELD_CONFIG_VERSION, "应用配置版本字段");
        assertEquals("forwardAuthorization", SimpleIamClientConstant.FIELD_FORWARD_AUTHORIZATION,
                "Authorization 转发策略字段");
        assertEquals("forwardCookieNames", SimpleIamClientConstant.FIELD_FORWARD_COOKIE_NAMES,
                "Cookie 名称白名单字段");
        assertEquals("REQUEST", SimpleIamClientConstant.FORWARD_AUTHORIZATION_REQUEST,
                "Authorization 原请求转发策略");
        assertEquals("If-Match", SimpleIamClientConstant.HEADER_IF_MATCH, "乐观并发条件头");
        assertEquals("ETag", SimpleIamClientConstant.HEADER_ETAG, "乐观并发版本头");
        assertEquals("content", SimpleIamClientConstant.FIELD_CONTENT, "Spring Page 条目字段");
        assertEquals("totalElements", SimpleIamClientConstant.FIELD_TOTAL_ELEMENTS, "Spring Page 总数字段");
        assertEquals("items", SimpleIamClientConstant.FIELD_ITEMS, "自持分页条目字段");
        assertEquals("total", SimpleIamClientConstant.FIELD_TOTAL, "自持分页总数字段");
        log.info("契约常量逐字对位通过");
    }

    @Test
    void shouldExposeExactlyFortyNineMethodsAcrossAllClients() {
        assertEquals(13, countMethods(IamUserClient.class), "用户族 13 方法");
        assertEquals(5, countMethods(io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient.class), "部门族 5 方法");
        assertEquals(13, countMethods(io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient.class), "受委托角色族 13 方法");
        assertEquals(1, countMethods(io.github.surezzzzzz.sdk.iam.client.IamApplicationClient.class), "应用目录族 1 方法");
        assertEquals(1, countMethods(IamPortalApplicationClient.class), "Portal 可信应用路由投影族 1 方法");
        assertEquals(3, countMethods(io.github.surezzzzzz.sdk.iam.client.IamMessageClient.class), "站内信族 3 方法");
        assertEquals(2, countMethods(io.github.surezzzzzz.sdk.iam.client.IamSessionClient.class), "会话族 2 方法");
        assertEquals(1, countMethods(io.github.surezzzzzz.sdk.iam.client.IamPermissionClient.class), "权限清单族 1 方法");
        assertEquals(8, countMethods(io.github.surezzzzzz.sdk.iam.client.IamUserGroupClient.class), "协作组族 8 方法");
        assertEquals(1, countMethods(io.github.surezzzzzz.sdk.iam.client.IamRoleCatalogClient.class), "角色目录族 1 方法");
        assertEquals(1, countMethods(io.github.surezzzzzz.sdk.iam.client.IamStatisticsClient.class), "统计族 1 方法");
        assertEquals(49, countMethods(IamUserClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamApplicationClient.class)
                + countMethods(IamPortalApplicationClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamMessageClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamSessionClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamPermissionClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamUserGroupClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamRoleCatalogClient.class)
                + countMethods(io.github.surezzzzzz.sdk.iam.client.IamStatisticsClient.class), "全 openapi 共 49 方法");
        log.info("全 openapi 49 方法对位通过（server 1.3.8）");
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

        IamPortalApplicationRoute route = IamPortalApplicationRoute.builder()
                .applicationCode("sample")
                .applicationName("Sample")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/sample")
                .entry("/app/sample/")
                .apiBase("/api/sample")
                .targetOrigin("http://sample.internal")
                .configVersion(3L)
                .forwardAuthorization(SimpleIamClientConstant.FORWARD_AUTHORIZATION_REQUEST)
                .forwardCookieNames(java.util.Collections.<String>emptyList())
                .build();
        IamPortalApplicationPage portalPage = IamPortalApplicationPage.builder()
                .catalogRevision(7L)
                .totalElements(1L)
                .page(0)
                .size(20)
                .items(java.util.Collections.singletonList(route))
                .build();
        log.info("Portal 路由投影模型字段通过：revision={}, routePrefix={}",
                portalPage.getCatalogRevision(), portalPage.getItems().get(0).getRoutePrefix());
        assertEquals(Long.valueOf(7L), portalPage.getCatalogRevision(), "Portal 目录版本");
        assertEquals("/app/sample", portalPage.getItems().get(0).getRoutePrefix(), "Portal 路由前缀");
        assertEquals(SimpleIamClientConstant.FORWARD_AUTHORIZATION_REQUEST,
                portalPage.getItems().get(0).getForwardAuthorization(), "Authorization 转发策略");
        assertEquals(0, portalPage.getItems().get(0).getForwardCookieNames().size(), "Cookie 转发白名单为空");
        log.info("投影模型 builder 契约字段通过");
    }
}
