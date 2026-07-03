package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Image Resource Resolver
 *
 * <p>统一解析本地、classpath、data URI 图片，远程图片默认拒绝。
 * 原生 Markdown 相对图片按模板 baseUri 受控解析：
 * <ul>
 *   <li>classpath 模板：相对图片解析为 classpath 资源，禁止 {@code ..} 逃逸</li>
 *   <li>file 模板：相对图片解析后用 {@code toRealPath()} 校验仍在模板目录内</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class ImageResourceResolver {

    private static final String DATA_IMAGE_PREFIX = "data:image/";
    private static final String DATA_PREFIX = "data:";
    private static final String IMAGE_MIME_PREFIX = "image/";
    private static final String BASE64_MARK = ";base64,";
    private static final String CLASSPATH_PREFIX =
            SimpleDocTemplateConstant.URL_SCHEME_CLASSPATH_PREFIX;

    private final ResourceLoader resourceLoader;
    private final TemplateResourcePolicy resourcePolicy;
    private final LimitedInputStreamHelper inputStreamHelper;

    /**
     * 将 SDK 图片解析为 data URI。
     *
     * <p>SDK 图片由业务显式传入，允许 classpath: / file: 绝对 URI 和相对路径。
     *
     * @param image 图片模型
     * @param baseUri 基础 URI
     * @return data URI
     */
    public ResolvedImage resolveToDataUri(Image image, String baseUri) {
        if (image == null) {
            throw TemplateRenderException.markdownRenderFailed(ErrorMessage.IMAGE_NULL);
        }
        return resolve(image.getSrc(), image.getType(), baseUri, true);
    }

    /**
     * 将原生 Markdown 图片 src 解析为 data URI。
     *
     * <p>原生图片只接受相对路径（按 baseUri 受控解析）和 data URI，
     * 拒绝 http/https/file/classpath/jar 等绝对 URI。
     *
     * @param src 图片 src
     * @param type 图片类型
     * @param baseUri 基础 URI
     * @return data URI
     */
    public ResolvedImage resolveToDataUri(String src, Image.ImageType type, String baseUri) {
        return resolve(src, type, baseUri, false);
    }

    private ResolvedImage resolve(String src, Image.ImageType type, String baseUri, boolean sdkImage) {
        if (src == null || src.isEmpty()) {
            throw TemplateRenderException.markdownRenderFailed(ErrorMessage.IMAGE_SRC_EMPTY);
        }
        if (src.startsWith(DATA_IMAGE_PREFIX)) {
            return validateDataUri(src);
        }
        resourcePolicy.validateImageSource(src);
        String scheme = resourcePolicy.schemeOf(src);
        if (!scheme.isEmpty()) {
            return resolveByScheme(src, scheme, type, sdkImage);
        }
        return resolveRelative(src, type, baseUri);
    }

    private ResolvedImage resolveByScheme(String src, String scheme, Image.ImageType type, boolean sdkImage) {
        if (SimpleDocTemplateConstant.URL_SCHEME_CLASSPATH.equals(scheme)) {
            if (!sdkImage) {
                throw TemplateRenderException.markdownSecurityRejected(
                        String.format(ErrorMessage.IMAGE_ABSOLUTE_URI_REJECTED, src));
            }
            byte[] bytes = readClasspathResource(src);
            return toDataUri(bytes, type, src);
        }
        if (SimpleDocTemplateConstant.URL_SCHEME_FILE.equals(scheme)) {
            if (!sdkImage) {
                throw TemplateRenderException.markdownSecurityRejected(
                        String.format(ErrorMessage.IMAGE_ABSOLUTE_URI_REJECTED, src));
            }
            Path path = fileUriToPath(src);
            byte[] bytes = readPath(path, src);
            return toDataUri(bytes, type, src);
        }
        throw TemplateRenderException.markdownSecurityRejected(
                String.format(ErrorMessage.IMAGE_SCHEME_REJECTED, scheme));
    }

    private ResolvedImage resolveRelative(String src, Image.ImageType type, String baseUri) {
        if (baseUri != null
                && SimpleDocTemplateConstant.URL_SCHEME_CLASSPATH.equals(resourcePolicy.schemeOf(baseUri))) {
            String classpathLocation = buildClasspathRelative(src, baseUri);
            byte[] bytes = readClasspathResource(classpathLocation);
            return toDataUri(bytes, type, src);
        }
        Path path = resolveAndContain(src, baseUri);
        byte[] bytes = readPath(path, src);
        return toDataUri(bytes, type, src);
    }

    private String buildClasspathRelative(String src, String baseUri) {
        String normalized = src.replace('\\', '/');
        if (normalized.contains("..")) {
            throw TemplateRenderException.markdownSecurityRejected(
                    String.format(ErrorMessage.IMAGE_PATH_TRAVERSAL, src));
        }
        String basePath = baseUri.substring(CLASSPATH_PREFIX.length());
        String full = basePath.endsWith("/") ? basePath + normalized : basePath + "/" + normalized;
        return CLASSPATH_PREFIX + full;
    }

    private Path fileUriToPath(String src) {
        try {
            return Paths.get(new URI(src));
        } catch (Exception e) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_FILE_URI_INVALID, src), e);
        }
    }

    private Path resolveAndContain(String src, String baseUri) {
        Path srcPath = Paths.get(src);
        if (srcPath.isAbsolute()) {
            return srcPath.normalize();
        }
        if (baseUri == null || baseUri.isEmpty()) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_BASE_URI_MISSING, src));
        }
        if (SimpleDocTemplateConstant.URL_SCHEME_CLASSPATH.equals(resourcePolicy.schemeOf(baseUri))) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_CLASSPATH_BASE_MISUSE, src));
        }
        Path base;
        try {
            base = Paths.get(baseUri).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_BASE_URI_INVALID, baseUri), e);
        }
        Path resolved = base.resolve(src).normalize();
        if (!resolved.startsWith(base)) {
            throw TemplateRenderException.markdownSecurityRejected(
                    String.format(ErrorMessage.IMAGE_PATH_TRAVERSAL, src));
        }
        if (!Files.exists(resolved)) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_NOT_FOUND, src));
        }
        try {
            Path real = resolved.toRealPath();
            if (!real.startsWith(base)) {
                throw TemplateRenderException.markdownSecurityRejected(
                        String.format(ErrorMessage.IMAGE_PATH_TRAVERSAL, src));
            }
            return real;
        } catch (IOException e) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_READ_FAILED, src), e);
        }
    }

    private byte[] readClasspathResource(String classpathLocation) {
        Resource resource = resourceLoader.getResource(classpathLocation);
        if (!resource.exists()) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_NOT_FOUND, classpathLocation));
        }
        try (InputStream is = resource.getInputStream()) {
            return inputStreamHelper.readImageBytes(is);
        } catch (IOException e) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_READ_FAILED, classpathLocation), e);
        }
    }

    private byte[] readPath(Path path, String src) {
        if (!Files.exists(path)) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_NOT_FOUND, src));
        }
        try (InputStream is = Files.newInputStream(path)) {
            return inputStreamHelper.readImageBytes(is);
        } catch (IOException e) {
            throw TemplateRenderException.markdownRenderFailed(
                    String.format(ErrorMessage.IMAGE_READ_FAILED, src), e);
        }
    }

    private ResolvedImage validateDataUri(String src) {
        int markIndex = src.indexOf(BASE64_MARK);
        if (markIndex < 0) {
            throw TemplateRenderException.markdownSecurityRejected(ErrorMessage.IMAGE_DATA_URI_BASE64_ONLY);
        }
        String mime = src.substring(DATA_PREFIX.length(), markIndex);
        if (!mime.startsWith(IMAGE_MIME_PREFIX)) {
            throw TemplateRenderException.markdownSecurityRejected(ErrorMessage.IMAGE_DATA_URI_MIME_INVALID);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(src.substring(markIndex + BASE64_MARK.length()));
            inputStreamHelper.readImageBytes(new ByteArrayInputStream(bytes));
            return new ResolvedImage(src, bytes.length);
        } catch (IllegalArgumentException e) {
            throw TemplateRenderException.markdownSecurityRejected(ErrorMessage.IMAGE_DATA_URI_BASE64_INVALID);
        }
    }

    private ResolvedImage toDataUri(byte[] bytes, Image.ImageType type, String src) {
        String mimeType = mimeType(type, src);
        return new ResolvedImage(
                "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes),
                bytes.length);
    }

    private String mimeType(Image.ImageType type, String src) {
        Image.ImageType imageType = type == null ? Image.ImageType.fromFileName(src) : type;
        if (Image.ImageType.JPEG == imageType) {
            return "image/jpeg";
        }
        if (Image.ImageType.GIF == imageType) {
            return "image/gif";
        }
        return "image/png";
    }

    /**
     * Resolved Image
     */
    @Getter
    public static class ResolvedImage {
        private final String dataUri;
        private final int byteSize;

        public ResolvedImage(String dataUri, int byteSize) {
            this.dataUri = dataUri;
            this.byteSize = byteSize;
        }
    }
}
