package io.github.surezzzzzz.sdk.iam.resttemplate.client.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient;
import io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException;
import io.github.surezzzzzz.sdk.iam.client.model.*;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.IamDepartmentRestTemplateClient;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.IamOpenRoleRestTemplateClient;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.IamUserRestTemplateClient;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * 三客户端契约测试：MockRestServiceServer 桩底座 RestTemplate，30 方法断言 URL/方法/请求体/响应解析。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamClientRestTemplateContractTest {

    private static final String BASE = "https://iam.example.internal";
    private static final String API = BASE + "/iam/api";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private IamUserClient users;
    private IamDepartmentClient departments;
    private IamOpenRoleClient openRoles;
    private io.github.surezzzzzz.sdk.iam.resttemplate.client.IamApplicationRestTemplateClient applicationsClient;
    private io.github.surezzzzzz.sdk.iam.resttemplate.client.IamPortalApplicationRestTemplateClient portalApplicationsClient;

    private static String springPageJson() {
        return "{\"content\":[" + userJson() + "],\"totalElements\":1,\"totalPages\":1,\"number\":0,\"size\":20,"
                + "\"first\":true,\"last\":true,\"empty\":false,\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},"
                + "\"pageable\":{\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},\"offset\":0,\"pageNumber\":0,\"pageSize\":20,\"paged\":true,\"unpaged\":false}}";
    }

    private static String userJson() {
        return "{\"id\":3,\"subjectId\":\"sid-e2e-1\",\"username\":\"e2e-user\",\"displayName\":\"成员\",\"departmentId\":1,"
                + "\"departmentName\":\"root\",\"status\":1,\"email\":null,\"phone\":\"+8613800000001\","
                + "\"roles\":[\"iam_user\"]}";
    }

    private static String departmentJson() {
        return "{\"id\":1,\"code\":\"ROOT\",\"name\":\"root\",\"parentId\":null,\"fullPath\":\"/root\","
                + "\"sortOrder\":1,\"status\":1}";
    }

    private static String roleJson() {
        return "{\"openRoleId\":\"uuid-1\",\"externalId\":\"ext-1\",\"code\":\"role-1\",\"applicationId\":3,"
                + "\"rootDepartmentId\":1,\"name\":\"角色\",\"description\":\"描述\",\"revision\":4,"
                + "\"state\":\"ACTIVE\",\"rulePresent\":true,\"binding\":null}";
    }

    private static String ruleJson() {
        return "{\"openRoleId\":\"uuid-1\",\"applicationId\":3,\"revision\":6,\"pagePermissions\":[],"
                + "\"apiPermissions\":[\"kms.key:api\"],\"dataGrantTemplate\":{\"iam:user\":{\"read\":true}}}";
    }

    private static String directoryJson() {
        return "{\"rootDepartmentId\":1,\"complete\":true,\"directoryDigest\":\"sha\","
                + "\"departments\":[{\"departmentId\":1,\"code\":\"ROOT\",\"parentId\":null,\"name\":\"root\",\"status\":1}]}";
    }

    private static PortalPageFixture portalPageFixture() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("applicationCode", "ticket");
        item.put("applicationName", "Ticket");
        item.put("status", Integer.valueOf(1));
        item.put("portalEnabled", Boolean.TRUE);
        item.put("routePrefix", "/app/ticket");
        item.put("entry", "/app/ticket/");
        item.put("apiBase", "/api/ticket");
        item.put("targetOrigin", "http://ticket.internal");
        item.put("configVersion", Long.valueOf(3L));
        item.put("forwardAuthorization", "REQUEST");
        item.put("forwardCookieNames", new ArrayList<String>());

        Map<String, Object> page = new LinkedHashMap<String, Object>();
        page.put("catalogRevision", Long.valueOf(7L));
        page.put("totalElements", Long.valueOf(1L));
        page.put("page", Integer.valueOf(0));
        page.put("size", Integer.valueOf(20));
        page.put("items", Collections.singletonList(item));
        return new PortalPageFixture(page, item);
    }

    private static String portalJson(PortalPageFixture fixture) throws Exception {
        return new ObjectMapper().writeValueAsString(fixture.page);
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        SimpleIamClientRestTemplateProperties properties = new SimpleIamClientRestTemplateProperties();
        properties.setBaseUrl(BASE);
        users = new IamUserRestTemplateClient(restTemplate, properties);
        departments = new IamDepartmentRestTemplateClient(restTemplate, properties);
        openRoles = new IamOpenRoleRestTemplateClient(restTemplate, properties);
        applicationsClient = new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamApplicationRestTemplateClient(restTemplate, properties);
        portalApplicationsClient = new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamPortalApplicationRestTemplateClient(restTemplate, properties);
    }

    @Test
    void shouldCallUserEndpointsWithContractFields() {
        server.expect(requestTo(API + "/users?status=1&departmentId=2&keyword=e2e&page=0&size=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(springPageJson(), MediaType.APPLICATION_JSON));
        IamSpringPage<IamUser> page = users.listUsers(1, 2L, "e2e", 0, 20);
        assertEquals(1, page.getContent().size(), "Spring Page 条目解析");
        assertEquals(1L, page.getTotalElements(), "Spring Page totalElements");
        assertEquals(0, page.getNumber(), "Spring Page 页码 0 起");
        assertEquals("e2e-user", page.getContent().get(0).getUsername(), "用户字段解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/users/sub-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(userJson(), MediaType.APPLICATION_JSON));
        io.github.surezzzzzz.sdk.iam.client.model.IamUser detail = users.getUser("sub-1");
        assertEquals("e2e-user", detail.getUsername(), "用户详情");
        assertEquals("sid-e2e-1", detail.getSubjectId(), "用户详情 subjectId 回传解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/users/sub-1/page-admitted-applications"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[\"iam\",\"kms\"]", MediaType.APPLICATION_JSON));
        assertEquals(Arrays.asList("iam", "kms"), users.listPageAdmittedApplications("sub-1"),
                "页面准入应用编码裸列表");
        server.verify();
        server.reset();

        // 1.3.7 details：对象数组直读（code/displayName/description）
        server.expect(requestTo(API + "/users/sub-1/page-admitted-application-details"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"code\":\"iam\",\"displayName\":\"IAM 管理台\",\"description\":null}]",
                        MediaType.APPLICATION_JSON));
        java.util.List<io.github.surezzzzzz.sdk.iam.client.model.PageAdmittedApplication> details =
                users.listPageAdmittedApplicationDetails("sub-1");
        assertEquals("IAM 管理台", details.get(0).getDisplayName(), "详情 displayName 解析");
        assertEquals("iam", details.get(0).getCode(), "详情 code 解析");
        server.verify();
        server.reset();

        // 1.3.7 applications：目录分页（Spring Page wire + 三字段）
        server.expect(requestTo(API + "/applications?page=0&size=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[{\"applicationCode\":\"iam\",\"applicationName\":\"IAM 管理台\",\"status\":1}],\"totalElements\":1,\"totalPages\":1,\"number\":0,\"size\":20}",
                        MediaType.APPLICATION_JSON));
        io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage<io.github.surezzzzzz.sdk.iam.client.model.IamApplication> applications =
                applicationsClient.listApplications(0, 20);
        assertEquals(1L, applications.getTotalElements(), "目录分页 totalElements");
        assertEquals("iam", applications.getContent().get(0).getApplicationCode(), "目录三字段解析");
        assertEquals(Integer.valueOf(1), applications.getContent().get(0).getStatus(), "目录 status 解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/portal/applications?page=0&size=20&catalogRevision=7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"catalogRevision\":7,\"totalElements\":1,\"page\":0,\"size\":20,"
                                + "\"items\":[{\"applicationCode\":\"ticket\",\"applicationName\":\"Ticket\","
                                + "\"status\":1,\"portalEnabled\":true,\"routePrefix\":\"/app/ticket\","
                                + "\"entry\":\"/app/ticket/\",\"apiBase\":\"/api/ticket\",\"targetOrigin\":\"http://ticket.internal\",\"configVersion\":3,"
                                + "\"forwardAuthorization\":\"REQUEST\",\"forwardCookieNames\":[]}]}",
                        MediaType.APPLICATION_JSON));
        io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationPage portalPage =
                portalApplicationsClient.listApplications(0, 20, 7L);
        assertEquals(7L, portalPage.getCatalogRevision(), "Portal 目录 revision");
        assertEquals("/app/ticket", portalPage.getItems().get(0).getRoutePrefix(), "Portal 路由前缀");
        assertEquals("REQUEST", portalPage.getItems().get(0).getForwardAuthorization(), "Authorization 原样转发策略");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/users/sub-1/roles"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[\"iam_user\",\"kms-admin\"]", MediaType.APPLICATION_JSON));
        assertEquals(Arrays.asList("iam_user", "kms-admin"), users.getUserRoles("sub-1"),
                "角色编码裸列表契约（字符串数组非对象数组）");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"username\":\"new-user\",\"password\":\"P@ssw0rd\",\"displayName\":\"成员\"}", true))
                .andRespond(withCreatedEntity(URI.create(API + "/users/sub-9")).body(userJson()).contentType(MediaType.APPLICATION_JSON));
        users.createUser("new-user", "P@ssw0rd", "成员", null, null, null);
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/users/sub-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"displayName\":\"新名\",\"email\":\"\",\"phone\":\"\"}", true))
                .andRespond(withSuccess(userJson(), MediaType.APPLICATION_JSON));
        users.updateUser("sub-1", "新名", "", "");
        server.verify();
        server.reset();

        expectVoid(HttpMethod.DELETE, API + "/users/sub-1", null);
        users.deleteUser("sub-1");
        server.verify();
        server.reset();

        expectVoid(HttpMethod.PUT, API + "/users/sub-1/enable", null);
        users.enableUser("sub-1");
        server.verify();
        server.reset();

        expectVoid(HttpMethod.PUT, API + "/users/sub-1/disable", null);
        users.disableUser("sub-1");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/users/sub-1/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"newPassword\":\"NewP@ss1\"}", true))
                .andRespond(withNoContent());
        users.resetPassword("sub-1", "NewP@ss1");
        server.verify();
        server.reset();

        expectVoid(HttpMethod.POST, API + "/users/sub-1/roles/5", null);
        users.assignRole("sub-1", 5L);
        server.verify();
        server.reset();

        expectVoid(HttpMethod.DELETE, API + "/users/sub-1/roles/5", null);
        users.revokeRole("sub-1", 5L);
        server.verify();
    }

    @Test
    void shouldRejectMissingPortalRequiredFields() throws Exception {
        for (String field : Arrays.asList("catalogRevision", "totalElements", "page", "size", "items")) {
            PortalPageFixture fixture = portalPageFixture();
            fixture.page.remove(field);
            assertPortalProtocolError(fixture, "缺少分页字段 " + field);
        }
        for (String field : Arrays.asList("applicationCode", "applicationName", "status", "portalEnabled",
                "routePrefix", "entry", "apiBase", "targetOrigin", "configVersion", "forwardAuthorization", "forwardCookieNames")) {
            PortalPageFixture fixture = portalPageFixture();
            fixture.item.remove(field);
            assertPortalProtocolError(fixture, "缺少路由字段 " + field);
        }
    }

    @Test
    void shouldRejectMalformedPortalRequiredFields() throws Exception {
        PortalPageFixture fixture = portalPageFixture();
        fixture.page.put("catalogRevision", "7");
        assertPortalProtocolError(fixture, "catalogRevision 类型错误");

        fixture = portalPageFixture();
        fixture.page.put("totalElements", Long.valueOf(-1L));
        assertPortalProtocolError(fixture, "totalElements 越界");

        fixture = portalPageFixture();
        fixture.page.put("items", "[]");
        assertPortalProtocolError(fixture, "items 类型错误");

        fixture = portalPageFixture();
        fixture.item.put("status", Integer.valueOf(2));
        assertPortalProtocolError(fixture, "status 不符合机器目录状态契约");

        fixture = portalPageFixture();
        fixture.item.put("portalEnabled", "true");
        assertPortalProtocolError(fixture, "portalEnabled 类型错误");

        fixture = portalPageFixture();
        fixture.item.put("forwardCookieNames", "[]");
        assertPortalProtocolError(fixture, "forwardCookieNames 类型错误");

        fixture = portalPageFixture();
        fixture.item.put("forwardCookieNames", Collections.singletonList("session"));
        assertPortalProtocolError(fixture, "forwardCookieNames 非空");

        fixture = portalPageFixture();
        fixture.item.put("apiBase", Integer.valueOf(1));
        assertPortalProtocolError(fixture, "apiBase 非空时类型必须为文本");

        fixture = portalPageFixture();
        fixture.item.put("entry", "http://ticket.internal/app/ticket/");
        assertPortalProtocolError(fixture, "entry 只能是公共绝对路径");

        fixture = portalPageFixture();
        fixture.item.put("entry", "/app/other/index.html");
        assertPortalProtocolError(fixture, "entry 必须位于 routePrefix 下");

        for (String invalidOrigin : Arrays.asList(
                "http://ticket.internal/base", "http://ticket.internal?mode=1", "http://user@ticket.internal",
                "https://ticket.internal")) {
            fixture = portalPageFixture();
            fixture.item.put("targetOrigin", invalidOrigin);
            assertPortalProtocolError(fixture, "targetOrigin 必须是无路径、无 query、无 userinfo 的 origin");
        }
    }

    @Test
    void shouldRejectMissingOrNullPortalApiBaseOrTargetOrigin() throws Exception {
        for (boolean includeApiBase : Arrays.asList(Boolean.TRUE, Boolean.FALSE)) {
            PortalPageFixture fixture = portalPageFixture();
            if (includeApiBase) {
                fixture.item.put("apiBase", null);
            } else {
                fixture.item.remove("apiBase");
            }
            log.info("验证 Portal apiBase 可空契约: includeApiBase={}", includeApiBase);
            server.expect(requestTo(API + "/portal/applications?page=0&size=20&catalogRevision=7"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(portalJson(fixture), MediaType.APPLICATION_JSON));
            assertThrows(IamClientProtocolException.class,
                    () -> portalApplicationsClient.listApplications(0, 20, 7L));
            server.verify();
            server.reset();
        }
        PortalPageFixture fixture = portalPageFixture();
        fixture.item.remove("targetOrigin");
        assertPortalProtocolError(fixture, "targetOrigin 缺失");
        for (String forwardingPolicy : Arrays.asList("NONE", "BEARER", "request")) {
            fixture = portalPageFixture();
            fixture.item.put("forwardAuthorization", forwardingPolicy);
            assertPortalProtocolError(fixture, "forwardAuthorization 不符合固定策略");
        }
    }

    private void assertPortalProtocolError(PortalPageFixture fixture, String scenario) throws Exception {
        log.info("验证 Portal 客户端拒绝不符合契约的响应: {}", scenario);
        server.expect(requestTo(API + "/portal/applications?page=0&size=20&catalogRevision=7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(portalJson(fixture), MediaType.APPLICATION_JSON));
        assertThrows(IamClientProtocolException.class,
                () -> portalApplicationsClient.listApplications(0, 20, 7L), scenario);
        server.verify();
        server.reset();
    }

    @Test
    void shouldCallDepartmentEndpointsWithContractFields() {
        server.expect(requestTo(API + "/departments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + departmentJson() + "]", MediaType.APPLICATION_JSON));
        assertEquals("root", departments.listDepartments().get(0).getName(), "部门列表（数组形态）");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/departments/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(departmentJson(), MediaType.APPLICATION_JSON));
        assertEquals("ROOT", departments.getDepartment(1L).getCode(), "部门详情");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/departments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"code\":\"dev\",\"name\":\"研发\",\"parentId\":1,\"sortOrder\":2,\"status\":1,\"memberSubjectIds\":[\"sub-1\"]}", true))
                .andRespond(withCreatedEntity(URI.create(API + "/departments/9")).body(departmentJson()).contentType(MediaType.APPLICATION_JSON));
        departments.createDepartment("dev", "研发", 1L, 2, 1, Collections.singletonList("sub-1"));
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/departments/1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"name\":\"新名\"}", true))
                .andRespond(withSuccess(departmentJson(), MediaType.APPLICATION_JSON));
        departments.updateDepartment(1L, "新名", null, null, null);
        server.verify();
        server.reset();

        expectVoid(HttpMethod.DELETE, API + "/departments/1", null);
        departments.deleteDepartment(1L);
        server.verify();
    }

    @Test
    void shouldCallOpenRoleEndpointsWithIfMatchAndOwnPagination() {
        server.expect(requestTo(API + "/roles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"externalId\":\"ext-1\",\"applicationId\":3,\"rootDepartmentId\":1,\"name\":\"角色\",\"description\":\"描述\"}", true))
                .andRespond(withCreatedEntity(URI.create(API + "/roles/uuid-1")).body(roleJson()).contentType(MediaType.APPLICATION_JSON));
        IamOpenRole created = openRoles.createOpenRole("ext-1", 3L, 1L, "角色", "描述");
        assertEquals(4L, created.getRevision(), "revision 解析（If-Match 值来源）");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/roles?page=1&size=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[" + roleJson() + "],\"total\":1,\"page\":1,\"size\":20}",
                        MediaType.APPLICATION_JSON));
        IamOpenRolePage page = openRoles.listOpenRoles(1, 20);
        assertEquals(1, page.getItems().size(), "自持分页条目");
        assertEquals(1, page.getPage(), "自持分页页码 1 起（与 Spring Page 不同）");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/roles/uuid-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(roleJson(), MediaType.APPLICATION_JSON));
        openRoles.getOpenRole("uuid-1");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/roles/uuid-1/authorization-rules/3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ruleJson(), MediaType.APPLICATION_JSON));
        assertEquals(6L, openRoles.getOpenRoleRule("uuid-1", 3L).getRevision(), "规则 revision 解析");
        server.verify();
        server.reset();

        Map<String, Object> dataGrant = new LinkedHashMap<String, Object>();
        dataGrant.put("iam:user", Collections.singletonMap("read", Boolean.TRUE));
        server.expect(requestTo(API + "/roles/uuid-1/authorization-rules/3"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("If-Match", "open-role:uuid-1:4"))
                .andExpect(content().json("{\"manifestVersion\":2,\"manifestDigest\":\"sha\",\"pagePermissions\":[],\"apiPermissions\":[\"kms.key:api\"],\"dataGrantTemplate\":{\"iam:user\":{\"read\":true}}}", true))
                .andRespond(withSuccess(ruleJson(), MediaType.APPLICATION_JSON));
        openRoles.putOpenRoleRule("uuid-1", 3L, "open-role:uuid-1:4", 2L, "sha",
                Collections.<String>emptyList(), Collections.singletonList("kms.key:api"), dataGrant);
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/roles/uuid-1/authorization-rules/3"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("If-Match", "open-role:uuid-1:6"))
                .andRespond(withNoContent());
        openRoles.deleteOpenRoleRule("uuid-1", 3L, "open-role:uuid-1:6");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/roles/uuid-1/departments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[{\"departmentId\":1,\"inCurrentRoot\":true,\"createdAt\":\"2026-10-07T00:00:00Z\"}],\"total\":1,\"page\":1,\"size\":20,\"revision\":4}", MediaType.APPLICATION_JSON));
        IamOpenRoleDepartmentPage departmentsPage = openRoles.listOpenRoleDepartments("uuid-1");
        assertEquals(4L, departmentsPage.getRevision(), "挂载分页附带整体 revision");
        assertTrue(departmentsPage.getItems().get(0).isInCurrentRoot(), "挂载条目解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/departments/1/roles/uuid-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("If-Match", "open-role:uuid-1:4"))
                .andRespond(withNoContent());
        openRoles.mountOpenRoleToDepartment(1L, "uuid-1", "open-role:uuid-1:4");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/departments/1/roles/uuid-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("If-Match", "open-role:uuid-1:5"))
                .andRespond(withNoContent());
        openRoles.unmountOpenRoleFromDepartment(1L, "uuid-1", "open-role:uuid-1:5");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/organization-directories/1/departments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(directoryJson(), MediaType.APPLICATION_JSON));
        IamOrganizationDirectory directory = openRoles.listOrganizationDirectoryDepartments(1L);
        assertTrue(directory.isComplete(), "目录完整性解析");
        assertEquals("ROOT", directory.getDepartments().get(0).getCode(), "目录部门解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/organization-directories/1/members/sub-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"subjectId\":\"sub-1\",\"status\":1,\"departmentId\":1,\"inCurrentRoot\":true}", MediaType.APPLICATION_JSON));
        IamOrganizationMember member = openRoles.getOrganizationDirectoryMember(1L, "sub-1");
        assertTrue(member.isInCurrentRoot(), "成员定位解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/target-applications/3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"applicationId\":3,\"applicationCode\":\"kms\",\"name\":\"KMS\",\"status\":1,\"builtIn\":false}", MediaType.APPLICATION_JSON));
        IamTargetApplication application = openRoles.getTargetApplication(3L);
        assertEquals("kms", application.getApplicationCode(), "目标应用解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API + "/target-applications/3/permission-manifest"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"manifestVersion\":2,\"manifestDigest\":\"sha\",\"pagePermissions\":[\"kms.key:page\"],\"apiPermissions\":[\"kms.key:api\"]}", MediaType.APPLICATION_JSON));
        IamPermissionManifest manifest = openRoles.getTargetApplicationManifest(3L);
        assertEquals("kms.key:page", manifest.getPagePermissions().get(0), "清单解析");
        server.verify();
    }

    @Test
    void shouldPropagateHttpErrorsWithoutWrapping() {
        server.expect(requestTo(API + "/users/sub-1"))
                .andRespond(withBadRequest().body("{\"message\":\"bad\"}"));
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class,
                () -> users.getUser("sub-1"), "4xx 透传");
        assertEquals(400, exception.getStatusCode().value(), "状态码保留");
        server.verify();
    }

    @Test
    void shouldCallSixClanEndpointsWithContractFields() {
        io.github.surezzzzzz.sdk.iam.resttemplate.client.IamMessageRestTemplateClient messagesClient =
                new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamMessageRestTemplateClient(restTemplate, properties());
        io.github.surezzzzzz.sdk.iam.resttemplate.client.IamSessionRestTemplateClient sessionsClient =
                new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamSessionRestTemplateClient(restTemplate, properties());
        io.github.surezzzzzz.sdk.iam.resttemplate.client.IamPermissionRestTemplateClient permissionsClient =
                new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamPermissionRestTemplateClient(restTemplate, properties());
        io.github.surezzzzzz.sdk.iam.resttemplate.client.IamRoleCatalogRestTemplateClient roleCatalogClient =
                new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamRoleCatalogRestTemplateClient(restTemplate, properties());
        io.github.surezzzzzz.sdk.iam.resttemplate.client.IamStatisticsRestTemplateClient statisticsClient =
                new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamStatisticsRestTemplateClient(restTemplate, properties());
        io.github.surezzzzzz.sdk.iam.resttemplate.client.IamUserGroupRestTemplateClient userGroupsClient =
                new io.github.surezzzzzz.sdk.iam.resttemplate.client.IamUserGroupRestTemplateClient(restTemplate, properties());

        // applications（Spring Page wire）
        server.expect(requestTo(API + "/applications?page=0&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\":[{\"applicationCode\":\"iam\",\"applicationName\":\"IAM\","
                        + "\"status\":1,\"createdAt\":\"2026-01-01T00:00:00Z\"}],\"totalElements\":1,\"totalPages\":1,"
                        + "\"number\":0,\"size\":50}", MediaType.APPLICATION_JSON));
        assertEquals(1L, applicationsClient.listApplications(0, 50).getTotalElements());
        server.reset();

        // messages 三方法
        server.expect(requestTo(API + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"recipientSubjectIds\":[\"sid-e2e-1\"],\"title\":\"t\",\"content\":\"c\"}", true))
                .andRespond(withSuccess("{\"sendBatchId\":\"batch-1\",\"recipientCount\":1}", MediaType.APPLICATION_JSON));
        assertEquals("batch-1", messagesClient.sendMessage(
                io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendRequest.builder()
                        .recipientSubjectIds(java.util.Collections.singletonList("sid-e2e-1"))
                        .title("t").content("c").build()).getSendBatchId());
        server.reset();
        server.expect(requestTo(API + "/messages?subjectId=sid-e2e-1&page=1&size=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"title\":\"t\",\"content\":\"c\",\"sendBatchId\":\"batch-1\","
                        + "\"senderUsername\":\"aksk:c1\"}]", MediaType.APPLICATION_JSON));
        assertEquals("t", messagesClient.listBySubject("sid-e2e-1", 1, 5).get(0).getTitle());
        server.reset();
        server.expect(requestTo(API + "/messages/batch-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"sendBatchId\":\"batch-1\",\"title\":\"t\",\"content\":\"c\",\"recipientCount\":1,"
                        + "\"targetUserCount\":1,\"targetDepartmentCount\":0}", MediaType.APPLICATION_JSON));
        assertEquals(1, messagesClient.getBatch("batch-1").getTargetUserCount());
        server.reset();

        // sessions 两方法
        server.expect(requestTo(API + "/sessions?subjectId=sid-e2e-1&page=1&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"sessionId\":\"sess-1\",\"username\":\"e2e-user\",\"subjectId\":\"sid-e2e-1\","
                                + "\"clientId\":\"c1\",\"remoteIp\":\"127.0.0.1\",\"userAgent\":\"ua\"}]",
                        MediaType.APPLICATION_JSON));
        assertEquals("sess-1", sessionsClient.listBySubject("sid-e2e-1", 1, 10).get(0).getSessionId());
        server.reset();
        expectVoid(HttpMethod.DELETE, API + "/sessions/sess-1", null);
        sessionsClient.revokeSession("sess-1");
        server.reset();

        // permissions
        server.expect(requestTo(API + "/permissions?keyword=&page=1&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"code\":\"iam:user:api\",\"name\":\"用户API\",\"type\":\"API\","
                        + "\"description\":\"d\",\"builtIn\":true}]", MediaType.APPLICATION_JSON));
        assertEquals("iam:user:api", permissionsClient.listPermissions("", 1, 50).get(0).getCode());
        server.reset();

        // role-catalog
        server.expect(requestTo(API + "/role-catalog?keyword=&page=1&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":2,\"code\":\"iam_user\",\"name\":\"普通用户\","
                        + "\"description\":\"d\",\"builtIn\":true}]", MediaType.APPLICATION_JSON));
        assertEquals(Long.valueOf(2L), roleCatalogClient.listRoles("", 1, 50).get(0).getId());
        server.reset();

        // statistics（嵌套 wire）
        server.expect(requestTo(API + "/statistics"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"counts\":{\"user\":10,\"role\":14,\"department\":6,\"userGroup\":0,"
                                + "\"permission\":33,\"trustedApplication\":7},\"stats\":{\"activeSessions\":0,"
                                + "\"todayLoggedInUsers\":3,\"lockedUsers\":0,\"disabledUsers\":0,\"usersWithoutDepartment\":0}}",
                        MediaType.APPLICATION_JSON));
        assertEquals(10L, statisticsClient.getStatistics().getUser());
        server.reset();

        // user-groups 八方法
        server.expect(requestTo(API + "/user-groups?status=1&keyword=e2e&page=1&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":9,\"code\":\"g1\",\"name\":\"组一\",\"description\":\"d\","
                        + "\"displayName\":\"组一\",\"status\":1}]", MediaType.APPLICATION_JSON));
        assertEquals("g1", userGroupsClient.listGroups(1, "e2e", 1, 10).get(0).getCode());
        server.reset();
        server.expect(requestTo(API + "/user-groups"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"code\":\"g1\",\"name\":\"组一\",\"description\":\"d\"}", true))
                .andRespond(withSuccess("{\"id\":9,\"code\":\"g1\",\"name\":\"组一\",\"status\":1}",
                        MediaType.APPLICATION_JSON));
        assertEquals(Long.valueOf(9L), userGroupsClient.createGroup("g1", "组一", "d").getId());
        server.reset();
        server.expect(requestTo(API + "/user-groups/9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":9,\"code\":\"g1\",\"name\":\"组一\",\"status\":1}",
                        MediaType.APPLICATION_JSON));
        assertEquals("g1", userGroupsClient.getGroup(9L).getCode());
        server.reset();
        server.expect(requestTo(API + "/user-groups/9"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"name\":\"组二\"}", true))
                .andRespond(withSuccess("{\"id\":9,\"code\":\"g1\",\"name\":\"组二\",\"status\":1}",
                        MediaType.APPLICATION_JSON));
        assertEquals("组二", userGroupsClient.updateGroup(9L, "组二", null).getName());
        server.reset();
        expectVoid(HttpMethod.POST, API + "/user-groups/9/users/sid-e2e-1", null);
        userGroupsClient.assignUser(9L, "sid-e2e-1");
        server.reset();
        server.expect(requestTo(API + "/user-groups/9/users?page=1&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"subjectId\":\"sid-e2e-1\",\"username\":\"e2e-user\","
                        + "\"displayName\":\"成员\"}]", MediaType.APPLICATION_JSON));
        assertEquals("sid-e2e-1", userGroupsClient.listGroupUsers(9L, 1, 10).get(0).getSubjectId());
        server.reset();
        expectVoid(HttpMethod.DELETE, API + "/user-groups/9/users/sid-e2e-1", null);
        userGroupsClient.revokeUser(9L, "sid-e2e-1");
        server.reset();
        expectVoid(HttpMethod.DELETE, API + "/user-groups/9", null);
        userGroupsClient.deleteGroup(9L);
        server.verify();
    }

    private SimpleIamClientRestTemplateProperties properties() {
        SimpleIamClientRestTemplateProperties properties = new SimpleIamClientRestTemplateProperties();
        properties.setBaseUrl(BASE);
        return properties;
    }

    private void expectVoid(HttpMethod httpMethod, String url, String ifMatch) {
        org.springframework.test.web.client.ResponseActions actions = server.expect(requestTo(url))
                .andExpect(method(httpMethod));
        if (ifMatch != null) {
            actions = actions.andExpect(header("If-Match", ifMatch));
        }
        actions.andRespond(withNoContent());
    }

    private static final class PortalPageFixture {
        private final Map<String, Object> page;
        private final Map<String, Object> item;

        private PortalPageFixture(Map<String, Object> page, Map<String, Object> item) {
            this.page = page;
            this.item = item;
        }
    }
}
