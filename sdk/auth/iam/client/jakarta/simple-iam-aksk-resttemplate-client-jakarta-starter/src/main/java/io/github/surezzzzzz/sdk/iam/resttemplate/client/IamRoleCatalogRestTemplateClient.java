package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamRoleCatalogClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamRoleCatalogEntry;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色目录 RestTemplate 实现（1.3.7）：只读目录（内置+自定义全量）。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamRoleCatalogRestTemplateClient implements IamRoleCatalogClient {

    private final IamClientHttpSupport http;

    public IamRoleCatalogRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    @Override
    public List<IamRoleCatalogEntry> listRoles(String keyword, Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_KEYWORD, keyword)
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_ROLE_CATALOG);
        List<IamRoleCatalogEntry> roles = new ArrayList<IamRoleCatalogEntry>();
        for (JsonNode item : node) {
            roles.add(IamRoleCatalogEntry.builder()
                    .id(IamClientHttpSupport.optionalLong(item, "id"))
                    .code(IamClientHttpSupport.text(item, "code"))
                    .name(IamClientHttpSupport.text(item, "name"))
                    .description(IamClientHttpSupport.optionalText(item, "description"))
                    .builtIn(IamClientHttpSupport.optionalInteger(item, "builtIn"))
                    .build());
        }
        return roles;
    }
}
