package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户族 RestTemplate 客户端（认证+传输=底座 akskClientRestTemplate，本类只做契约拼装与解析）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamClientRestTemplateComponent
public class IamUserRestTemplateClient implements IamUserClient {

    private final IamClientHttpSupport http;

    /**
     * 创建客户端。
     *
     * @param akskClientRestTemplate 底座预配置 RestTemplate
     * @param properties             IAM 服务配置
     */
    public IamUserRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    private static void putIfPresent(Map<String, Object> body, String name, Object value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    private static Map<String, Object> body() {
        return new LinkedHashMap<String, Object>();
    }

    private static IamUser user(JsonNode node) {
        return IamUser.builder()
                .id(IamClientHttpSupport.optionalLong(node, "id"))
                .username(IamClientHttpSupport.text(node, "username"))
                .displayName(IamClientHttpSupport.optionalText(node, "displayName"))
                .departmentId(IamClientHttpSupport.optionalLong(node, "departmentId"))
                .departmentName(IamClientHttpSupport.optionalText(node, "departmentName"))
                .status(IamClientHttpSupport.optionalInteger(node, "status"))
                .email(IamClientHttpSupport.optionalText(node, "email"))
                .phone(IamClientHttpSupport.optionalText(node, "phone"))
                .roles(IamClientHttpSupport.stringList(node, "roles"))
                .build();
    }

    @Override
    public IamSpringPage<IamUser> listUsers(Integer status, Long departmentId, String keyword, Integer page,
                                            Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_STATUS, status)
                .add(SimpleIamClientConstant.QUERY_DEPARTMENT_ID, departmentId)
                .add(SimpleIamClientConstant.QUERY_KEYWORD, keyword)
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_USERS);
        List<IamUser> content = new ArrayList<IamUser>();
        for (JsonNode item : node.path(SimpleIamClientConstant.FIELD_CONTENT)) {
            content.add(user(item));
        }
        return IamSpringPage.<IamUser>builder()
                .content(content)
                .totalElements(IamClientHttpSupport.longValue(node, SimpleIamClientConstant.FIELD_TOTAL_ELEMENTS))
                .totalPages(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_TOTAL_PAGES))
                .number(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_NUMBER))
                .size(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_SIZE))
                .build();
    }

    @Override
    public IamUser getUser(String subjectId) {
        return user(http.get(SimpleIamClientConstant.RESOURCE_USERS, subjectId));
    }

    @Override
    public List<String> getUserRoles(String subjectId) {
        // wire 契约为字符串数组（角色编码裸列表），非对象数组
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_USERS, subjectId, "roles");
        if (!node.isArray()) {
            throw new io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException();
        }
        List<String> roles = new ArrayList<String>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException();
            }
            roles.add(item.textValue());
        }
        return roles;
    }

    @Override
    public IamUser createUser(String username, String password, String displayName, String email, String phone,
                              Long departmentId) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("username", username);
        body.put("password", password);
        putIfPresent(body, "displayName", displayName);
        putIfPresent(body, "email", email);
        putIfPresent(body, "phone", phone);
        putIfPresent(body, "departmentId", departmentId);
        return user(http.post(body, SimpleIamClientConstant.RESOURCE_USERS));
    }

    @Override
    public IamUser updateUser(String subjectId, String displayName, String email, String phone) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        putIfPresent(body, "displayName", displayName);
        putIfPresent(body, "email", email);
        putIfPresent(body, "phone", phone);
        return user(http.patch(body, SimpleIamClientConstant.RESOURCE_USERS, subjectId));
    }

    @Override
    public void deleteUser(String subjectId) {
        http.delete(null, SimpleIamClientConstant.RESOURCE_USERS, subjectId);
    }

    @Override
    public void enableUser(String subjectId) {
        http.put(null, body(), SimpleIamClientConstant.RESOURCE_USERS, subjectId,
                SimpleIamClientConstant.RESOURCE_ENABLE);
    }

    @Override
    public void disableUser(String subjectId) {
        http.put(null, body(), SimpleIamClientConstant.RESOURCE_USERS, subjectId,
                SimpleIamClientConstant.RESOURCE_DISABLE);
    }

    @Override
    public void resetPassword(String subjectId, String newPassword) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("newPassword", newPassword);
        http.put(null, body, SimpleIamClientConstant.RESOURCE_USERS, subjectId,
                SimpleIamClientConstant.RESOURCE_RESET_PASSWORD);
    }

    @Override
    public void assignRole(String subjectId, Long roleId) {
        http.put(null, body(), SimpleIamClientConstant.RESOURCE_USERS, subjectId,
                "roles", String.valueOf(roleId));
    }

    @Override
    public void revokeRole(String subjectId, Long roleId) {
        http.delete(null, SimpleIamClientConstant.RESOURCE_USERS, subjectId,
                "roles", String.valueOf(roleId));
    }
}
