package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Elasticsearch DSL 机械兼容 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchDslCompatibilityHelper {

    private ElasticsearchDslCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String removeFieldRecursively(String json, String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return json;
        }
        return removeFieldsRecursively(json, Arrays.asList(fieldName));
    }

    public static String removeFieldsRecursively(String json, Collection<String> fieldNames) {
        if (json == null || json.trim().isEmpty() || fieldNames == null || fieldNames.isEmpty()) {
            return json;
        }
        try {
            Object mapper = createObjectMapper();
            Method readValue = ElasticsearchReflectionHelper.loadMethod(mapper.getClass(),
                    SimpleElasticsearchRouteConstant.METHOD_READ_VALUE, String.class, Class.class);
            Object root = ElasticsearchReflectionHelper.invoke(readValue, mapper, json, Map.class);
            removeFields(root, fieldNames);
            Method writeValueAsString = ElasticsearchReflectionHelper.loadMethod(mapper.getClass(),
                    SimpleElasticsearchRouteConstant.METHOD_WRITE_VALUE_AS_STRING, Object.class);
            return (String) ElasticsearchReflectionHelper.invoke(writeValueAsString, mapper, root);
        } catch (Exception e) {
            return removeFieldsByRegex(json, fieldNames);
        }
    }

    public static String removeCompositeUnsupportedFields(String json, ClusterInfo clusterInfo) {
        if (ElasticsearchVersionHelper.supportsCompositeMissingBucket(clusterInfo)) {
            return json;
        }
        return removeFieldsRecursively(json, unsupportedCompositeFields());
    }

    public static String removeEs7CompositeUnsupportedFieldsForEs6(String json, ClusterInfo clusterInfo) {
        return removeCompositeUnsupportedFields(json, clusterInfo);
    }

    public static String removeEs7CompositeUnsupportedFieldsForEs6(String json) {
        return removeFieldsRecursively(json, unsupportedCompositeFields());
    }

    @SuppressWarnings("unchecked")
    private static void removeFields(Object value, Collection<String> fieldNames) {
        if (value instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            Iterator<Object> iterator = map.keySet().iterator();
            while (iterator.hasNext()) {
                Object key = iterator.next();
                if (key != null && fieldNames.contains(String.valueOf(key))) {
                    iterator.remove();
                }
            }
            for (Object child : map.values()) {
                removeFields(child, fieldNames);
            }
            return;
        }
        if (value instanceof Collection) {
            for (Object child : (Collection<?>) value) {
                removeFields(child, fieldNames);
            }
        }
    }

    private static Collection<String> unsupportedCompositeFields() {
        List<String> fields = new ArrayList<>();
        fields.add(SimpleElasticsearchRouteConstant.AGG_COMPOSITE_FIELD_MISSING_BUCKET);
        fields.add(SimpleElasticsearchRouteConstant.AGG_COMPOSITE_FIELD_MISSING_ORDER);
        return fields;
    }

    private static Object createObjectMapper() {
        return ElasticsearchReflectionHelper.newInstance(
                ElasticsearchReflectionHelper.loadConstructor(
                        ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.CLASS_JACKSON_OBJECT_MAPPER)));
    }

    private static String removeFieldsByRegex(String json, Collection<String> fieldNames) {
        String result = json;
        for (String fieldName : fieldNames) {
            String quotedField = java.util.regex.Pattern.quote(fieldName);
            result = result
                    .replaceAll(",\\s*\"" + quotedField + "\"\\s*:\\s*(true|false|null|\"[^\"]*\"|-?\\d+(?:\\.\\d+)?)", "")
                    .replaceAll("\"" + quotedField + "\"\\s*:\\s*(true|false|null|\"[^\"]*\"|-?\\d+(?:\\.\\d+)?)\\s*,", "")
                    .replaceAll("\"" + quotedField + "\"\\s*:\\s*(true|false|null|\"[^\"]*\"|-?\\d+(?:\\.\\d+)?)", "");
        }
        return result;
    }
}
