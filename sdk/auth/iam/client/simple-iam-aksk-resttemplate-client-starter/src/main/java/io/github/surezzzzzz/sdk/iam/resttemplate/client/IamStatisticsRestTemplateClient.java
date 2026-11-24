package io.github.surezzzzzz.sdk.iam.resttemplate.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.IamStatisticsClient;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.model.IamStatistics;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateProperties;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.support.IamClientHttpSupport;
import org.springframework.web.client.RestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.transport.AkskRestTemplateResolver;

/**
 * 统计聚合 RestTemplate 实现（1.3.7）：只读快照（wire 同源直读，与管理台同口径）。
 *
 * @author surezzzzzz
 */
@SimpleIamClientRestTemplateComponent
public class IamStatisticsRestTemplateClient implements IamStatisticsClient {

    private final IamClientHttpSupport http;

    public IamStatisticsRestTemplateClient(
            AkskRestTemplateResolver restTemplateResolver,
            SimpleIamClientRestTemplateProperties properties) {
        this.http = new IamClientHttpSupport(restTemplateResolver.resolve(properties.getAkskProfile()), properties.getBaseUrl());
    }

    @Override
    public IamStatistics getStatistics() {
        JsonNode node = http.get(SimpleIamClientConstant.RESOURCE_STATISTICS);
        // wire 为 {counts:{...},stats:{...}} 嵌套（与管理台同源），分层读取
        JsonNode counts = node.path("counts");
        JsonNode stats = node.path("stats");
        return IamStatistics.builder()
                .user(IamClientHttpSupport.longValue(counts, "user"))
                .department(IamClientHttpSupport.longValue(counts, "department"))
                .userGroup(IamClientHttpSupport.longValue(counts, "userGroup"))
                .role(IamClientHttpSupport.longValue(counts, "role"))
                .permission(IamClientHttpSupport.longValue(counts, "permission"))
                .trustedApplication(IamClientHttpSupport.longValue(counts, "trustedApplication"))
                .activeSessions(IamClientHttpSupport.longValue(stats, "activeSessions"))
                .todayLoggedInUsers(IamClientHttpSupport.longValue(stats, "todayLoggedInUsers"))
                .lockedUsers(IamClientHttpSupport.longValue(stats, "lockedUsers"))
                .disabledUsers(IamClientHttpSupport.longValue(stats, "disabledUsers"))
                .usersWithoutDepartment(IamClientHttpSupport.longValue(stats, "usersWithoutDepartment"))
                .build();
    }
}
