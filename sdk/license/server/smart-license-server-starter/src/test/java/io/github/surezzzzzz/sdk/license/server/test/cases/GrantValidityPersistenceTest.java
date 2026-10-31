package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 MySQL 期限回读：新建默认值随行落库，JPA 不会为旧空列生成新截止。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GrantValidityPersistenceTest {

    @Autowired
    private GrantRepository grants;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void defaultWindowPersistsAndReadbackDoesNotRecreateIt() {
        String id = "validity-" + UUID.randomUUID();
        GrantRevisionEntity created = new GrantRevisionEntity(id, "customer-1", "product-1",
                "PERSON", "CUSTOMER_MEMBERS", null, "ONLINE", null);
        try {
            grants.saveAndFlush(created);
            GrantRevisionEntity loaded = grants.findById(id).get();
            assertThat(loaded.getStartsAt()).isEqualTo(created.getStartsAt());
            assertThat(loaded.getServiceUntil()).isEqualTo(created.getServiceUntil());
            assertThat(loaded.getGraceUntil()).isEqualTo(created.getGraceUntil());
            assertThat(GrantValidityHelper.fromGrant(loaded).getKind()).isEqualTo(ValidityPolicy.Kind.FIXED);
        } finally {
            grants.deleteById(id);
        }
    }

    @Test
    void legacyNullColumnsRemainNullAndFailClosedAfterJpaHydration() {
        String id = "validity-legacy-" + UUID.randomUUID();
        GrantRevisionEntity created = new GrantRevisionEntity(id, "customer-1", "product-1",
                "PERSON", "CUSTOMER_MEMBERS", null, "ONLINE", null);
        try {
            grants.saveAndFlush(created);
            jdbc.update("UPDATE license_grant SET validity_kind=NULL, starts_at=NULL, "
                    + "service_until=NULL, grace_until=NULL WHERE grant_id=?", id);
            GrantRevisionEntity loaded = grants.findById(id).get();
            assertThat(loaded.getStartsAt()).isNull();
            assertThat(loaded.getServiceUntil()).isNull();
            assertThat(loaded.getGraceUntil()).isNull();
            assertThatThrownBy(() -> GrantValidityHelper.fromGrant(loaded))
                    .isInstanceOf(LicenseHttpException.class)
                    .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        } finally {
            grants.deleteById(id);
        }
    }
}
