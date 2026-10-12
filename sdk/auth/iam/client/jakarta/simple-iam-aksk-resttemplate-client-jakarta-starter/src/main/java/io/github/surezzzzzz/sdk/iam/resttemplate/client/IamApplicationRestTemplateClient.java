package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamApplication;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 可信应用目录 RestTemplate 实现（1.3.7）：复用底座 akskClientRestTemplate 的
 * 认证与传输，Spring Page wire 直读解析（与 users 族同形态，页码 0 起）。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamApplicationRestTemplateClient implements IamApplicationClient {

    private final IamClientHttpSupport http;

    /**
     * 以底座 akskClientRestTemplate 与 baseUrl 组装 HTTP 支撑（与 users 族同形态）。
     *
     * @param akskClientRestTemplate 底座认证 RestTemplate
     * @param properties             客户端配置（baseUrl）
     */
    public IamApplicationRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    @Override
    public IamSpringPage<IamApplication> listApplications(Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_APPLICATIONS);
        List<IamApplication> content = new ArrayList<IamApplication>();
        for (JsonNode item : node.path(SimpleIamClientConstant.FIELD_CONTENT)) {
            content.add(IamApplication.builder()
                    .applicationCode(IamClientHttpSupport.text(item, "applicationCode"))
                    .applicationName(IamClientHttpSupport.text(item, "applicationName"))
                    .status(IamClientHttpSupport.optionalInteger(item, "status"))
                    .build());
        }
        return IamSpringPage.<IamApplication>builder()
                .content(content)
                .totalElements(IamClientHttpSupport.longValue(node, SimpleIamClientConstant.FIELD_TOTAL_ELEMENTS))
                .totalPages(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_TOTAL_PAGES))
                .number(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_NUMBER))
                .size(IamClientHttpSupport.intValue(node, SimpleIamClientConstant.FIELD_SIZE))
                .build();
    }
}
