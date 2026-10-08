package io.github.surezzzzzz.sdk.iam.resttemplate.client.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient;
import io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * 三客户端契约测试：MockRestServiceServer 桩底座 RestTemplate，29 方法断言 URL/方法/请求体/响应解析。
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

    private static String springPageJson() {
        return "{\"content\":[" + userJson() + "],\"totalElements\":1,\"totalPages\":1,\"number\":0,\"size\":20,"
                + "\"first\":true,\"last\":true,\"empty\":false,\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},"
                + "\"pageable\":{\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},\"offset\":0,\"pageNumber\":0,\"pageSize\":20,\"paged\":true,\"unpaged\":false}}";
    }

    private static String userJson() {
        return "{\"id\":3,\"username\":\"e2e-user\",\"displayName\":\"成员\",\"departmentId\":1,"
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

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        SimpleIamClientRestTemplateProperties properties = new SimpleIamClientRestTemplateProperties();
        properties.setBaseUrl(BASE);
        users = new IamUserRestTemplateClient(restTemplate, properties);
        departments = new IamDepartmentRestTemplateClient(restTemplate, properties);
        openRoles = new IamOpenRoleRestTemplateClient(restTemplate, properties);
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
        assertEquals("e2e-user", users.getUser("sub-1").getUsername(), "用户详情");
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
                .andExpect(method(HttpMethod.PATCH))
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

        expectVoid(HttpMethod.PUT, API + "/users/sub-1/roles/5", null);
        users.assignRole("sub-1", 5L);
        server.verify();
        server.reset();

        expectVoid(HttpMethod.DELETE, API + "/users/sub-1/roles/5", null);
        users.revokeRole("sub-1", 5L);
        server.verify();
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
                .andExpect(method(HttpMethod.PATCH))
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

    private void expectVoid(HttpMethod httpMethod, String url, String ifMatch) {
        org.springframework.test.web.client.ResponseActions actions = server.expect(requestTo(url))
                .andExpect(method(httpMethod));
        if (ifMatch != null) {
            actions = actions.andExpect(header("If-Match", ifMatch));
        }
        actions.andRespond(withNoContent());
    }
}
