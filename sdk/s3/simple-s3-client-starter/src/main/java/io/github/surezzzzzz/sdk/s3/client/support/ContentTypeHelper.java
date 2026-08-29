package io.github.surezzzzzz.sdk.s3.client.support;

import io.github.surezzzzzz.sdk.s3.client.constant.SimpleS3ClientConstant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Content-Type 推断工具（迁自老 s3-client-starter，映射表不变）。
 *
 * @author surezzzzzz
 */
public final class ContentTypeHelper {

    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE;

    static {
        Map<String, String> map = new HashMap<>();

        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("png", "image/png");
        map.put("gif", "image/gif");
        map.put("bmp", "image/bmp");
        map.put("webp", "image/webp");
        map.put("svg", "image/svg+xml");

        map.put("pdf", "application/pdf");
        map.put("doc", "application/msword");
        map.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        map.put("xls", "application/vnd.ms-excel");
        map.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        map.put("ppt", "application/vnd.ms-powerpoint");
        map.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        map.put("txt", "text/plain");
        map.put("rtf", "application/rtf");
        map.put("odt", "application/vnd.oasis.opendocument.text");

        map.put("zip", "application/zip");
        map.put("rar", "application/vnd.rar");
        map.put("7z", "application/x-7z-compressed");
        map.put("tar", "application/x-tar");
        map.put("gz", "application/gzip");

        map.put("mp4", "video/mp4");
        map.put("webm", "video/webm");
        map.put("mov", "video/quicktime");
        map.put("avi", "video/x-msvideo");
        map.put("mkv", "video/x-matroska");

        map.put("mp3", "audio/mpeg");
        map.put("wav", "audio/wav");
        map.put("ogg", "audio/ogg");

        map.put("html", "text/html");
        map.put("htm", "text/html");
        map.put("css", "text/css");
        map.put("js", "application/javascript");
        map.put("json", "application/json");
        map.put("xml", "application/xml");
        map.put("java", "text/x-java-source");
        map.put("py", "text/x-python");
        map.put("c", "text/x-c");
        map.put("cpp", "text/x-c++");
        map.put("sh", "application/x-sh");

        EXTENSION_TO_CONTENT_TYPE = Collections.unmodifiableMap(map);
    }

    private ContentTypeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 根据对象 key 的扩展名推断 Content-Type。
     *
     * @param objectKey 对象 key
     * @return 推断的 Content-Type，未知或无扩展名时返回默认值
     */
    public static String getContentType(String objectKey) {
        if (objectKey == null || !objectKey.contains(".")) {
            return SimpleS3ClientConstant.DEFAULT_CONTENT_TYPE;
        }
        String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1).toLowerCase();
        return EXTENSION_TO_CONTENT_TYPE.getOrDefault(extension, SimpleS3ClientConstant.DEFAULT_CONTENT_TYPE);
    }
}
