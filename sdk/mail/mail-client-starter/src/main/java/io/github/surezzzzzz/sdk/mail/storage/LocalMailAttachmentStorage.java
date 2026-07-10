package io.github.surezzzzzz.sdk.mail.storage;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import io.github.surezzzzzz.sdk.mail.exception.MailAttachmentException;
import io.github.surezzzzzz.sdk.mail.model.value.MailAttachment;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import javax.mail.BodyPart;
import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * 本地 Mail 附件存储
 *
 * @author surezzzzzz
 */
@MailComponent
@ConditionalOnMissingBean(MailAttachmentStorage.class)
public class LocalMailAttachmentStorage implements MailAttachmentStorage {

    @Override
    public MailAttachment save(BodyPart bodyPart, String savePath) {
        try {
            String fileName = sanitizeFileName(MimeUtility.decodeText(bodyPart.getFileName()));
            File dir = new File(savePath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new MailAttachmentException(ErrorCode.ATTACHMENT_FAILED,
                        String.format(ErrorMessage.ATTACHMENT_FAILED, savePath));
            }
            File file = new File(dir, fileName);
            try (InputStream inputStream = bodyPart.getInputStream();
                 OutputStream outputStream = Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[MailConstant.BUFFER_SIZE];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
            }
            return MailAttachment.builder()
                    .fileName(fileName)
                    .contentType(bodyPart.getContentType())
                    .size(file.length())
                    .path(file.getAbsolutePath())
                    .build();
        } catch (MailAttachmentException e) {
            throw e;
        } catch (Exception e) {
            throw new MailAttachmentException(ErrorCode.ATTACHMENT_FAILED,
                    String.format(ErrorMessage.ATTACHMENT_FAILED, e.getMessage()), e);
        }
    }

    private String sanitizeFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "attachment";
        }
        return fileName.replace("/", MailConstant.FILE_NAME_SEPARATOR).replace("\\", MailConstant.FILE_NAME_SEPARATOR);
    }
}
