package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamUserGroupClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamUserGroup;
import io.github.surezzzzzz.sdk.iam.client.model.IamUserGroupMember;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协作组 RestTemplate 实现（1.3.7）：CRUD+成员管理全形态。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamUserGroupRestTemplateClient implements IamUserGroupClient {

    private final IamClientHttpSupport http;

    public IamUserGroupRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    @Override
    public List<IamUserGroup> listGroups(Integer status, String keyword, Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_STATUS, status)
                .add(SimpleIamClientConstant.QUERY_KEYWORD, keyword)
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_USER_GROUPS);
        List<IamUserGroup> groups = new ArrayList<IamUserGroup>();
        for (JsonNode item : node) {
            groups.add(group(item));
        }
        return groups;
    }

    @Override
    public IamUserGroup createGroup(String code, String name, String description) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("name", name);
        if (description != null) {
            body.put("description", description);
        }
        return group(http.post(body, SimpleIamClientConstant.RESOURCE_USER_GROUPS));
    }

    @Override
    public IamUserGroup getGroup(Long groupId) {
        return group(http.get(SimpleIamClientConstant.RESOURCE_USER_GROUPS, String.valueOf(groupId)));
    }

    @Override
    public IamUserGroup updateGroup(Long groupId, String name, String description) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        if (name != null) {
            body.put("name", name);
        }
        if (description != null) {
            body.put("description", description);
        }
        return group(http.put(null, body, SimpleIamClientConstant.RESOURCE_USER_GROUPS, String.valueOf(groupId)));
    }

    @Override
    public void deleteGroup(Long groupId) {
        http.delete(null, SimpleIamClientConstant.RESOURCE_USER_GROUPS, String.valueOf(groupId));
    }

    @Override
    public List<IamUserGroupMember> listGroupUsers(Long groupId, Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_USER_GROUPS,
                String.valueOf(groupId), SimpleIamClientConstant.RESOURCE_USERS);
        List<IamUserGroupMember> members = new ArrayList<IamUserGroupMember>();
        for (JsonNode item : node) {
            members.add(IamUserGroupMember.builder()
                    .subjectId(IamClientHttpSupport.text(item, "subjectId"))
                    .username(IamClientHttpSupport.text(item, "username"))
                    .displayName(IamClientHttpSupport.optionalText(item, "displayName"))
                    .status(IamClientHttpSupport.optionalInteger(item, "status"))
                    .build());
        }
        return members;
    }

    @Override
    public void assignUser(Long groupId, String subjectId) {
        http.post(new LinkedHashMap<String, Object>(), SimpleIamClientConstant.RESOURCE_USER_GROUPS,
                String.valueOf(groupId), SimpleIamClientConstant.RESOURCE_USERS, subjectId);
    }

    @Override
    public void revokeUser(Long groupId, String subjectId) {
        http.delete(null, SimpleIamClientConstant.RESOURCE_USER_GROUPS, String.valueOf(groupId),
                SimpleIamClientConstant.RESOURCE_USERS, subjectId);
    }

    private IamUserGroup group(JsonNode node) {
        return IamUserGroup.builder()
                .id(IamClientHttpSupport.optionalLong(node, "id"))
                .code(IamClientHttpSupport.text(node, "code"))
                .name(IamClientHttpSupport.text(node, "name"))
                .description(IamClientHttpSupport.optionalText(node, "description"))
                .status(IamClientHttpSupport.optionalInteger(node, "status"))
                .build();
    }
}
