package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.*;
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
 * 受委托角色族 RestTemplate 客户端（认证+传输=底座 akskClientRestTemplate；写操作走 If-Match
 * 乐观并发，revision 取自响应模型字段）。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamOpenRoleRestTemplateClient implements IamOpenRoleClient {

    private final IamClientHttpSupport http;

    /**
     * 创建客户端。
     *
     * @param akskClientRestTemplate 底座预配置 RestTemplate
     * @param properties             IAM 服务配置
     */
    public IamOpenRoleRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    private static IamOpenRole role(JsonNode node) {
        JsonNode bindingNode = node.path("binding");
        io.github.surezzzzzz.sdk.iam.client.model.IamOpenRoleBinding binding =
                bindingNode.isMissingNode() || bindingNode.isNull() ? null
                        : io.github.surezzzzzz.sdk.iam.client.model.IamOpenRoleBinding.builder()
                        .openRoleId(IamClientHttpSupport.optionalText(bindingNode, "openRoleId"))
                        .applicationId(IamClientHttpSupport.optionalLong(bindingNode, "applicationId"))
                        .rootDepartmentId(IamClientHttpSupport.optionalLong(bindingNode, "rootDepartmentId"))
                        .revision(IamClientHttpSupport.longValue(bindingNode, "revision"))
                        .state(IamClientHttpSupport.optionalText(bindingNode, "state"))
                        .build();
        return IamOpenRole.builder()
                .openRoleId(IamClientHttpSupport.text(node, "openRoleId"))
                .externalId(IamClientHttpSupport.optionalText(node, "externalId"))
                .code(IamClientHttpSupport.optionalText(node, "code"))
                .applicationId(IamClientHttpSupport.optionalLong(node, "applicationId"))
                .rootDepartmentId(IamClientHttpSupport.optionalLong(node, "rootDepartmentId"))
                .name(IamClientHttpSupport.optionalText(node, "name"))
                .description(IamClientHttpSupport.optionalText(node, "description"))
                .revision(IamClientHttpSupport.longValue(node, "revision"))
                .state(IamClientHttpSupport.optionalText(node, "state"))
                .rulePresent(node.path("rulePresent").isBoolean()
                        ? Boolean.valueOf(node.path("rulePresent").booleanValue()) : null)
                .binding(binding)
                .build();
    }

    private static IamOpenRoleRule rule(JsonNode node) {
        return IamOpenRoleRule.builder()
                .openRoleId(IamClientHttpSupport.text(node, "openRoleId"))
                .applicationId(IamClientHttpSupport.optionalLong(node, "applicationId"))
                .revision(IamClientHttpSupport.longValue(node, "revision"))
                .pagePermissions(IamClientHttpSupport.stringList(node, "pagePermissions"))
                .apiPermissions(IamClientHttpSupport.stringList(node, "apiPermissions"))
                .dataGrantTemplate(IamClientHttpSupport.optionalMap(node, "dataGrantTemplate"))
                .build();
    }

    @Override
    public IamOpenRole createOpenRole(String externalId, Long applicationId, Long rootDepartmentId, String name,
                                      String description) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("externalId", externalId);
        body.put("applicationId", applicationId);
        body.put("rootDepartmentId", rootDepartmentId);
        body.put("name", name);
        if (description != null) {
            body.put("description", description);
        }
        return role(http.post(body, SimpleIamClientConstant.RESOURCE_ROLES));
    }

    @Override
    public IamOpenRolePage listOpenRoles(Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_ROLES);
        // 受委托角色族分页为 server 自持形态（items/total/page/size，页码 1 起）
        List<IamOpenRole> items = new ArrayList<IamOpenRole>();
        for (JsonNode item : node.path(SimpleIamClientConstant.FIELD_ITEMS)) {
            items.add(role(item));
        }
        return IamOpenRolePage.builder()
                .items(items)
                .total(IamClientHttpSupport.longValue(node, SimpleIamClientConstant.FIELD_TOTAL))
                .page(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_PAGE))
                .size(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_SIZE))
                .build();
    }

    @Override
    public IamOpenRole getOpenRole(String openRoleId) {
        return role(http.get(SimpleIamClientConstant.RESOURCE_ROLES, openRoleId));
    }

    @Override
    public IamOpenRoleRule getOpenRoleRule(String openRoleId, Long applicationId) {
        return rule(http.get(SimpleIamClientConstant.RESOURCE_ROLES, openRoleId,
                SimpleIamClientConstant.RESOURCE_AUTHORIZATION_RULES, String.valueOf(applicationId)));
    }

    @Override
    public IamOpenRoleRule putOpenRoleRule(String openRoleId, Long applicationId, String ifMatch,
                                           Long manifestVersion, String manifestDigest,
                                           List<String> pagePermissions, List<String> apiPermissions,
                                           Map<String, Object> dataGrantTemplate) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("manifestVersion", manifestVersion);
        body.put("manifestDigest", manifestDigest);
        body.put("pagePermissions", pagePermissions);
        body.put("apiPermissions", apiPermissions);
        body.put("dataGrantTemplate", dataGrantTemplate);
        return rule(http.put(ifMatch, body, SimpleIamClientConstant.RESOURCE_ROLES, openRoleId,
                SimpleIamClientConstant.RESOURCE_AUTHORIZATION_RULES, String.valueOf(applicationId)));
    }

    @Override
    public void deleteOpenRoleRule(String openRoleId, Long applicationId, String ifMatch) {
        http.delete(ifMatch, SimpleIamClientConstant.RESOURCE_ROLES, openRoleId,
                SimpleIamClientConstant.RESOURCE_AUTHORIZATION_RULES, String.valueOf(applicationId));
    }

    @Override
    public IamOpenRoleDepartmentPage listOpenRoleDepartments(String openRoleId) {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_ROLES, openRoleId,
                SimpleIamClientConstant.RESOURCE_DEPARTMENTS);
        List<IamOpenRoleDepartment> items = new ArrayList<IamOpenRoleDepartment>();
        for (JsonNode item : node.path(SimpleIamClientConstant.FIELD_ITEMS)) {
            items.add(IamOpenRoleDepartment.builder()
                    .departmentId(IamClientHttpSupport.optionalLong(item, "departmentId"))
                    .inCurrentRoot(IamClientHttpSupport.booleanValue(item, "inCurrentRoot"))
                    .createdAt(IamClientHttpSupport.optionalText(item, "createdAt"))
                    .build());
        }
        return IamOpenRoleDepartmentPage.builder()
                .items(items)
                .total(IamClientHttpSupport.longValue(node, SimpleIamClientConstant.FIELD_TOTAL))
                .page(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_PAGE))
                .size(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_SIZE))
                .revision(IamClientHttpSupport.longValue(node, "revision"))
                .build();
    }

    @Override
    public void mountOpenRoleToDepartment(Long departmentId, String openRoleId, String ifMatch) {
        http.put(ifMatch, new LinkedHashMap<String, Object>(), SimpleIamClientConstant.RESOURCE_DEPARTMENTS,
                String.valueOf(departmentId), SimpleIamClientConstant.RESOURCE_ROLES, openRoleId);
    }

    @Override
    public void unmountOpenRoleFromDepartment(Long departmentId, String openRoleId, String ifMatch) {
        http.delete(ifMatch, SimpleIamClientConstant.RESOURCE_DEPARTMENTS,
                String.valueOf(departmentId), SimpleIamClientConstant.RESOURCE_ROLES, openRoleId);
    }

    @Override
    public IamOrganizationDirectory listOrganizationDirectoryDepartments(Long rootDepartmentId) {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_ORGANIZATION_DIRECTORIES,
                String.valueOf(rootDepartmentId), SimpleIamClientConstant.RESOURCE_DEPARTMENTS);
        List<io.github.surezzzzzz.sdk.iam.client.model.IamOpenDirectoryDepartment> departments =
                new ArrayList<io.github.surezzzzzz.sdk.iam.client.model.IamOpenDirectoryDepartment>();
        for (JsonNode item : node.path("departments")) {
            departments.add(io.github.surezzzzzz.sdk.iam.client.model.IamOpenDirectoryDepartment.builder()
                    .departmentId(IamClientHttpSupport.optionalLong(item, "departmentId"))
                    .code(IamClientHttpSupport.text(item, "code"))
                    .parentId(IamClientHttpSupport.optionalLong(item, "parentId"))
                    .name(IamClientHttpSupport.text(item, "name"))
                    .status(IamClientHttpSupport.optionalInteger(item, "status"))
                    .build());
        }
        return IamOrganizationDirectory.builder()
                .rootDepartmentId(IamClientHttpSupport.optionalLong(node, "rootDepartmentId"))
                .complete(IamClientHttpSupport.booleanValue(node, "complete"))
                .directoryDigest(IamClientHttpSupport.optionalText(node, "directoryDigest"))
                .departments(departments)
                .build();
    }

    @Override
    public IamOrganizationMember getOrganizationDirectoryMember(Long rootDepartmentId, String subjectId) {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_ORGANIZATION_DIRECTORIES,
                String.valueOf(rootDepartmentId), SimpleIamClientConstant.RESOURCE_MEMBERS, subjectId);
        return IamOrganizationMember.builder()
                .subjectId(IamClientHttpSupport.text(node, "subjectId"))
                .status(IamClientHttpSupport.optionalInteger(node, "status"))
                .departmentId(IamClientHttpSupport.optionalLong(node, "departmentId"))
                .inCurrentRoot(IamClientHttpSupport.booleanValue(node, "inCurrentRoot"))
                .build();
    }

    @Override
    public IamTargetApplication getTargetApplication(Long applicationId) {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_TARGET_APPLICATIONS,
                String.valueOf(applicationId));
        return IamTargetApplication.builder()
                .applicationId(IamClientHttpSupport.optionalLong(node, "applicationId"))
                .applicationCode(IamClientHttpSupport.optionalText(node, "applicationCode"))
                .name(IamClientHttpSupport.optionalText(node, "name"))
                .status(IamClientHttpSupport.optionalInteger(node, "status"))
                .builtIn(IamClientHttpSupport.booleanValue(node, "builtIn"))
                .build();
    }

    @Override
    public IamPermissionManifest getTargetApplicationManifest(Long applicationId) {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_TARGET_APPLICATIONS,
                String.valueOf(applicationId), SimpleIamClientConstant.RESOURCE_PERMISSION_MANIFEST);
        return IamPermissionManifest.builder()
                .manifestVersion(IamClientHttpSupport.optionalLong(node, "manifestVersion"))
                .manifestDigest(IamClientHttpSupport.optionalText(node, "manifestDigest"))
                .pagePermissions(IamClientHttpSupport.stringList(node, "pagePermissions"))
                .apiPermissions(IamClientHttpSupport.stringList(node, "apiPermissions"))
                .build();
    }
}
