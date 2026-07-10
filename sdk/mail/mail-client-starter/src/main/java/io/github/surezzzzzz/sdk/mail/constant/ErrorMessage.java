package io.github.surezzzzzz.sdk.mail.constant;

/**
 * Mail 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIGURATION_ERROR = "Mail 配置错误：%s";
    public static final String REQUEST_REQUIRED = "邮件请求不能为空";
    public static final String RECIPIENT_REQUIRED = "收件人不能为空";
    public static final String SUBJECT_REQUIRED = "邮件主题不能为空";
    public static final String CONTENT_REQUIRED = "邮件内容不能为空";
    public static final String CONTENT_TYPE_REQUIRED = "邮件内容类型不能为空";
    public static final String PROVIDER_REQUIRED = "邮件发送 Provider 不能为空";
    public static final String PROVIDER_NOT_SUPPORTED = "邮件发送 Provider 不存在或不支持：%s";
    public static final String SEND_FAILED = "邮件发送失败：%s";
    public static final String CONNECT_FAILED = "邮箱连接失败：%s";
    public static final String READ_FAILED = "邮件读取失败：%s";
    public static final String PARSE_FAILED = "邮件解析失败：%s";
    public static final String ATTACHMENT_FAILED = "附件处理失败：%s";
    public static final String STATUS_CHANGE_FAILED = "邮件状态变更失败：%s";
    public static final String HEADER_REQUIRED = "邮件 Header 名称不能为空";
    public static final String MESSAGE_ID_REQUIRED = "邮件 Message-ID 不能为空";
    public static final String TARGET_FOLDER_REQUIRED = "目标文件夹不能为空";
}
