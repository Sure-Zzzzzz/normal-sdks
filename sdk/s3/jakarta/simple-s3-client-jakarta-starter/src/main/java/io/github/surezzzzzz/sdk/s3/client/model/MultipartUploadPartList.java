package io.github.surezzzzzz.sdk.s3.client.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 列举分段结果。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public class MultipartUploadPartList {

    /**
     * 分段列表
     */
    private final List<MultipartUploadPart> parts;

    /**
     * 下一次列举的起始位置标记；listParts 已聚合全部分页，返回值固定为 0 表示列举完毕
     */
    private final int nextPartNumberMarker;
}
