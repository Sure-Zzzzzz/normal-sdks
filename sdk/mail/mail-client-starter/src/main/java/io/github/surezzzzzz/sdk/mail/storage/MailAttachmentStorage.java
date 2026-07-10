package io.github.surezzzzzz.sdk.mail.storage;

import io.github.surezzzzzz.sdk.mail.model.value.MailAttachment;

import javax.mail.BodyPart;

/**
 * Mail 附件存储
 *
 * @author surezzzzzz
 */
public interface MailAttachmentStorage {

    MailAttachment save(BodyPart bodyPart, String savePath);
}
