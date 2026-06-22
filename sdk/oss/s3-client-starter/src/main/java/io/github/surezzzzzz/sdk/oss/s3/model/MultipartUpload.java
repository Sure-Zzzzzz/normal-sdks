package io.github.surezzzzzz.sdk.oss.s3.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;

/**
 * 进行中的分段上传信息
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class MultipartUpload {

    /**
     * 分段上传 uploadId
     */
    private final String uploadId;

    /**
     * 对象 key
     */
    private final String key;

    /**
     * 分段上传发起时间
     */
    private final Date initiated;
}
