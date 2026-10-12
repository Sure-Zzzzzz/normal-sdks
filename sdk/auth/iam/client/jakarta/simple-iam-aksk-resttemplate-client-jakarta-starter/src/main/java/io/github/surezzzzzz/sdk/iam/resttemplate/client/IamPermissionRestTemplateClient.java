package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamPermissionClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamPermission;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限清单 RestTemplate 实现（1.3.7）：只读目录。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamPermissionRestTemplateClient implements IamPermissionClient {

    private final IamClientHttpSupport http;

    public IamPermissionRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    @Override
    public List<IamPermission> listPermissions(String keyword, Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_KEYWORD, keyword)
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_PERMISSIONS);
        List<IamPermission> permissions = new ArrayList<IamPermission>();
        for (JsonNode item : node) {
            permissions.add(IamPermission.builder()
                    .code(IamClientHttpSupport.text(item, "code"))
                    .name(IamClientHttpSupport.text(item, "name"))
                    .description(IamClientHttpSupport.optionalText(item, "description"))
                    .type(IamClientHttpSupport.text(item, "type"))
                    .builtIn(IamClientHttpSupport.optionalInteger(item, "builtIn"))
                    .build());
        }
        return permissions;
    }
}
