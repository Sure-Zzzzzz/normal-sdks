package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamSessionClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamSession;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话 RestTemplate 实现（1.3.7）：风控视角按人查会话/踢会话。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamSessionRestTemplateClient implements IamSessionClient {

    private final IamClientHttpSupport http;

    public IamSessionRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    @Override
    public List<IamSession> listBySubject(String subjectId, Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_SUBJECT_ID, subjectId)
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_SESSIONS);
        List<IamSession> sessions = new ArrayList<IamSession>();
        for (JsonNode item : node) {
            sessions.add(IamSession.builder()
                    .sessionId(IamClientHttpSupport.text(item, "sessionId"))
                    .subjectId(IamClientHttpSupport.text(item, "subjectId"))
                    .username(IamClientHttpSupport.text(item, "username"))
                    .clientId(IamClientHttpSupport.optionalText(item, "clientId"))
                    .remoteIp(IamClientHttpSupport.optionalText(item, "remoteIp"))
                    .userAgent(IamClientHttpSupport.optionalText(item, "userAgent"))
                    .build());
        }
        return sessions;
    }

    @Override
    public void revokeSession(String sessionId) {
        http.delete(null, SimpleIamClientConstant.RESOURCE_SESSIONS, sessionId);
    }
}
