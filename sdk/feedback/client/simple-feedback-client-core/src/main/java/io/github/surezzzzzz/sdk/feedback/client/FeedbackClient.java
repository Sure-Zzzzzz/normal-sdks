package io.github.surezzzzzz.sdk.feedback.client;

import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackDownload;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackPage;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmissionHint;
import io.github.surezzzzzz.sdk.feedback.client.model.SelectableApplication;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;

import java.util.List;
import java.util.Map;

/**
 * Feedback 机器调用契约。实现由认证传输 starter 提供；当前版本提供 AKSK RestTemplate 实现。
 *
 * <p>参数 Map 是公开 wire（网络字段）面，不复用 server 内部 DTO；写操作的
 * 幂等键由调用方生成并在失败重试时复用。</p>
 *
 * @author surezzzzzz
 */
public interface FeedbackClient {

    /** 提交反馈。 */
    FeedbackSubmission createSubmission(Map<String, Object> submission,
                                        List<FeedbackAttachmentPart> attachments,
                                        String idempotencyKey, String correlationId);

    /** 管理列表。 */
    FeedbackPage listSubmissions(Map<String, ?> query);

    /** 管理详情。 */
    FeedbackSubmission getSubmission(String id);

    /** 处理反馈。 */
    FeedbackSubmission handleSubmission(String id, Map<String, Object> handling,
                                        String correlationId);

    /** 管理附件下载。 */
    FeedbackDownload downloadSubmissionAttachment(String id, String attachmentId);

    /** 本人列表。 */
    FeedbackPage listMySubmissions(Map<String, ?> query);

    /** 本人详情。 */
    FeedbackSubmission getMySubmission(String id);

    /** 本人附件下载。 */
    FeedbackDownload downloadMySubmissionAttachment(String id, String attachmentId);

    /** 提交提示。 */
    FeedbackSubmissionHint getSubmissionHints();

    /** 可选应用。 */
    List<SelectableApplication> listSelectableApplications();

    /** 注册表列表。 */
    List<Map<String, Object>> listApplications();

    /** 登记应用。 */
    Map<String, Object> createApplication(Map<String, Object> application,
                                          String correlationId);

    /** 更新注册项。 */
    Map<String, Object> updateApplication(String applicationCode,
                                          Map<String, Object> application,
                                          String correlationId);
}
