package io.github.surezzzzzz.sdk.mail.constant;

/**
 * Mail 常量
 *
 * @author surezzzzzz
 */
public final class MailConstant {

    private MailConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.mail";
    public static final String SEND_NORMAL_CONFIG_PREFIX = CONFIG_PREFIX + ".send.normal";
    public static final String READ_CONFIG_PREFIX = CONFIG_PREFIX + ".read";
    public static final String PROPERTY_ENABLE = "enable";
    public static final String PROPERTY_TRUE = "true";

    public static final boolean DEFAULT_ENABLE = false;
    public static final boolean DEFAULT_SEND_NORMAL_ENABLE = false;
    public static final boolean DEFAULT_READ_ENABLE = false;
    public static final boolean DEFAULT_ATTACHMENT_OVERWRITE = false;

    public static final String DEFAULT_PROVIDER = "normal";
    public static final String DEFAULT_PROTOCOL = "smtp";
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String DEFAULT_ID_DOMAIN = "localhost";
    public static final String DEFAULT_MESSAGE_ID_HEADER = "X-SUREZZZZZZ-Message-ID";
    public static final String DEFAULT_ATTACHMENT_PATH = "attachments";
    public static final String DEFAULT_INBOX_FOLDER = "INBOX";

    public static final int DEFAULT_SMTP_PORT = 25;
    public static final int DEFAULT_MAX_PAGE_SIZE = 500;
    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_SCAN_SIZE = 2000;
    public static final int BUFFER_SIZE = 4096;

    public static final String HEADER_MESSAGE_ID = "Message-ID";
    public static final String HEADER_IN_REPLY_TO = "In-Reply-To";
    public static final String HEADER_REFERENCES = "References";

    public static final String CONTENT_TYPE_TEXT = "text/plain";
    public static final String CONTENT_TYPE_HTML = "text/html";
    public static final String CONTENT_TYPE_MULTIPART = "multipart";

    public static final String FILE_NAME_SEPARATOR = "_";
}
