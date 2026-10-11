package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 站内信发送请求（messages 族，1.3.7）：收件人形态与管理台同构——
 * 显式 subjectId 数组 / 部门 / 协作组并集，至少一项。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamMessageSendRequest {

    /**
     * 显式收件人公开主体数组。
     */
    List<String> recipientSubjectIds;

    /**
     * 部门 ID 数组。
     */
    List<Long> departmentIds;

    /**
     * 协作组 ID 数组。
     */
    List<Long> userGroupIds;

    /**
     * 部门收件是否含子部门。
     */
    Boolean includeChildDepartments;

    /**
     * 标题。
     */
    String title;

    /**
     * 内容。
     */
    String content;
}
