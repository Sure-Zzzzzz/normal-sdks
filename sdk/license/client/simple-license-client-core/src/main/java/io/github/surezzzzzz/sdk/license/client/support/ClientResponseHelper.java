package io.github.surezzzzzz.sdk.license.client.support;

import io.github.surezzzzzz.sdk.license.client.constant.ClientProtocolConstant;
import io.github.surezzzzzz.sdk.license.client.model.ClientReservation;
import io.github.surezzzzzz.sdk.license.client.model.GrantCandidate;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionPage;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionRequest;
import io.github.surezzzzzz.sdk.license.client.model.QuotaRequest;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator.ProtocolViolationException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 传输响应的严格标量读取与信任范围校验，不把错误类型便利转换为有效资料。
 *
 * @author surezzzzzz
 */
public final class ClientResponseHelper {

    private static final Set<String> TRUST_MATERIAL_FIELDS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("issuerId", "productId", "sequence", "issuedAt",
                    "snapshotId", "manifestJws", "revocationPartJws", "trustedPublicKeysJson")));

    private ClientResponseHelper() {
    }

    /** 文本必须是非空字符串，不能把数字或对象转为编号。 */
    public static String text(Map<String, Object> response, String field) {
        Object value = response == null ? null : response.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new ProtocolViolationException("响应文本字段缺失或类型无效：" + field);
        }
        return (String) value;
    }

    /** 仅接收 JSON 安全范围内的非负整数，不截断浮点与超范围大整数。 */
    public static long number(Map<String, Object> response, String field) {
        Object value = response == null ? null : response.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger)) {
            throw new ProtocolViolationException("响应整数字段缺失或类型无效：" + field);
        }
        BigInteger integer = new BigInteger(value.toString());
        if (integer.signum() < 0
                || integer.compareTo(BigInteger.valueOf(ClientProtocolConstant.SAFE_INTEGER_MAX)) > 0) {
            throw new ProtocolViolationException("响应整数字段超出安全范围：" + field);
        }
        return integer.longValue();
    }

    /** 唯一非安全整数例外是公开契约规定的永久额度截止标记，其他数值仍严格拒绝。 */
    public static long reservationDeadline(Map<String, Object> response) {
        Object value = response == null ? null : response.get("validUntil");
        if ((value instanceof Long || value instanceof BigInteger)
                && new BigInteger(value.toString()).equals(BigInteger.valueOf(Long.MAX_VALUE))) {
            return Long.MAX_VALUE;
        }
        return number(response, "validUntil");
    }

    /** 完整额度回执逐字段核验，终态原样保留，不把幂等回读当作新的有效预占。 */
    public static ClientReservation reservation(Map<String, Object> response) {
        Set<String> fields = new HashSet<String>(Arrays.asList("reservationId", "grantId", "metric", "quantity",
                "kind", "action", "grantRevision", "businessOperationId", "fence", "validUntil", "state"));
        if (response == null || !fields.equals(response.keySet())) {
            throw new ProtocolViolationException("额度回执字段缺失或未知");
        }
        String kind = text(response, "kind");
        String state = text(response, "state");
        long quantity = number(response, "quantity");
        long revision = number(response, "grantRevision");
        if (!("CONCURRENT".equals(kind) || "CONSUMPTION".equals(kind))
                || !("ACTIVE".equals(state) || "RELEASED".equals(state) || "EXPIRED".equals(state))
                || quantity < 1 || revision < 1) {
            throw new ProtocolViolationException("额度回执种类、状态、数量或修订无效");
        }
        return new ClientReservation(text(response, "reservationId"), text(response, "grantId"),
                text(response, "metric"), quantity, kind, text(response, "action"), revision,
                text(response, "businessOperationId"), number(response, "fence"), reservationDeadline(response), state);
    }

    /** 创建或幂等回读必须关联本次全部不可变请求上下文，不能采用其他操作的回执。 */
    public static ClientReservation quotaReservation(Map<String, Object> response, QuotaRequest request, String kind) {
        ClientReservation receipt = reservation(response);
        if (!request.getGrantId().equals(receipt.getGrantId()) || !request.getMetric().equals(receipt.getMetric())
                || request.getQuantity() != receipt.getQuantity() || !kind.equals(receipt.getKind())
                || !request.getAction().equals(receipt.getAction())
                || request.getExpectedGrantRevision() != receipt.getGrantRevision()
                || !request.getBusinessOperationId().equals(receipt.getBusinessOperationId())) {
            throw new ProtocolViolationException("额度回执与请求上下文不一致");
        }
        return receipt;
    }

    /** 续期回执必须仍是同一有效并发占用，围栏不回退；无截止延长时允许服务端原值回读。 */
    public static ClientReservation renewedReservation(Map<String, Object> response, String reservationId,
                                                      long expectedFence) {
        ClientReservation receipt = reservation(response);
        if (!receipt.getReservationId().equals(reservationId) || !"CONCURRENT".equals(receipt.getKind())
                || !receipt.isActive() || receipt.getFence() < expectedFence) {
            throw new ProtocolViolationException("续期回执编号、种类、状态或围栏与请求不一致");
        }
        return receipt;
    }

    /** 候选页须与请求页码、大小和总量相符；元数据丢失不能当成第一页完整清单。 */
    public static GrantSelectionPage grantSelection(Map<String, Object> response, GrantSelectionRequest request) {
        Set<String> fields = new HashSet<String>(Arrays.asList("items", "total", "page", "size", "totalPages"));
        if (response == null || !fields.equals(response.keySet()) || !(response.get("items") instanceof List)) {
            throw new ProtocolViolationException("候选分页响应字段缺失或未知");
        }
        long total = number(response, "total");
        long page = number(response, "page");
        long size = number(response, "size");
        long totalPages = number(response, "totalPages");
        if (page != request.getPage() || size != request.getSize()
                || totalPages != (total + size - 1L) / size) {
            throw new ProtocolViolationException("候选分页响应与请求或总量不一致");
        }
        List<GrantCandidate> items = new ArrayList<GrantCandidate>();
        Set<String> ids = new HashSet<String>();
        for (Object item : (List<?>) response.get("items")) {
            if (!(item instanceof Map)) {
                throw new ProtocolViolationException("候选响应列表元素类型无效");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) item;
            if (!new HashSet<String>(Arrays.asList("grantId", "mode", "modelVersion", "commercialPhase"))
                    .equals(value.keySet())) {
                throw new ProtocolViolationException("候选响应字段缺失或未知");
            }
            String id = text(value, "grantId");
            String mode = text(value, "mode");
            String phase = text(value, "commercialPhase");
            long version = number(value, "modelVersion");
            if (!ids.add(id) || !("ONLINE".equals(mode) || "OFFLINE".equals(mode) || "HYBRID".equals(mode))
                    || !("ACTIVE".equals(phase) || "COMMERCIAL_GRACE".equals(phase))
                    || version < 1 || version > Integer.MAX_VALUE) {
                throw new ProtocolViolationException("候选响应编号、模式、阶段或模型版本无效");
            }
            items.add(new GrantCandidate(id, mode, version, phase));
        }
        long remaining = Math.max(0L, total - (page - 1L) * size);
        if (items.size() != Math.min(size, remaining)) {
            throw new ProtocolViolationException("候选分页响应条目数与总量不一致");
        }
        return new GrantSelectionPage(items, total, (int) page, (int) size, totalPages);
    }

    /** 旧人员/服务入口穷尽全部页，分页期间总量变化或编号重复则整份拒绝。 */
    public static List<GrantCandidate> allSelections(GrantSelectionRequest first,
                                                     Function<GrantSelectionRequest, GrantSelectionPage> loader) {
        List<GrantCandidate> result = new ArrayList<GrantCandidate>();
        Set<String> ids = new HashSet<String>();
        GrantSelectionPage response = loader.apply(first);
        long total = response.getTotal();
        long totalPages = response.getTotalPages();
        // 便捷返回类型是 Java List，超出其索引范围应使用显式分页，不能静默截断。
        if (total > Integer.MAX_VALUE || totalPages > Integer.MAX_VALUE) {
            throw new ProtocolViolationException("候选总量超出便捷列表范围，请使用显式分页");
        }
        for (long page = 1; page <= Math.max(1, totalPages); page++) {
            if (page > 1) {
                response = loader.apply(first.withPage((int) page));
            }
            if (response.getTotal() != total || response.getTotalPages() != totalPages) {
                throw new ProtocolViolationException("候选分页期间总量发生变化，请重试发现");
            }
            for (GrantCandidate item : response.getItems()) {
                if (!ids.add(item.getGrantId())) {
                    throw new ProtocolViolationException("候选分页出现重复编号");
                }
                result.add(item);
            }
        }
        if (result.size() != total) {
            throw new ProtocolViolationException("候选分页结果未完整获取");
        }
        return result;
    }

    /** 先核对响应信任域再构造快照；签名、公钥批准与原子安装仍由安装器负责。 */
    public static TrustMaterialSnapshot trustMaterial(Map<String, Object> response,
                                                     String issuerId, String productId) {
        if (response == null || !TRUST_MATERIAL_FIELDS.equals(response.keySet())) {
            throw new ProtocolViolationException("信任资料响应字段缺失或未知");
        }
        if (!text(response, "issuerId").equals(issuerId)
                || !text(response, "productId").equals(productId)) {
            throw new ProtocolViolationException("信任资料响应与请求范围不一致");
        }
        long sequence = number(response, "sequence");
        long issuedAt = number(response, "issuedAt");
        if (sequence < 1 || issuedAt > ClientProtocolConstant.EPOCH_SECONDS_MAX) {
            throw new ProtocolViolationException("信任资料代次或签发时间无效");
        }
        return new TrustMaterialSnapshot(sequence, issuedAt, text(response, "snapshotId"),
                text(response, "manifestJws"),
                stringList(response, "revocationPartJws", 1, ClientProtocolConstant.TRUST_MATERIAL_PARTS_MAX),
                stringList(response, "trustedPublicKeysJson", 0, Integer.MAX_VALUE));
    }

    /** 围栏与已知代次为非负安全整数，调用方错误在外部请求前拒绝。 */
    public static void requireSafeSequence(long value) {
        if (value < 0 || value > ClientProtocolConstant.SAFE_INTEGER_MAX) {
            throw new IllegalArgumentException("围栏或已知代次超出安全整数范围");
        }
    }

    private static List<String> stringList(Map<String, Object> response, String field,
                                           int minimumSize, int maximumSize) {
        Object value = response == null ? null : response.get(field);
        if (!(value instanceof List)) {
            throw new ProtocolViolationException("响应列表字段缺失：" + field);
        }
        List<?> values = (List<?>) value;
        if (values.size() < minimumSize || values.size() > maximumSize) {
            throw new ProtocolViolationException("响应列表字段数量无效：" + field);
        }
        List<String> result = new ArrayList<String>();
        for (Object item : values) {
            if (!(item instanceof String) || ((String) item).isEmpty()) {
                throw new ProtocolViolationException("响应列表字段含无效文本元素：" + field);
            }
            result.add((String) item);
        }
        return result;
    }
}
