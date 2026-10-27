package io.github.surezzzzzz.sdk.ticket.server.adaptor.profile;

import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import io.github.surezzzzzz.sdk.ticket.server.adaptor.annotation.SimpleTicketIamAdaptorComponent;
import io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 姓名解析适配（IAM openapi 用户族 getUser；空资料回落 subjectId，上游错误透传）。
 *
 * @author surezzzzzz
 */
@SimpleTicketIamAdaptorComponent
public class IamUserProfileProvider implements SubmitterProfileProvider {

    private static final Logger log = LoggerFactory.getLogger(IamUserProfileProvider.class);

    private final IamUserClient userClient;

    public IamUserProfileProvider(IamUserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public String displayNameOf(String subjectId) {
        IamUser user = userClient.getUser(subjectId);
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        log.warn("工单姓名解析返回空资料，回落主体标识");
        return subjectId;
    }
}
