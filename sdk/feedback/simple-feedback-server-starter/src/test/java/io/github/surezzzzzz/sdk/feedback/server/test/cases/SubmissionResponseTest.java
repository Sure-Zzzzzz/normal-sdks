package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.controller.response.SubmissionResponse;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 反馈响应在本人和管理视图中的字段边界。
 *
 * @author surezzzzzz
 */
class SubmissionResponseTest {

    @Test
    void testMineHidesManagementFieldsAndAdminKeepsThem() {
        Instant createdTime = Instant.parse("2026-10-15T00:00:00Z");
        FeedbackSubmission submission = FeedbackSubmission.builder()
                .id("submission-1")
                .applicationCode("demo-app")
                .content("反馈内容")
                .submitterSubjectId("subject-1")
                .handlerNote("内部处理备注")
                .reply("公开答复")
                .rowVersion(2)
                .createdTime(createdTime)
                .build();
        FeedbackAttachment attachment = FeedbackAttachment.builder()
                .id("attachment-1")
                .submissionId("submission-1")
                .originalFilename("example.txt")
                .contentType("text/plain")
                .sizeBytes(4)
                .build();

        SubmissionResponse mine = SubmissionResponse.fromMine(submission, Collections.singletonList(attachment));
        SubmissionResponse admin = SubmissionResponse.fromAdmin(submission, Collections.singletonList(attachment));

        assertNull(mine.getSubmitterSubjectId());
        assertNull(mine.getHandlerNote());
        assertEquals("subject-1", admin.getSubmitterSubjectId());
        assertEquals("内部处理备注", admin.getHandlerNote());
        assertEquals("公开答复", mine.getReply());
        assertEquals("公开答复", admin.getReply());
        assertEquals(2, mine.getRowVersion());
        assertEquals(createdTime, admin.getCreatedTime());
        assertEquals("attachment-1", mine.getAttachments().get(0).getId());
        assertEquals("attachment-1", admin.getAttachments().get(0).getId());
    }
}
