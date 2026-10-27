package io.github.surezzzzzz.sdk.feedback.client.model;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** 提交提示响应。 */
public final class FeedbackSubmissionHint {
    private final List<SelectableApplication> applications;
    private final String contactEmail;

    public FeedbackSubmissionHint(List<SelectableApplication> applications, String contactEmail) {
        this.applications = applications == null
                ? Collections.<SelectableApplication>emptyList()
                : Collections.unmodifiableList(new ArrayList<SelectableApplication>(applications));
        this.contactEmail = contactEmail;
    }

    public List<SelectableApplication> getApplications() { return applications; }
    public String getContactEmail() { return contactEmail; }
}
