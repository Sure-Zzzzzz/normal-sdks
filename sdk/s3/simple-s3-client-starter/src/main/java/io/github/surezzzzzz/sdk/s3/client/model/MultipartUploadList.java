package io.github.surezzzzzz.sdk.s3.client.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 进行中分段上传列表结果。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class MultipartUploadList {

    /**
     * 进行中的分段上传列表
     */
    private final List<MultipartUpload> uploads;

    /**
     * 是否还有更多结果未返回；listMultipartUploads 已聚合全部分页，返回值固定为 false
     */
    private final boolean truncated;

    /**
     * 下一次列举的 key 起始位置标记；listMultipartUploads 已聚合全部分页，返回值固定为 null
     */
    private final String nextKeyMarker;

    /**
     * 下一次列举的 uploadId 起始位置标记；listMultipartUploads 已聚合全部分页，返回值固定为 null
     */
    private final String nextUploadIdMarker;
}
