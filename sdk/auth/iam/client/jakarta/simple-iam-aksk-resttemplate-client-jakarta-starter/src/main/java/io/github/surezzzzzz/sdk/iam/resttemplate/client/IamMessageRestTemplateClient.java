package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamMessageClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessage;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageBatch;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendRequest;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendResult;
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
 * 站内信 RestTemplate 实现（1.3.7）：发信体直配（可选收件人字段 null 不入），wire 直读解析。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamMessageRestTemplateClient implements IamMessageClient {

    private final IamClientHttpSupport http;

    public IamMessageRestTemplateClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(akskClientRestTemplate, properties.getBaseUrl());
    }

    @Override
    public IamMessageSendResult sendMessage(IamMessageSendRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        if (request.getRecipientSubjectIds() != null) body.put("recipientSubjectIds", request.getRecipientSubjectIds());
        if (request.getDepartmentIds() != null) body.put("departmentIds", request.getDepartmentIds());
        if (request.getUserGroupIds() != null) body.put("userGroupIds", request.getUserGroupIds());
        if (request.getIncludeChildDepartments() != null)
            body.put("includeChildDepartments", request.getIncludeChildDepartments());
        body.put("title", request.getTitle());
        body.put("content", request.getContent());
        JsonNode node = http.post(body, SimpleIamClientConstant.RESOURCE_MESSAGES);
        return IamMessageSendResult.builder()
                .sendBatchId(IamClientHttpSupport.text(node, "sendBatchId"))
                .recipientCount(IamClientHttpSupport.longValue(node, "recipientCount"))
                .build();
    }

    @Override
    public List<IamMessage> listBySubject(String subjectId, Integer page, Integer size) {
        IamClientHttpSupport.Query query = new IamClientHttpSupport.Query()
                .add(SimpleIamClientConstant.QUERY_SUBJECT_ID, subjectId)
                .add(SimpleIamClientConstant.QUERY_PAGE, page)
                .add(SimpleIamClientConstant.QUERY_SIZE, size);
        JsonNode node = http.get(query, SimpleIamClientConstant.RESOURCE_MESSAGES);
        List<IamMessage> messages = new ArrayList<IamMessage>();
        for (JsonNode item : node) {
            messages.add(IamMessage.builder()
                    .title(IamClientHttpSupport.text(item, "title"))
                    .content(IamClientHttpSupport.text(item, "content"))
                    .build());
        }
        return messages;
    }

    @Override
    public IamMessageBatch getBatch(String sendBatchId) {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_MESSAGES, sendBatchId);
        return IamMessageBatch.builder()
                .sendBatchId(IamClientHttpSupport.text(node, "sendBatchId"))
                .title(IamClientHttpSupport.text(node, "title"))
                .content(IamClientHttpSupport.text(node, "content"))
                .senderUsername(IamClientHttpSupport.optionalText(node, "senderUsername"))
                .targetUserCount(IamClientHttpSupport.intValue(node, "targetUserCount"))
                .targetDepartmentCount(IamClientHttpSupport.intValue(node, "targetDepartmentCount"))
                .build();
    }
}
