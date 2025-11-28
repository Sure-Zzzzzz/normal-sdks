package io.github.surezzzzzz.sdk.prometheus.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/1/19 12:15
 */
@Getter
@Setter
@NoArgsConstructor
@PrometheusComponent
public class PrometheusProperties {

    @Bean
    @ConfigurationProperties("io.github.surezzzzzz.sdk.prometheus.write")
    public WriteServer writeServer() {
        return new WriteServer();
    }

    @Bean
    @ConfigurationProperties("io.github.surezzzzzz.sdk.prometheus.read")
    public ReadServer readServer() {
        return new ReadServer();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public class WriteServer {
        private String host = "http://localhost:9100";
        private String contentType = "application/x-protobuf";
        private String contentEncoding = "snappy";
        private String xPrometheusRemoteWriteVersion = "0.1.0";
        private String username;
        private String password;
        private String writeUri = "/api/v1/write";
        private Integer connectionMaxTotal = 100;
        private Integer connectionMaxPerRoute = 20;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public class ReadServer {
        private String host = "http://localhost:9100";
        private String contentType = "application/x-protobuf";
        private String contentEncoding = "snappy";
        private String xPrometheusRemoteWriteVersion = "0.1.0";
        private String username;
        private String password;
        private String queryUri = "/api/v1/query";
        private String queryRangeUri = "/api/v1/query_range";
        private Integer connectionMaxTotal = 100;
        private Integer connectionMaxPerRoute = 20;
    }
}