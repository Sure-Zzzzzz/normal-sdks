package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataCapturePreparer;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataCaptureResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求数据准备器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class RequestDataCapturePreparerTest {

    @Test
    void shouldCaptureQueryAndBoundedBodyWithoutBreakingDownstreamBody() throws Exception {
        SimpleXffCaptureProperties properties = properties("POST", "/api/**");
        properties.getRequestData().getQueryParameters().setEnabled(true);
        properties.getRequestData().getBody().setEnabled(true);
        properties.getRequestData().getBody().setMaxBytes(8L);
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList("application/json"));
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        String body = "{\"message\":\"long-body\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setQueryString("tag=one&tag=two");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        RequestDataCaptureResult result = preparer.prepare(request);
        byte[] downstreamBody = readAll(result.getRequest().getInputStream());

        log.info("请求数据准备结果：queryStatus={}，bodyStatus={}，capturedBytes={}，downstreamBytes={}",
                result.getSnapshot().getQueryParameters().getStatus(),
                result.getSnapshot().getBody().getStatus(),
                result.getSnapshot().getBody().getCapturedByteCount(), downstreamBody.length);
        assertEquals(RequestDataCaptureStatus.CAPTURED,
                result.getSnapshot().getQueryParameters().getStatus());
        assertEquals(2, result.getSnapshot().getQueryParameters().getValues().get("tag").size());
        assertEquals(RequestBodyCaptureStatus.TRUNCATED,
                result.getSnapshot().getBody().getStatus());
        assertEquals(8L, result.getSnapshot().getBody().getCapturedByteCount());
        assertEquals(body, new String(downstreamBody, StandardCharsets.UTF_8));
    }

    @Test
    void shouldNotTreatContentTypePrefixAsForm() {
        SimpleXffCaptureProperties properties = properties("POST", "/api/**");
        properties.getRequestData().getFormParameters().setEnabled(true);
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        AtomicBoolean parameterMapRead = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders") {
            @Override
            public Map<String, String[]> getParameterMap() {
                parameterMapRead.set(true);
                return super.getParameterMap();
            }
        };
        request.setContentType("application/x-www-form-urlencoded-extra");

        RequestDataCaptureResult result = preparer.prepare(request);

        log.info("伪 Form Content-Type 采集状态：status={}，parameterMapRead={}",
                result.getSnapshot().getFormParameters().getStatus(), parameterMapRead.get());
        assertEquals(RequestDataCaptureStatus.ABSENT,
                result.getSnapshot().getFormParameters().getStatus(),
                "非精确 Form Content-Type 不能进入 Form 采集分支");
        assertFalse(parameterMapRead.get(),
                "非精确 Form Content-Type 不能读取 Servlet 参数 API");
    }

    @Test
    void shouldOnlyCaptureBodyForConfiguredMediaTypeFamily() {
        SimpleXffCaptureProperties properties = properties("POST", "/api/**");
        properties.getRequestData().getBody().setEnabled(true);
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList("application/*+json"));
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("text/example+json");
        request.setContent("sensitive-body".getBytes(StandardCharsets.UTF_8));

        RequestDataCaptureResult result = preparer.prepare(request);
        MockHttpServletRequest structuredJsonRequest = new MockHttpServletRequest("POST", "/api/orders");
        structuredJsonRequest.setContentType("application/example+json");
        structuredJsonRequest.setContent("safe-body".getBytes(StandardCharsets.UTF_8));
        RequestDataCaptureResult structuredJsonResult = preparer.prepare(structuredJsonRequest);

        log.info("媒体类型族采集状态：crossFamily={}，configuredFamily={}",
                result.getSnapshot().getBody().getStatus(),
                structuredJsonResult.getSnapshot().getBody().getStatus());
        assertEquals(RequestBodyCaptureStatus.CONTENT_TYPE_SKIPPED,
                result.getSnapshot().getBody().getStatus(),
                "application/*+json 不能误采集 text/*+json 请求体");
        assertEquals(RequestBodyCaptureStatus.CAPTURED,
                structuredJsonResult.getSnapshot().getBody().getStatus(),
                "application/*+json 必须采集同类型族的结构化 JSON 请求体");
    }

    @Test
    void shouldRejectBlankAllowedContentTypeAtStartup() {
        SimpleXffCaptureProperties properties = new SimpleXffCaptureProperties();
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList(" "));

        log.info("验证空白 Content-Type 配置在启动期被拒绝");
        assertThrows(XffCaptureValidationException.class,
                () -> new RequestDataCapturePreparer(properties),
                "空白 Content-Type 配置必须在启动期失败");
    }

    @Test
    void shouldRejectMalformedStructuredSuffixContentTypeAtStartup() {
        SimpleXffCaptureProperties properties = new SimpleXffCaptureProperties();
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList("application/*+"));

        log.info("验证无后缀结构化 Content-Type 配置在启动期被拒绝");
        assertThrows(XffCaptureValidationException.class,
                () -> new RequestDataCapturePreparer(properties),
                "缺少结构化后缀的 Content-Type 配置必须在启动期失败");
    }

    @Test
    void shouldReplayReadPrefixWhenBodyReadFails() throws Exception {
        SimpleXffCaptureProperties properties = properties("POST", "/api/**");
        properties.getRequestData().getBody().setEnabled(true);
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList(MediaType.APPLICATION_JSON_VALUE));
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders") {
            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    private boolean first = true;

                    @Override
                    public int read() throws IOException {
                        if (first) {
                            first = false;
                            return 'a';
                        }
                        throw new IOException("test body read failure");
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        return read();
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                    }
                };
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);

        RequestDataCaptureResult result = preparer.prepare(request);
        InputStream downstream = result.getRequest().getInputStream();
        int prefixByte = downstream.read();

        log.info("请求体读取失败后的快照与回放：status={}，prefixByte={}",
                result.getSnapshot().getBody().getStatus(), prefixByte);
        assertEquals(RequestBodyCaptureStatus.READ_FAILED,
                result.getSnapshot().getBody().getStatus(),
                "中途读取失败必须明确记录 READ_FAILED");
        assertEquals('a', prefixByte,
                "回放流必须保留采集前成功读取的前缀");
        assertThrows(IOException.class, downstream::read,
                "底层读取失败必须仍由下游感知，不能被 SDK 静默吞掉");
    }

    @Test
    void shouldLeaveDisabledRequestUnwrappedAndUncaptured() throws Exception {
        SimpleXffCaptureProperties properties = new SimpleXffCaptureProperties();
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("body".getBytes(StandardCharsets.UTF_8));

        RequestDataCaptureResult result = preparer.prepare(request);

        log.info("默认请求数据状态：query={}，form={}，body={}，sameRequest={}",
                result.getSnapshot().getQueryParameters().getStatus(),
                result.getSnapshot().getFormParameters().getStatus(),
                result.getSnapshot().getBody().getStatus(), result.getRequest() == request);
        assertTrue(result.getRequest() == request, "默认关闭时不得包装请求");
        assertFalse(result.getSnapshot().getQueryParameters().getStatus()
                == RequestDataCaptureStatus.CAPTURED);
        assertEquals(RequestBodyCaptureStatus.DISABLED,
                result.getSnapshot().getBody().getStatus());
    }

    @Test
    void shouldMarkMethodMismatchWithoutReadingBody() throws Exception {
        SimpleXffCaptureProperties properties = properties("POST", "/api/**");
        properties.getRequestData().getBody().setEnabled(true);
        properties.getRequestData().getBody().setAllowedContentTypes(
                Collections.singletonList("application/json"));
        RequestDataCapturePreparer preparer = new RequestDataCapturePreparer(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("body".getBytes(StandardCharsets.UTF_8));

        RequestDataCaptureResult result = preparer.prepare(request);

        log.info("方法不匹配请求数据状态：body={}，sameRequest={}",
                result.getSnapshot().getBody().getStatus(), result.getRequest() == request);
        assertEquals(RequestBodyCaptureStatus.RULE_NOT_MATCHED,
                result.getSnapshot().getBody().getStatus());
        assertTrue(result.getRequest() == request, "规则不匹配时不得读取或包装请求");
    }

    private SimpleXffCaptureProperties properties(String method, String pathPattern) {
        SimpleXffCaptureProperties properties = new SimpleXffCaptureProperties();
        SimpleXffCaptureProperties.RequestDataRule rule = new SimpleXffCaptureProperties.RequestDataRule();
        rule.setMethod(method);
        rule.setPathPattern(pathPattern);
        properties.getRequestData().setWhitelist(Collections.singletonList(rule));
        return properties;
    }

    private byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
