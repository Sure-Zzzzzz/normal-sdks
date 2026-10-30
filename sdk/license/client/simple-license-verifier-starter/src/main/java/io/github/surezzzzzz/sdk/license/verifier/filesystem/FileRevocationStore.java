package io.github.surezzzzzz.sdk.license.verifier.filesystem;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationEntry;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationPackageCodec;
import io.github.surezzzzzz.sdk.license.core.verify.AtomicRevocationStore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 吊销快照文件仓：跨进程锁、临时文件原子替换、序号只增和条目只增。
 *
 * <p>查询和安装均重新读取最新完整文件，已有实例可见其他实例提交；候选状态落盘后才提交缓存。
 * 文件损坏或已观察水位回退按失败关闭处理，不恢复为空仓。
 * 文件只记录目标类型/编号和必要水位，不写入签名文本、访问令牌或业务理由。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class FileRevocationStore implements AtomicRevocationStore {

    private static final String VERSION = "license-revocation-store-v1";
    private static final String DIGEST_SUFFIX = ".snapshotDigest";
    private static final String STATE_ROLLBACK = "吊销仓已提交状态回退";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HASH_UNAVAILABLE = "JRE 缺少 SHA-256";
    private static final String ENTRIES_ROLLBACK = "吊销包累计条目回退";
    private static final String FIELD_CORRUPT = "吊销仓字段损坏";
    private static final String INDEX_CORRUPT = "吊销仓索引损坏";
    private static final String SCOPE_PREFIX = "scope.";
    private static final String SEQUENCE_SUFFIX = ".sequence";
    private static final String ISSUED_AT_SUFFIX = ".issuedAt";
    private static final String SNAPSHOT_SUFFIX = ".snapshotId";
    private static final String EMPTY_PACKAGE = "吊销包不得为空";
    private static final String SEQUENCE_ROLLBACK = "吊销包序号回退";
    private static final String SNAPSHOT_CONFLICT = "同序号吊销包快照不一致";
    private static final String INSTALL_FAILED = "吊销仓安装失败";
    private static final String FORMAT_UNSUPPORTED = "吊销仓格式不受支持";
    private static final String SCOPE_MISSING = "吊销仓范围元数据缺失";
    private static final String ENTRY_CORRUPT = "吊销仓条目损坏";
    private static final String SCOPED_ENTRY_CORRUPT = "吊销仓作用域条目损坏";
    private static final String READ_FAILED = "吊销仓读取失败";
    private static final String ATOMIC_MOVE_UNSUPPORTED = "吊销仓不支持原子替换";
    private static final String EMPTY_SCOPE = "吊销范围不得为空";
    private static final String NUMBER_CORRUPT = "吊销仓数值损坏";
    private static final String INVALID_KEY = "invalid";
    // JVM 文件锁不会等待同进程的重叠锁；安装先在进程内串行，跨进程仍使用固定锁文件。
    private static final Object INSTALL_MONITOR = new Object();
    private final Path stateFile;
    private final Path lockFile;
    private final Map<String, String> entries = new HashMap<String, String>();
    private final Map<String, String> scopedEntries = new HashMap<String, String>();
    private final Map<String, ScopeState> scopes = new HashMap<String, ScopeState>();
    private boolean stateObserved;

    /** 读取已有快照；首次尚未安装允许空仓，已有损坏文件拒绝启动。 */
    public FileRevocationStore(Path stateDirectory) {
        this.stateFile = stateDirectory.resolve("revocations.properties");
        this.lockFile = stateDirectory.resolve("revocations.lock");
        load();
    }

    /** 从最新已提交文件查询吊销，不依赖本实例的历史缓存。 */
    @Override
    public synchronized boolean isRevoked(String targetKind, String targetId) {
        load();
        return entries.containsKey(entryKey(targetKind, targetId));
    }

    /** 从最新已提交文件查询指定签发方和产品范围的吊销。 */
    @Override
    public synchronized boolean isRevoked(String issuerId, String productId, String targetKind, String targetId) {
        load();
        return scopedEntries.containsKey(scopedEntryKey(issuerId, productId, targetKind, targetId));
    }

    /** 返回最新已提交水位，包含其他实例完成的安装。 */
    @Override
    public synchronized OptionalLong installedSequence(String issuerId, String productId) {
        load();
        ScopeState state = scopes.get(scopeKey(issuerId, productId));
        return state == null ? OptionalLong.empty() : OptionalLong.of(state.sequence);
    }

    /** 返回最新快照时间，失败安装不提前更新新鲜度。 */
    @Override
    public synchronized Optional<Long> installedIssuedAt(String issuerId, String productId) {
        load();
        ScopeState state = scopes.get(scopeKey(issuerId, productId));
        return state == null ? Optional.<Long>empty() : Optional.of(state.issuedAt);
    }

    /** 原子安装完整验证批次；同序号仅允许时间与条目完全一致的同一快照重放。 */
    @Override
    public synchronized void install(RevocationPackageCodec.VerifiedPackage verifiedPackage) {
        if (verifiedPackage == null) {
            throw new IllegalArgumentException(EMPTY_PACKAGE);
        }
        synchronized (INSTALL_MONITOR) {
            try {
                Files.createDirectories(stateFile.getParent());
                try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
                    load();
                    String scope = scopeKey(verifiedPackage.getIssuerId(), verifiedPackage.getProductId());
                    ScopeState current = scopes.get(scope);
                    Map<String, String> incomingEntries = snapshotEntries(verifiedPackage);
                    String incomingDigest = digest(incomingEntries);
                    log.debug("吊销仓安装开始：sequence={}, entryCount={}",
                            verifiedPackage.getSequence(), verifiedPackage.getEntries().size());
                    if (current != null && verifiedPackage.getSequence() < current.sequence) {
                        log.debug("吊销仓拒绝安装：reason=SEQUENCE_ROLLBACK，incoming={}, installed={}",
                                verifiedPackage.getSequence(), current.sequence);
                        throw new IllegalArgumentException(SEQUENCE_ROLLBACK);
                    }
                    // 输入必须是完整累计快照。漏掉或改写已知条目不能靠本地合并补齐后刷新水位。
                    Map<String, String> knownEntries = scopeEntries(scope);
                    if (!incomingEntries.entrySet().containsAll(knownEntries.entrySet())) {
                        log.debug("吊销仓拒绝安装：reason=ENTRIES_ROLLBACK，sequence={}",
                                verifiedPackage.getSequence());
                        throw new IllegalArgumentException(ENTRIES_ROLLBACK);
                    }
                    if (current != null && verifiedPackage.getSequence() == current.sequence) {
                        // 旧文件未存摘要时，核对范围内累计条目；同号快照不能追加或改写内容。
                        String currentDigest = current.snapshotDigest == null
                                ? digest(knownEntries) : current.snapshotDigest;
                        if (!verifiedPackage.getSnapshotId().equals(current.snapshotId)
                                || verifiedPackage.getIssuedAt() != current.issuedAt
                                || !incomingDigest.equals(currentDigest)) {
                            log.debug("吊销仓拒绝安装：reason=SNAPSHOT_CONFLICT，sequence={}", current.sequence);
                            throw new IllegalArgumentException(SNAPSHOT_CONFLICT);
                        }
                        log.debug("吊销仓幂等回读：sequence={}", current.sequence);
                        return;
                    }
                    Map<String, ScopeState> candidateScopes = new HashMap<String, ScopeState>(scopes);
                    Map<String, String> candidateEntries = new HashMap<String, String>(entries);
                    Map<String, String> candidateScopedEntries = new HashMap<String, String>(scopedEntries);
                    candidateScopes.put(scope, new ScopeState(verifiedPackage.getSequence(),
                            verifiedPackage.getIssuedAt(), verifiedPackage.getSnapshotId(), incomingDigest));
                    for (RevocationEntry entry : verifiedPackage.getEntries()) {
                        String revokedAt = String.valueOf(entry.getRevokedAt());
                        candidateEntries.putIfAbsent(entryKey(entry.getTargetKind().getCode(), entry.getTargetId()),
                                revokedAt);
                        candidateScopedEntries.putIfAbsent(scopedEntryKey(verifiedPackage.getIssuerId(),
                                verifiedPackage.getProductId(), entry.getTargetKind().getCode(), entry.getTargetId()), revokedAt);
                    }
                    flush(candidateScopes, candidateEntries, candidateScopedEntries);
                    replaceCache(candidateScopes, candidateEntries, candidateScopedEntries);
                    stateObserved = true;
                    log.debug("吊销仓安装已提交：sequence={}", verifiedPackage.getSequence());
                }
            } catch (IOException failure) {
                log.debug("吊销仓安装未提交：failureType={}", failure.getClass().getSimpleName());
                throw new IllegalStateException(INSTALL_FAILED, failure);
            }
        }
    }

    private void load() {
        if (!Files.exists(stateFile)) {
            if (stateObserved) {
                throw new IllegalStateException(STATE_ROLLBACK);
            }
            return;
        }
        Map<String, ScopeState> loadedScopes = new HashMap<String, ScopeState>();
        Map<String, String> loadedEntries = new HashMap<String, String>();
        Map<String, String> loadedScopedEntries = new HashMap<String, String>();
        try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            if (!VERSION.equals(properties.getProperty("format"))) {
                throw new IllegalStateException(FORMAT_UNSUPPORTED);
            }
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("scope.") && key.endsWith(".sequence")) {
                    String scope = key.substring("scope.".length(), key.length() - ".sequence".length());
                    validateIndex(scope, 2, false);
                    long sequence = parseNonNegative(properties.getProperty(key));
                    if (sequence == 0) {
                        throw new IllegalStateException(FIELD_CORRUPT);
                    }
                    long issuedAt = parseNonNegative(properties.getProperty("scope." + scope + ".issuedAt"));
                    String snapshot = properties.getProperty("scope." + scope + ".snapshotId");
                    if (snapshot == null || snapshot.isEmpty()) {
                        throw new IllegalStateException(SCOPE_MISSING);
                    }
                    loadedScopes.put(scope, new ScopeState(sequence, issuedAt, snapshot,
                            properties.getProperty("scope." + scope + DIGEST_SUFFIX)));
                } else if (key.startsWith("entry.")) {
                    String encoded = key.substring("entry.".length());
                    validateIndex(encoded, 2, true);
                    if (encoded.isEmpty() || properties.getProperty(key) == null) {
                        throw new IllegalStateException(ENTRY_CORRUPT);
                    }
                    parseNonNegative(properties.getProperty(key));
                    loadedEntries.put(encoded, properties.getProperty(key));
                } else if (key.startsWith("scoped.")) {
                    String encoded = key.substring("scoped.".length());
                    validateIndex(encoded, 4, true);
                    if (encoded.isEmpty() || properties.getProperty(key) == null) {
                        throw new IllegalStateException(SCOPED_ENTRY_CORRUPT);
                    }
                    parseNonNegative(properties.getProperty(key));
                    loadedScopedEntries.put(encoded, properties.getProperty(key));
                } else if (!key.startsWith(SCOPE_PREFIX) && !"format".equals(key)) {
                    throw new IllegalStateException(FIELD_CORRUPT);
                }
            }
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(SCOPE_PREFIX)) {
                    String scope = scopeFromMetadataKey(key);
                    if (!loadedScopes.containsKey(scope)) {
                        throw new IllegalStateException(FIELD_CORRUPT);
                    }
                }
            }
            for (String scoped : loadedScopedEntries.keySet()) {
                String[] components = decodeComponents(scoped);
                if (!loadedScopes.containsKey(scopeKey(components[0], components[1]))
                        || !loadedEntries.containsKey(entryKey(components[2], components[3]))) {
                    throw new IllegalStateException(INDEX_CORRUPT);
                }
            }
            // 新文件摘要绑定该范围完整累计条目；不能让删掉一行的残缺快照在重启后重新变得有效。
            for (Map.Entry<String, ScopeState> scope : loadedScopes.entrySet()) {
                if (scope.getValue().snapshotDigest != null
                        && !scope.getValue().snapshotDigest.equals(digest(scopeEntries(loadedScopedEntries,
                        scope.getKey())))) {
                    throw new IllegalStateException(FIELD_CORRUPT);
                }
            }
        } catch (IOException failure) {
            log.debug("吊销仓读取失败：failureType={}", failure.getClass().getSimpleName());
            throw new IllegalStateException(READ_FAILED, failure);
        }
        for (Map.Entry<String, ScopeState> item : scopes.entrySet()) {
            ScopeState next = loadedScopes.get(item.getKey());
            ScopeState previous = item.getValue();
            if (next == null || next.sequence < previous.sequence
                    || (next.sequence == previous.sequence && (!next.snapshotId.equals(previous.snapshotId)
                    || next.issuedAt != previous.issuedAt
                    || (previous.snapshotDigest != null
                    && !previous.snapshotDigest.equals(next.snapshotDigest))))) {
                throw new IllegalStateException(STATE_ROLLBACK);
            }
        }
        if (!loadedEntries.entrySet().containsAll(entries.entrySet())
                || !loadedScopedEntries.entrySet().containsAll(scopedEntries.entrySet())) {
            throw new IllegalStateException(STATE_ROLLBACK);
        }
        replaceCache(loadedScopes, loadedEntries, loadedScopedEntries);
        stateObserved = true;
    }

    private static String scopeFromMetadataKey(String key) {
        for (String suffix : new String[]{SEQUENCE_SUFFIX, ISSUED_AT_SUFFIX, SNAPSHOT_SUFFIX, DIGEST_SUFFIX}) {
            if (key.endsWith(suffix)) {
                return key.substring(SCOPE_PREFIX.length(), key.length() - suffix.length());
            }
        }
        throw new IllegalStateException(FIELD_CORRUPT);
    }

    private static void validateIndex(String encoded, int componentCount, boolean targetIndex) {
        try {
            String[] components = decodeComponents(encoded);
            if (components.length != componentCount) {
                throw new IllegalArgumentException();
            }
            for (String component : components) {
                if (component.isEmpty()) {
                    throw new IllegalArgumentException();
                }
            }
            if (targetIndex) {
                RevocationEntry.TargetKind.valueOf(components[componentCount - 2]);
            }
        } catch (RuntimeException corrupt) {
            throw new IllegalStateException(INDEX_CORRUPT, corrupt);
        }
    }

    private static String[] decodeComponents(String encoded) {
        byte[] bytes = Base64Url.decode(encoded);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        if (!Base64Url.encode(decoded.getBytes(StandardCharsets.UTF_8)).equals(encoded)) {
            throw new IllegalArgumentException(INDEX_CORRUPT);
        }
        return decoded.split("\u0000", -1);
    }

    private void replaceCache(Map<String, ScopeState> newScopes, Map<String, String> newEntries,
                              Map<String, String> newScopedEntries) {
        scopes.clear();
        scopes.putAll(newScopes);
        entries.clear();
        entries.putAll(newEntries);
        scopedEntries.clear();
        scopedEntries.putAll(newScopedEntries);
    }

    private void flush(Map<String, ScopeState> candidateScopes, Map<String, String> candidateEntries,
                       Map<String, String> candidateScopedEntries) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", VERSION);
        for (Map.Entry<String, ScopeState> item : candidateScopes.entrySet()) {
            String prefix = "scope." + item.getKey();
            properties.setProperty(prefix + ".sequence", String.valueOf(item.getValue().sequence));
            properties.setProperty(prefix + ".issuedAt", String.valueOf(item.getValue().issuedAt));
            properties.setProperty(prefix + ".snapshotId", item.getValue().snapshotId);
            if (item.getValue().snapshotDigest != null) {
                properties.setProperty(prefix + DIGEST_SUFFIX, item.getValue().snapshotDigest);
            }
        }
        for (Map.Entry<String, String> item : candidateEntries.entrySet()) {
            properties.setProperty("entry." + item.getKey(), item.getValue());
        }
        for (Map.Entry<String, String> item : candidateScopedEntries.entrySet()) {
            properties.setProperty("scoped." + item.getKey(), item.getValue());
        }
        Path temp = Files.createTempFile(stateFile.getParent(), "revocations", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "license verifier revocation store");
            }
            try {
                Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                throw new IllegalStateException(ATOMIC_MOVE_UNSUPPORTED, unsupported);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Map<String, String> snapshotEntries(RevocationPackageCodec.VerifiedPackage verified) {
        Map<String, String> result = new TreeMap<String, String>();
        for (RevocationEntry entry : verified.getEntries()) {
            result.put(entryKey(entry.getTargetKind().getCode(), entry.getTargetId()),
                    String.valueOf(entry.getRevokedAt()));
        }
        return result;
    }

    private Map<String, String> scopeEntries(String scope) {
        return scopeEntries(scopedEntries, scope);
    }

    private static Map<String, String> scopeEntries(Map<String, String> scoped, String scope) {
        Map<String, String> result = new TreeMap<String, String>();
        String prefix = new String(Base64Url.decode(scope), StandardCharsets.UTF_8) + '\u0000';
        for (Map.Entry<String, String> entry : scoped.entrySet()) {
            String decoded = new String(Base64Url.decode(entry.getKey()), StandardCharsets.UTF_8);
            if (decoded.startsWith(prefix)) {
                result.put(Base64Url.encode(decoded.substring(prefix.length()).getBytes(StandardCharsets.UTF_8)),
                        entry.getValue());
            }
        }
        return result;
    }

    private static String digest(Map<String, String> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return Base64Url.encode(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(HASH_UNAVAILABLE, impossible);
        }
    }

    private static String scopeKey(String issuerId, String productId) {
        if (issuerId == null || productId == null) {
            throw new IllegalArgumentException(EMPTY_SCOPE);
        }
        return Base64Url.encode((issuerId + "\u0000" + productId).getBytes(StandardCharsets.UTF_8));
    }

    private static String entryKey(String targetKind, String targetId) {
        if (targetKind == null || targetId == null) {
            return INVALID_KEY;
        }
        return Base64Url.encode((targetKind + "\u0000" + targetId).getBytes(StandardCharsets.UTF_8));
    }

    private static String scopedEntryKey(String issuerId, String productId, String targetKind, String targetId) {
        if (issuerId == null || productId == null || targetKind == null || targetId == null) {
            return INVALID_KEY;
        }
        return Base64Url.encode((issuerId + "\u0000" + productId + "\u0000"
                + targetKind + "\u0000" + targetId).getBytes(StandardCharsets.UTF_8));
    }

    private static long parseNonNegative(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (Exception failure) {
            throw new IllegalStateException(NUMBER_CORRUPT, failure);
        }
    }

    private static final class ScopeState {
        private final long sequence;
        private final long issuedAt;
        private final String snapshotId;
        private final String snapshotDigest;

        private ScopeState(long sequence, long issuedAt, String snapshotId, String snapshotDigest) {
            this.sequence = sequence;
            this.issuedAt = issuedAt;
            this.snapshotId = snapshotId;
            this.snapshotDigest = snapshotDigest;
        }
    }
}
