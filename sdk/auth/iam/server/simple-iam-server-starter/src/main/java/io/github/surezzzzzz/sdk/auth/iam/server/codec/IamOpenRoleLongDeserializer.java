package io.github.surezzzzzz.sdk.auth.iam.server.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;

import java.io.IOException;

/**
 * 开放角色整数只接受 JSON 整数标记，不截断小数或转换字符串；不改变宿主的全局解析规则。
 *
 * @author surezzzzzz
 */
public class IamOpenRoleLongDeserializer extends JsonDeserializer<Long> {
    /**
     * 保留完整的有符号 64 位整数；正数及必填约束继续由领域入口校验。
     */
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            throw MismatchedInputException.from(parser, Long.class, ServerErrorMessage.OPEN_ROLE_INVALID);
        }
        return parser.getLongValue();
    }
}
