package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamMessage;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageBatch;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendRequest;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendResult;

import java.util.List;

/**
 * IAM 站内信 openapi 契约（3 方法，对位 server /iam/api/messages，1.3.7）。
 *
 * <p>消费方 AKP 需授 {@code iam:message:api}；无 DATA 面。发信收件人形态与管理台同构
 * （subjectId 数组 / 部门 / 协作组并集）；按收件人查信 subjectId 必填（防全库遍历）。</p>
 *
 * @author surezzzzzz
 */
public interface IamMessageClient {

    /**
     * 发送站内信（201 返回批次标识与收件人数）。
     *
     * @param request 收件人与内容（recipients 至少一项）
     * @return 批次标识与命中收件人数
     */
    IamMessageSendResult sendMessage(IamMessageSendRequest request);

    /**
     * 按收件人查信（分页，页码 1 起；subjectId 必填）。
     *
     * @param subjectId 收件人公开主体
     * @param page      页码（1 起）
     * @param size      页大小
     * @return 信件列表（标题/内容/时间/已读）
     */
    List<IamMessage> listBySubject(String subjectId, Integer page, Integer size);

    /**
     * 发送批次详情（对账）。
     *
     * @param sendBatchId 批次标识
     * @return 批次元数据
     */
    IamMessageBatch getBatch(String sendBatchId);
}
