package io.github.surezzzzzz.sdk.prometheus.client.template;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.prometheus.client.annotation.SimplePrometheusClientComponent;
import io.github.surezzzzzz.sdk.prometheus.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.client.constant.SimplePrometheusClientConstant;
import io.github.surezzzzzz.sdk.prometheus.client.exception.PrometheusClientException;
import io.github.surezzzzzz.sdk.prometheus.client.model.QueryInstantResponse;
import io.github.surezzzzzz.sdk.prometheus.client.model.QueryRangeResponse;
import io.github.surezzzzzz.sdk.prometheus.route.model.*;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.xerial.snappy.Snappy;
import prometheus.Remote;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Prometheus Client 门面，基于 Route 模块提供远程写入与查询能力。
 *
 * <p>由自动配置的受限扫描链注册。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimplePrometheusClientComponent
public class PrometheusClientTemplate {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final PrometheusRouteTemplate routeTemplate;

    /**
     * 创建 Prometheus Client 门面。
     *
     * @param routeTemplate Route 模块提供的同步 HTTP 门面
     */
    public PrometheusClientTemplate(PrometheusRouteTemplate routeTemplate) {
        this.routeTemplate = routeTemplate;
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 远程写入（Remote Write）。
     *
     * @param targetKey    Route 配置中的 target 名称
     * @param writeRequest Prometheus Remote Write protobuf 请求
     * @throws PrometheusClientException 请求参数非法、写入被拒绝或收到非预期重定向
     */
    public void write(String targetKey, Remote.WriteRequest writeRequest) {
        validateWriteRequest(writeRequest);

        long startMillis = System.currentTimeMillis();
        log.debug("Prometheus Client Remote Write 开始: targetKey={}, timeseries={}",
                targetKey, writeRequest.getTimeseriesCount());

        byte[] compressed;
        try {
            compressed = Snappy.compress(writeRequest.toByteArray());
        } catch (IOException exception) {
            log.debug("Prometheus Client Remote Write 压缩失败: targetKey={}, errorType={}",
                    targetKey, exception.getClass().getSimpleName());
            throw new PrometheusClientException(ErrorCode.WRITE_REJECTED,
                    String.format(ErrorMessage.WRITE_COMPRESSION_FAILED, targetKey));
        }

        List<PrometheusRouteHeader> headers = Arrays.asList(
                new PrometheusRouteHeader(SimplePrometheusClientConstant.HEADER_CONTENT_TYPE,
                        SimplePrometheusClientConstant.CONTENT_TYPE_PROTOBUF),
                new PrometheusRouteHeader(SimplePrometheusClientConstant.HEADER_CONTENT_ENCODING,
                        SimplePrometheusClientConstant.CONTENT_ENCODING_SNAPPY),
                new PrometheusRouteHeader(SimplePrometheusClientConstant.HEADER_REMOTE_WRITE_VERSION,
                        SimplePrometheusClientConstant.REMOTE_WRITE_VERSION)
        );

        PrometheusRouteRequest request = new PrometheusRouteRequest(
                PrometheusRouteHttpMethod.POST,
                SimplePrometheusClientConstant.WRITE_PATH,
                Collections.<PrometheusRouteParameter>emptyList(),
                headers,
                compressed
        );

        PrometheusRouteResponse response = routeTemplate.exchange(targetKey, request);
        checkStatus(response, targetKey, SimplePrometheusClientConstant.WRITE_PATH, ErrorCode.WRITE_REJECTED);
        log.debug("Prometheus Client Remote Write 成功: targetKey={}, 压缩字节数={}, 状态码={}, 耗时ms={}",
                targetKey, compressed.length, response.getStatusCode(),
                System.currentTimeMillis() - startMillis);
    }

    /**
     * 即时查询。
     *
     * @param targetKey Route 配置中的 target 名称
     * @param promql    PromQL 表达式
     * @param time      查询时间点，为 null 时使用 Prometheus Server 当前时间
     * @return 即时查询响应
     * @throws PrometheusClientException 请求参数非法、查询失败或响应解析失败
     */
    public QueryInstantResponse query(String targetKey, String promql, Instant time) {
        validatePromql(promql);

        long startMillis = System.currentTimeMillis();

        List<PrometheusRouteParameter> parameters = new ArrayList<PrometheusRouteParameter>();
        parameters.add(new PrometheusRouteParameter(SimplePrometheusClientConstant.PARAMETER_QUERY, promql));
        if (time != null) {
            parameters.add(new PrometheusRouteParameter(SimplePrometheusClientConstant.PARAMETER_TIME,
                    formatInstant(time)));
        }

        PrometheusRouteRequest request = new PrometheusRouteRequest(
                PrometheusRouteHttpMethod.GET,
                SimplePrometheusClientConstant.QUERY_PATH,
                parameters,
                Collections.<PrometheusRouteHeader>emptyList(),
                null
        );

        PrometheusRouteResponse response = routeTemplate.exchange(targetKey, request);
        checkStatus(response, targetKey, SimplePrometheusClientConstant.QUERY_PATH, ErrorCode.QUERY_FAILED);
        QueryInstantResponse parsed = readValue(response, QueryInstantResponse.class, targetKey,
                SimplePrometheusClientConstant.QUERY_PATH);
        log.debug("Prometheus Client 即时查询成功: targetKey={}, resultType={}, 序列数={}, 耗时ms={}",
                targetKey, parsed.getData().getResultType(), parsed.getData().getResult().size(),
                System.currentTimeMillis() - startMillis);
        return parsed;
    }

    /**
     * 范围查询。
     *
     * @param targetKey   Route 配置中的 target 名称
     * @param promql      PromQL 表达式
     * @param start       起始时间点
     * @param end         结束时间点
     * @param stepSeconds 采样步长（秒）
     * @return 范围查询响应
     * @throws PrometheusClientException 请求参数非法、查询失败或响应解析失败
     */
    public QueryRangeResponse queryRange(String targetKey, String promql, Instant start, Instant end,
                                         int stepSeconds) {
        validatePromql(promql);
        validateRange(start, end, stepSeconds);

        long startMillis = System.currentTimeMillis();

        List<PrometheusRouteParameter> parameters = Arrays.asList(
                new PrometheusRouteParameter(SimplePrometheusClientConstant.PARAMETER_QUERY, promql),
                new PrometheusRouteParameter(SimplePrometheusClientConstant.PARAMETER_START, formatInstant(start)),
                new PrometheusRouteParameter(SimplePrometheusClientConstant.PARAMETER_END, formatInstant(end)),
                new PrometheusRouteParameter(SimplePrometheusClientConstant.PARAMETER_STEP,
                        String.valueOf(stepSeconds))
        );

        PrometheusRouteRequest request = new PrometheusRouteRequest(
                PrometheusRouteHttpMethod.GET,
                SimplePrometheusClientConstant.QUERY_RANGE_PATH,
                parameters,
                Collections.<PrometheusRouteHeader>emptyList(),
                null
        );

        PrometheusRouteResponse response = routeTemplate.exchange(targetKey, request);
        checkStatus(response, targetKey, SimplePrometheusClientConstant.QUERY_RANGE_PATH, ErrorCode.QUERY_FAILED);
        QueryRangeResponse parsed = readValue(response, QueryRangeResponse.class, targetKey,
                SimplePrometheusClientConstant.QUERY_RANGE_PATH);
        log.debug("Prometheus Client 范围查询成功: targetKey={}, resultType={}, 序列数={}, 耗时ms={}",
                targetKey, parsed.getData().getResultType(), parsed.getData().getResult().size(),
                System.currentTimeMillis() - startMillis);
        return parsed;
    }

    private void validateWriteRequest(Remote.WriteRequest writeRequest) {
        if (writeRequest == null) {
            throw requestIllegal(SimplePrometheusClientConstant.WRITE_PATH,
                    ErrorMessage.REASON_WRITE_REQUEST_NULL);
        }
    }

    private void validatePromql(String promql) {
        if (promql == null || promql.trim().isEmpty()) {
            throw requestIllegal(SimplePrometheusClientConstant.QUERY_PATH, ErrorMessage.REASON_PROMQL_EMPTY);
        }
    }

    private void validateRange(Instant start, Instant end, int stepSeconds) {
        if (start == null || end == null) {
            throw requestIllegal(SimplePrometheusClientConstant.QUERY_RANGE_PATH,
                    ErrorMessage.REASON_RANGE_TIME_NULL);
        }
        if (stepSeconds < SimplePrometheusClientConstant.MIN_STEP_SECONDS) {
            throw requestIllegal(SimplePrometheusClientConstant.QUERY_RANGE_PATH,
                    ErrorMessage.REASON_RANGE_STEP_INVALID);
        }
        if (start.isAfter(end)) {
            throw requestIllegal(SimplePrometheusClientConstant.QUERY_RANGE_PATH,
                    ErrorMessage.REASON_RANGE_ORDER_INVALID);
        }
    }

    private PrometheusClientException requestIllegal(String operation, String reason) {
        return new PrometheusClientException(ErrorCode.REQUEST_ILLEGAL,
                String.format(ErrorMessage.REQUEST_PARAMETER_ILLEGAL, operation, reason));
    }

    /**
     * 检查响应状态并在异常时抛出业务异常。
     *
     * @param response  Route 响应
     * @param targetKey target 名称
     * @param path      请求路径
     * @param errorCode 稳定错误码
     * @throws PrometheusClientException 状态码不在 2xx 范围时抛出
     */
    private void checkStatus(PrometheusRouteResponse response, String targetKey, String path, String errorCode) {
        int statusCode = response.getStatusCode();
        if (statusCode >= SimplePrometheusClientConstant.HTTP_SUCCESS_MIN
                && statusCode < SimplePrometheusClientConstant.HTTP_SUCCESS_MAX) {
            return;
        }

        int responseBodyBytes = responseBodyBytes(response.getBody());
        log.debug("Prometheus Client 收到非成功响应: targetKey={}, path={}, statusCode={}, responseBodyBytes={}",
                targetKey, path, statusCode, responseBodyBytes);
        if (statusCode >= SimplePrometheusClientConstant.HTTP_REDIRECT_MIN
                && statusCode < SimplePrometheusClientConstant.HTTP_REDIRECT_MAX) {
            throw new PrometheusClientException(ErrorCode.UNEXPECTED_REDIRECT,
                    String.format(ErrorMessage.UNEXPECTED_REDIRECT,
                            targetKey, path, statusCode, responseBodyBytes));
        }

        throw new PrometheusClientException(errorCode,
                String.format(ErrorMessage.HTTP_REQUEST_FAILED,
                        targetKey, path, statusCode, responseBodyBytes));
    }

    /**
     * 返回响应体字节数，不将正文写入异常或日志。
     *
     * @param body 原始响应体
     * @return 响应体字节数
     */
    private int responseBodyBytes(byte[] body) {
        return body == null ? 0 : body.length;
    }

    /**
     * 解析并校验 Prometheus 查询响应。
     *
     * @param response  Route 响应
     * @param valueType 目标类型
     * @param targetKey target 名称
     * @param path      请求路径
     * @param <T>       响应类型
     * @return 解析后的对象
     * @throws PrometheusClientException 响应无法解析或结构不符合查询契约时抛出
     */
    private <T> T readValue(PrometheusRouteResponse response, Class<T> valueType, String targetKey, String path) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
            validateResponse(root, targetKey, path);
            return OBJECT_MAPPER.treeToValue(root, valueType);
        } catch (PrometheusClientException exception) {
            throw exception;
        } catch (IOException exception) {
            log.debug("Prometheus Client 响应解析失败: targetKey={}, path={}, responseBodyBytes={}, errorType={}",
                    targetKey, path, responseBodyBytes(response.getBody()),
                    exception.getClass().getSimpleName());
            throw new PrometheusClientException(ErrorCode.RESPONSE_PARSE_FAILED,
                    String.format(ErrorMessage.RESPONSE_PARSE_FAILED, targetKey, path));
        }
    }

    private void validateResponse(JsonNode root, String targetKey, String path) {
        if (root == null || !root.isObject()) {
            throw responseStructureIllegal(targetKey, path, "根节点缺失或不是对象");
        }

        JsonNode status = root.get("status");
        if (status == null || !status.isTextual()) {
            throw responseStructureIllegal(targetKey, path, "status 缺失或不是字符串");
        }
        if (!SimplePrometheusClientConstant.RESPONSE_STATUS_SUCCESS.equals(status.asText())) {
            log.debug("Prometheus Client 收到非 success 业务状态: targetKey={}, path={}",
                    targetKey, path);
            throw new PrometheusClientException(ErrorCode.QUERY_FAILED,
                    String.format(ErrorMessage.RESPONSE_STATUS_FAILED, targetKey, path));
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw responseStructureIllegal(targetKey, path, "data 缺失或不是对象");
        }
        JsonNode resultType = data.get("resultType");
        if (resultType == null || !resultType.isTextual()
                || (!SimplePrometheusClientConstant.RESULT_TYPE_VECTOR.equals(resultType.asText())
                && !SimplePrometheusClientConstant.RESULT_TYPE_MATRIX.equals(resultType.asText()))) {
            throw responseStructureIllegal(targetKey, path, "resultType 仅支持 vector 或 matrix");
        }
        JsonNode result = data.get("result");
        if (result == null || !result.isArray()) {
            throw responseStructureIllegal(targetKey, path, "result 缺失或不是数组");
        }
        validateResultItems(result, resultType.asText(), targetKey, path);
    }

    /**
     * 校验 vector 或 matrix 的序列元素和样本数组形状。
     *
     * @param result     Prometheus 返回的结果数组
     * @param resultType 已校验的结果类型
     * @param targetKey  Route target 名称
     * @param path       固定 Prometheus API 路径
     */
    private void validateResultItems(JsonNode result, String resultType, String targetKey, String path) {
        boolean vector = SimplePrometheusClientConstant.RESULT_TYPE_VECTOR.equals(resultType);
        String samplesField = vector ? "value" : "values";
        for (JsonNode metric : result) {
            if (!metric.isObject()) {
                throw responseStructureIllegal(targetKey, path, "结果元素不是对象");
            }
            JsonNode labels = metric.get("metric");
            JsonNode samples = metric.get(samplesField);
            if (labels == null || !labels.isObject() || samples == null || !samples.isArray()) {
                throw responseStructureIllegal(targetKey, path,
                        "结果元素缺失 metric 对象或 " + samplesField + " 数组");
            }
            if (vector) {
                validateSample(samples, targetKey, path);
                continue;
            }
            for (JsonNode sample : samples) {
                validateSample(sample, targetKey, path);
            }
        }
    }

    /**
     * Prometheus 样本必须是 timestamp 和 value 组成的双元素数组。
     *
     * @param sample    单个样本
     * @param targetKey Route target 名称
     * @param path      固定 Prometheus API 路径
     */
    private void validateSample(JsonNode sample, String targetKey, String path) {
        if (sample == null || !sample.isArray() || sample.size() != 2) {
            throw responseStructureIllegal(targetKey, path, "样本不是双元素数组");
        }
    }

    private PrometheusClientException responseStructureIllegal(String targetKey, String path, String reason) {
        return new PrometheusClientException(ErrorCode.RESPONSE_PARSE_FAILED,
                String.format(ErrorMessage.RESPONSE_STRUCTURE_ILLEGAL, targetKey, path, reason));
    }

    /**
     * 格式化 Instant 为 Prometheus 时间格式（Unix 时间戳，秒并保留三位小数）。
     *
     * @param instant 时间点
     * @return 格式化字符串（小数部分向零截断至三位，如 "1609459200.123"）
     */
    private String formatInstant(Instant instant) {
        BigDecimal timestamp = BigDecimal.valueOf(instant.getEpochSecond())
                .add(BigDecimal.valueOf(instant.getNano(), SimplePrometheusClientConstant.TIME_NANOS_SCALE));
        return timestamp.setScale(SimplePrometheusClientConstant.TIME_FRACTION_SCALE, RoundingMode.DOWN)
                .toPlainString();
    }
}
