package io.github.surezzzzzz.sdk.auth.iam.server.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.claim.DataGrantDocumentClaimMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * IAM 应用授权持久化 JSON 编解码器。
 *
 * @author surezzzzzz
 */
public final class IamApplicationAuthorizationJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private static final JavaType STRING_LIST_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructCollectionType(List.class, String.class);
    private static final JavaType MAP_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructMapType(Map.class, String.class, Object.class);
    private static final JavaType DATA_RESOURCE_LIST_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructCollectionType(List.class, DataResourceDeclaration.class);

    private IamApplicationAuthorizationJsonCodec() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * JSON 数组字符串安全反序列化为列表（空 / 非法输入返回空列表）
     */
    public static List<String> readStringList(String value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName);
        }
        try {
            List<String> result = OBJECT_MAPPER.readValue(value, STRING_LIST_TYPE);
            if (result == null) {
                throw invalid(fieldName);
            }
            return Collections.unmodifiableList(new ArrayList<String>(result));
        } catch (JsonProcessingException exception) {
            throw invalid(fieldName);
        }
    }

    /**
     * JSON 对象字符串安全反序列化为数据授权文档（空 / 非法输入返回空 Map）
     */
    public static DataGrantDocument readDataGrantDocument(String value) {
        if (value == null) {
            return null;
        }
        try {
            Map<String, Object> claim = OBJECT_MAPPER.readValue(value, MAP_TYPE);
            return DataGrantDocumentClaimMapper.fromClaim(claim);
        } catch (RuntimeException exception) {
            throw invalid("dataGrantDocument");
        } catch (JsonProcessingException exception) {
            throw invalid("dataGrantDocument");
        }
    }

    /**
     * 数据授权文档序列化为 JSON 字符串（null 输入返回 null）
     */
    public static String writeDataGrantDocument(Map<String, Object> document) {
        if (document == null) {
            return null;
        }
        try {
            DataGrantDocumentClaimMapper.fromClaim(document);
            return OBJECT_MAPPER.writeValueAsString(document);
        } catch (RuntimeException exception) {
            throw invalid("dataGrantDocument");
        } catch (JsonProcessingException exception) {
            throw invalid("dataGrantDocument");
        }
    }

    /**
     * 请求体 Map 解析为已校验的数据授权文档（null 输入返回 null；结构或取值非法抛应用授权内容异常）
     */
    public static DataGrantDocument parseDataGrantDocument(Map<String, Object> document) {
        if (document == null) {
            return null;
        }
        try {
            return DataGrantDocumentClaimMapper.fromClaim(document);
        } catch (RuntimeException exception) {
            throw invalid("dataGrantTemplate");
        }
    }

    /**
     * 已构造的数据授权文档序列化为规范化 JSON 字符串（null 输入返回 null）
     */
    public static String writeDataGrantDocument(DataGrantDocument document) {
        if (document == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(DataGrantDocumentClaimMapper.toClaim(document));
        } catch (RuntimeException exception) {
            throw invalid("dataGrantDocument");
        } catch (JsonProcessingException exception) {
            throw invalid("dataGrantDocument");
        }
    }

    /**
     * DATA 资源声明列表序列化为 JSON 数组字符串（null 输入返回 null）
     */
    public static String writeDataResourceList(List<DataResourceDeclaration> declarations) {
        if (declarations == null) {
            throw invalid("dataResources");
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(declarations);
        } catch (JsonProcessingException exception) {
            throw invalid("dataResources");
        }
    }

    /**
     * JSON 数组字符串安全反序列化为 DATA 资源声明列表（null 输入抛异常，空 / 非法输入返回空列表）
     */
    public static List<DataResourceDeclaration> readDataResourceList(String value) {
        if (value == null) {
            throw invalid("dataResources");
        }
        try {
            List<DataResourceDeclaration> result = OBJECT_MAPPER.readValue(value, DATA_RESOURCE_LIST_TYPE);
            if (result == null) {
                throw invalid("dataResources");
            }
            return Collections.unmodifiableList(new ArrayList<DataResourceDeclaration>(result));
        } catch (JsonProcessingException exception) {
            throw invalid("dataResources");
        }
    }

    /**
     * 应用维度数据授权文档批量反序列化
     */
    public static Map<String, Object> readDataGrantDocumentMap(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw invalid("dataGrantDocument");
        }
    }

    /**
     * 字符串列表序列化为 JSON 数组字符串（null 输入返回 null）
     */
    public static String writeStringList(List<String> values) {
        if (values == null) {
            throw invalid("permissionList");
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw invalid("permissionList");
        }
    }

    private static SimpleIamServerException invalid(String fieldName) {
        return new SimpleIamServerException("应用授权数据无效：" + fieldName);
    }
}
