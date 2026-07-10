package io.github.surezzzzzz.sdk.mail.configuration;

import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Mail 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(MailConstant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = MailConstant.CONFIG_PREFIX, name = MailConstant.PROPERTY_ENABLE, havingValue = MailConstant.PROPERTY_TRUE)
public class MailProperties {

    /**
     * 是否启用
     */
    private boolean enable = MailConstant.DEFAULT_ENABLE;

    /**
     * 发送配置
     */
    private Send send = new Send();

    /**
     * 读取配置
     */
    private Read read = new Read();

    /**
     * 附件配置
     */
    private Attachment attachment = new Attachment();

    @Data
    public static class Send {

        /**
         * 默认 Provider
         */
        private String defaultProvider = MailConstant.DEFAULT_PROVIDER;

        /**
         * 普通 SMTP 配置
         */
        private Normal normal = new Normal();
    }

    @Data
    public static class Normal {

        /**
         * 是否启用
         */
        private boolean enable = MailConstant.DEFAULT_SEND_NORMAL_ENABLE;

        /**
         * SMTP 主机
         */
        private String host;

        /**
         * SMTP 端口
         */
        private int port = MailConstant.DEFAULT_SMTP_PORT;

        /**
         * 协议
         */
        private String protocol = MailConstant.DEFAULT_PROTOCOL;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * 默认编码
         */
        private String defaultEncoding = MailConstant.DEFAULT_ENCODING;

        /**
         * Message-ID 域名
         */
        private String idDomain = MailConstant.DEFAULT_ID_DOMAIN;

        /**
         * 自定义 Message-ID Header
         */
        private String customMessageIdHeader = MailConstant.DEFAULT_MESSAGE_ID_HEADER;

        /**
         * JavaMail 属性
         */
        private Map<String, Object> properties = new HashMap<>();
    }

    @Data
    public static class Read {

        /**
         * 是否启用
         */
        private boolean enable = MailConstant.DEFAULT_READ_ENABLE;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * 最大分页大小
         */
        private int maxPageSize = MailConstant.DEFAULT_MAX_PAGE_SIZE;

        /**
         * 最大扫描数量
         */
        private int maxScanSize = MailConstant.DEFAULT_MAX_SCAN_SIZE;

        /**
         * JavaMail 属性
         */
        private Map<String, Object> properties = new HashMap<>();
    }

    @Data
    public static class Attachment {

        /**
         * 本地保存路径
         */
        private String localSavePath = MailConstant.DEFAULT_ATTACHMENT_PATH;

        /**
         * 是否覆盖同名文件
         */
        private boolean overwrite = MailConstant.DEFAULT_ATTACHMENT_OVERWRITE;
    }
}
