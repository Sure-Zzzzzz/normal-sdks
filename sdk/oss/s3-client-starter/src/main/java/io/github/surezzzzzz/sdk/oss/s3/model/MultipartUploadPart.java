package io.github.surezzzzzz.sdk.oss.s3.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;

/**
 * 单个分段信息
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class MultipartUploadPart {

    /**
     * 分段编号
     */
    private final int partNumber;

    /**
     * 分段 ETag
     */
    private final String eTag;

    /**
     * 分段大小（字节）
     */
    private final long size;

    /**
     * 分段最后修改时间
     */
    private final Date lastModified;
}
