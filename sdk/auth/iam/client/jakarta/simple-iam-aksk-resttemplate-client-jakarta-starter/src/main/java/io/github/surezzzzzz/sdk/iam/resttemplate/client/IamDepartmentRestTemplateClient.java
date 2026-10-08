package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamDepartment;
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
 * 部门族 RestTemplate 客户端（认证+传输=底座 akskClientRestTemplate）。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamDepartmentRestTemplateClient implements IamDepartmentClient {

    private final IamClientHttpSupport http;

    /**
     * 创建客户端。
     *
     * @param akskClientRestTemplate 底座预配置 RestTemplate
     * @param properties             IAM 服务配置
     */
    public IamDepartmentRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    private static void putIfPresent(Map<String, Object> body, String name, Object value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    static IamDepartment department(JsonNode node) {
        return IamDepartment.builder()
                .id(IamClientHttpSupport.optionalLong(node, "id"))
                .code(IamClientHttpSupport.text(node, "code"))
                .name(IamClientHttpSupport.text(node, "name"))
                .parentId(IamClientHttpSupport.optionalLong(node, "parentId"))
                .fullPath(IamClientHttpSupport.optionalText(node, "fullPath"))
                .sortOrder(IamClientHttpSupport.optionalInteger(node, "sortOrder"))
                .status(IamClientHttpSupport.optionalInteger(node, "status"))
                .build();
    }

    @Override
    public List<IamDepartment> listDepartments() {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_DEPARTMENTS);
        if (!node.isArray()) {
            throw new io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException();
        }
        List<IamDepartment> departments = new ArrayList<IamDepartment>();
        for (JsonNode item : node) {
            departments.add(department(item));
        }
        return departments;
    }

    @Override
    public IamDepartment getDepartment(Long departmentId) {
        return department(http.get(SimpleIamClientConstant.RESOURCE_DEPARTMENTS,
                String.valueOf(departmentId)));
    }

    @Override
    public IamDepartment createDepartment(String code, String name, Long parentId, Integer sortOrder,
                                          Integer status, List<String> memberSubjectIds) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("name", name);
        putIfPresent(body, "parentId", parentId);
        putIfPresent(body, "sortOrder", sortOrder);
        putIfPresent(body, "status", status);
        putIfPresent(body, "memberSubjectIds", memberSubjectIds);
        return department(http.post(body, SimpleIamClientConstant.RESOURCE_DEPARTMENTS));
    }

    @Override
    public IamDepartment updateDepartment(Long departmentId, String name, Long parentId, Integer sortOrder,
                                          Integer status) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        putIfPresent(body, "name", name);
        putIfPresent(body, "parentId", parentId);
        putIfPresent(body, "sortOrder", sortOrder);
        putIfPresent(body, "status", status);
        return department(http.patch(body, SimpleIamClientConstant.RESOURCE_DEPARTMENTS,
                String.valueOf(departmentId)));
    }

    @Override
    public void deleteDepartment(Long departmentId) {
        http.delete(null, SimpleIamClientConstant.RESOURCE_DEPARTMENTS, String.valueOf(departmentId));
    }
}
