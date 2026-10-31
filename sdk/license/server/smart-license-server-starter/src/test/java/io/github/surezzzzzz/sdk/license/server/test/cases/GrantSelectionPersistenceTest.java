package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 候选查询的产品、对象、状态、DATA 交集与主键续扫，不依赖 Controller mock 证明 SQL。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GrantSelectionPersistenceTest {

    @Autowired
    private GrantRepository grants;

    @Test
    void sqlIntersectionAndKeysetExcludeForeignAndDisabledRows() {
        String prefix = "selection-" + UUID.randomUUID();
        String productId = prefix + "-product";
        List<GrantRevisionEntity> rows = new ArrayList<>();
        rows.add(row(prefix + "-01", productId, "PERSON", "ENABLED"));
        rows.add(row(prefix + "-02", productId, "PERSON", "ENABLED"));
        rows.add(row(prefix + "-03", productId, "SERVICE", "ENABLED"));
        rows.add(row(prefix + "-04", prefix + "-foreign", "PERSON", "ENABLED"));
        rows.add(row(prefix + "-05", productId, "PERSON", "SUSPENDED"));
        try {
            grants.saveAll(rows);
            grants.flush();
            PageRequest one = PageRequest.of(0, 1, Sort.by("grantId"));
            Page<GrantRevisionEntity> first = grants.findSelectionBatch(productId, "PERSON", null, null, one);
            assertThat(first.getContent()).extracting(GrantRevisionEntity::getGrantId).containsExactly(prefix + "-01");
            assertThat(first.getTotalElements()).isEqualTo(2L);
            Page<GrantRevisionEntity> next = grants.findSelectionBatch(productId, "PERSON", null, prefix + "-01", one);
            assertThat(next.getContent()).extracting(GrantRevisionEntity::getGrantId).containsExactly(prefix + "-02");
            assertThat(grants.findSelectionBatch(productId, "PERSON", Collections.singleton(prefix + "-02"), null, one)
                    .getContent()).extracting(GrantRevisionEntity::getGrantId).containsExactly(prefix + "-02");
            assertThat(grants.findSelectionBatch(productId, "PERSON", Collections.emptySet(), null, one).getContent()).isEmpty();
            assertThat(grants.findSelectionBatch(productId, "PERSON", Collections.singleton(prefix + "-04"), null, one).getContent()).isEmpty();
            assertThat(grants.findVisiblePage("customer-test", productId,
                    Collections.singleton(prefix + "-01"), one).getContent())
                    .extracting(GrantRevisionEntity::getGrantId).containsExactly(prefix + "-01");
            assertThat(grants.findVisiblePage("foreign-customer", productId,
                    Collections.singleton(prefix + "-01"), one).getContent()).isEmpty();
            assertThat(grants.findVisiblePage("customer-test", productId,
                    Collections.singleton(prefix + "-04"), one).getContent()).isEmpty();
            log.info("真实候选SQL已求交产品、对象、状态、受限DATA，并按主键续扫");
        } finally {
            for (GrantRevisionEntity row : rows) {
                if (grants.existsById(row.getGrantId())) {
                    grants.deleteById(row.getGrantId());
                }
            }
        }
    }

    private GrantRevisionEntity row(String id, String productId, String kind, String state) {
        GrantRevisionEntity row = new GrantRevisionEntity(id, "customer-test", productId, kind,
                "ASSIGNED_SUBJECTS", null, "ONLINE", null);
        row.setState(state);
        return row;
    }
}
