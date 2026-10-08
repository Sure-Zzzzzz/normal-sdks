package io.github.surezzzzzz.sdk.iam.client.exception;

import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;

/**
 * 2xx 响应形状不符契约（字段缺失/类型不符/分页形态错）。
 *
 * @author surezzzzzz
 */
public class IamClientProtocolException extends SimpleIamClientException {

    private static final long serialVersionUID = 1L;

    public IamClientProtocolException() {
        super("IAM_PROTOCOL_001", SimpleIamClientConstant.MESSAGE_PROTOCOL_ERROR);
    }
}
