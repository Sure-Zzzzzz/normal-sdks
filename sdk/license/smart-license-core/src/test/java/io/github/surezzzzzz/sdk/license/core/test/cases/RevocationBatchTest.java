package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.codec.RevocationBatch;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationEntry;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 吊销批次结构断言：分片/条目预算、条目去重、序号比较。
 *
 * @author surezzzzzz
 */
class RevocationBatchTest {

    @Test
    void validBatchWithEmptySinglePartBuilds() {
        List<List<RevocationEntry>> parts = new ArrayList<List<RevocationEntry>>();
        parts.add(new ArrayList<RevocationEntry>());
        assertThatCode(() -> RevocationBatch.of("i", "p", 1L, 1780000000L, "snap-1", parts))
                .doesNotThrowAnyException();
    }

    @Test
    void duplicateEntriesRejected() {
        List<List<RevocationEntry>> parts = new ArrayList<List<RevocationEntry>>();
        parts.add(Arrays.asList(
                RevocationEntry.of(RevocationEntry.TargetKind.CREDENTIAL, "c-1", 1780000000L),
                RevocationEntry.of(RevocationEntry.TargetKind.CREDENTIAL, "c-1", 1780000001L)));
        assertThatThrownBy(() -> RevocationBatch.of("i", "p", 1L, 1780000000L, "snap-1", parts))
                .isInstanceOf(LicenseCoreException.class);
        // 不同类型同编号不冲突
        List<List<RevocationEntry>> mixed = new ArrayList<List<RevocationEntry>>();
        mixed.add(Arrays.asList(
                RevocationEntry.of(RevocationEntry.TargetKind.CREDENTIAL, "x", 1780000000L),
                RevocationEntry.of(RevocationEntry.TargetKind.GRANT, "x", 1780000000L)));
        assertThatCode(() -> RevocationBatch.of("i", "p", 1L, 1780000000L, "snap-1", mixed))
                .doesNotThrowAnyException();
    }

    @Test
    void partCountAndEntryLimitsEnforced() {
        List<List<RevocationEntry>> tooManyParts = new ArrayList<List<RevocationEntry>>();
        for (int i = 0; i < 257; i++) {
            tooManyParts.add(Arrays.asList(RevocationEntry.of(
                    RevocationEntry.TargetKind.GRANT, "g-" + i, 1780000000L)));
        }
        assertThatThrownBy(() -> RevocationBatch.of("i", "p", 1L, 1780000000L, "s", tooManyParts))
                .isInstanceOf(LicenseCoreException.class);

        List<RevocationEntry> overEntries = new ArrayList<RevocationEntry>();
        for (int i = 0; i < 4097; i++) {
            overEntries.add(RevocationEntry.of(RevocationEntry.TargetKind.CREDENTIAL, "c-" + i, 1780000000L));
        }
        assertThatThrownBy(() -> RevocationBatch.of("i", "p", 1L, 1780000000L, "s",
                Arrays.<List<RevocationEntry>>asList(overEntries)))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void sequenceComparisonForReplacement() {
        List<List<RevocationEntry>> parts = new ArrayList<List<RevocationEntry>>();
        parts.add(new ArrayList<RevocationEntry>());
        RevocationBatch batch = RevocationBatch.of("i", "p", 5L, 1780000000L, "snap", parts);
        assertThat(batch.supersedes(4L)).isTrue();
        assertThat(batch.supersedes(5L)).isFalse();
        assertThat(batch.supersedes(6L)).isFalse();
    }

    @Test
    void partsDefensivelyCopied() {
        List<List<RevocationEntry>> parts = new ArrayList<List<RevocationEntry>>();
        List<RevocationEntry> part = new ArrayList<RevocationEntry>();
        part.add(RevocationEntry.of(RevocationEntry.TargetKind.GRANT, "g", 1780000000L));
        parts.add(part);
        RevocationBatch batch = RevocationBatch.of("i", "p", 1L, 1780000000L, "snap", parts);
        part.add(RevocationEntry.of(RevocationEntry.TargetKind.GRANT, "g2", 1780000000L));
        assertThat(batch.getParts().get(0)).hasSize(1);
    }
}
