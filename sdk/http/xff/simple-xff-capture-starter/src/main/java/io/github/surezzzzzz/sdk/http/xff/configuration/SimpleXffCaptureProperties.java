package io.github.surezzzzzz.sdk.http.xff.configuration;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureWebConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple XFF Capture 配置属性。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleXffCaptureConstant.CONFIG_PREFIX)
public class SimpleXffCaptureProperties {

    /**
     * 是否启用 XFF 自动采集。
     */
    private boolean enable = SimpleXffCaptureConstant.DEFAULT_ENABLE;

    /**
     * XFF Filter 注册顺序，未配置时使用稳定默认值。
     */
    private int order = SimpleXffCaptureWebConstant.FILTER_ORDER;

    /**
     * 不自动采集的 Ant 路径模式。
     */
    private List<String> excludedPathPatterns = new ArrayList<String>(
            SimpleXffCaptureConstant.DEFAULT_EXCLUDED_PATH_PATTERNS);

    /**
     * 请求数据采集配置。
     */
    private RequestData requestData = new RequestData();

    /**
     * 请求数据采集的独立维度配置。
     */
    @Data
    public static class RequestData {

        /**
         * 查询参数采集配置。
         */
        private RequestParameter queryParameters = new RequestParameter();

        /**
         * 表单参数采集配置。
         */
        private RequestParameter formParameters = new RequestParameter();

        /**
         * 请求体采集配置。
         */
        private RequestBody body = new RequestBody();

        /**
         * 允许采集请求数据的方法与 URI 规则。
         */
        private List<RequestDataRule> whitelist = new ArrayList<RequestDataRule>();

        /**
         * 禁止采集请求数据的方法与 URI 规则。
         */
        private List<RequestDataRule> blacklist = new ArrayList<RequestDataRule>();
    }

    /**
     * 参数采集开关。
     */
    @Data
    public static class RequestParameter {

        /**
         * 是否采集。
         */
        private boolean enabled;
    }

    /**
     * 请求体采集配置。
     */
    @Data
    public static class RequestBody {

        /**
         * 是否采集。
         */
        private boolean enabled;

        /**
         * 最大采集字节数。
         */
        private long maxBytes = 65536L;

        /**
         * 允许采集的 Content-Type 模式。
         */
        private List<String> allowedContentTypes = new ArrayList<String>();
    }

    /**
     * 请求数据采集方法与 URI 规则。
     */
    @Data
    public static class RequestDataRule {

        /**
         * 应用内部 URI Ant 模式。
         */
        private String pathPattern;

        /**
         * HTTP 方法或 ALL。
         */
        private String method;
    }
}
