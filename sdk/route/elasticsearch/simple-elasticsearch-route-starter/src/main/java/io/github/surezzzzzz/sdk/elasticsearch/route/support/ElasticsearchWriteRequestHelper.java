package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.VersionType;

/**
 * Elasticsearch 写请求 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchWriteRequestHelper {

    private ElasticsearchWriteRequestHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static IndexRequest newTypedIndexRequest(String index) {
        return new IndexRequest(index, SimpleElasticsearchRouteConstant.MAPPING_TYPE_DOC);
    }

    public static IndexRequest newTypedIndexRequest(String index, String id) {
        IndexRequest request = newTypedIndexRequest(index);
        if (id != null && !id.trim().isEmpty()) {
            request.id(id);
        }
        return request;
    }

    public static UpdateRequest newTypedUpdateRequest(String index, String id) {
        return new UpdateRequest(index, SimpleElasticsearchRouteConstant.MAPPING_TYPE_DOC, id);
    }

    public static DeleteRequest newTypedDeleteRequest(String index, String id) {
        return new DeleteRequest(index, SimpleElasticsearchRouteConstant.MAPPING_TYPE_DOC, id);
    }

    public static void applyRouting(DocWriteRequest<?> request, String routing) {
        if (request != null && routing != null && !routing.trim().isEmpty()) {
            request.routing(routing);
        }
    }

    public static void applyPipeline(IndexRequest request, String pipeline) {
        if (request != null && pipeline != null && !pipeline.trim().isEmpty()) {
            request.setPipeline(pipeline);
        }
    }

    public static void applyPipeline(BulkRequest request, String pipeline) {
        if (request != null && pipeline != null && !pipeline.trim().isEmpty()) {
            request.pipeline(pipeline);
        }
    }

    public static void applyCreateOpType(IndexRequest request, boolean create) {
        if (request != null && create) {
            request.opType(DocWriteRequest.OpType.CREATE);
        }
    }

    public static void applyRetryOnConflict(UpdateRequest request, Integer retryOnConflict) {
        if (request != null && retryOnConflict != null) {
            request.retryOnConflict(retryOnConflict);
        }
    }

    public static void applyDetectNoop(UpdateRequest request, Boolean detectNoop) {
        if (request != null && detectNoop != null) {
            request.detectNoop(detectNoop);
        }
    }

    public static void applyVersion(DeleteRequest request, Long version) {
        if (request != null && version != null) {
            request.version(version);
        }
    }

    public static void applyVersionType(DeleteRequest request, String versionType) {
        if (request != null && versionType != null && !versionType.trim().isEmpty()) {
            request.versionType(VersionType.fromString(versionType));
        }
    }
}
