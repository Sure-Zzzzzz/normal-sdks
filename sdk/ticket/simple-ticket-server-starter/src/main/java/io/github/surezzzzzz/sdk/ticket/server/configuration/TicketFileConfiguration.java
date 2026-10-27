package io.github.surezzzzzz.sdk.ticket.server.configuration;

import io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

/**
 * 附件出口装配（DESIGN §5.2：默认禁用=散形态；S3 在场且显式开启=聚形态）。
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
@Import(TicketS3FileConfiguration.class)
public class TicketFileConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TicketFileConfiguration.class);

    /**
     * Noop 适配（散形态默认：附件能力关闭，接口明确报错）。
     *
     * @return FileProvider
     */
    @Bean
    @ConditionalOnMissingBean(FileProvider.class)
    public FileProvider noopFileProvider() {
        log.info("工单附件出口：Noop（散形态，附件能力关闭）");
        return new FileProvider() {
            @Override
            public String issueUploadKey(String ticketNo, String fileName, String contentType) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }

            @Override
            public String presignUpload(String objectKey, long size, String contentType, Duration ttl) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }

            @Override
            public String presignDownload(String objectKey, Duration ttl) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }

            @Override
            public boolean exists(String objectKey) {
                return false;
            }

            @Override
            public void delete(String objectKey) {
                // 散形态无对象可清
            }
        };
    }

}
