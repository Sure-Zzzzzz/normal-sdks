package io.github.surezzzzzz.sdk.auth.iam.server.dto.message.request;

import lombok.Data;

import java.util.List;

/**
 * 创建站内信请求
 *
 * @author surezzzzzz
 */
@Data
public class CreateMessageRequest {

    private Long recipientUserId;

    private List<Long> recipientUserIds;

    private List<Long> departmentIds;

    private List<Long> userGroupIds;

    private Boolean includeChildDepartments;

    private String title;

    private String content;
}
