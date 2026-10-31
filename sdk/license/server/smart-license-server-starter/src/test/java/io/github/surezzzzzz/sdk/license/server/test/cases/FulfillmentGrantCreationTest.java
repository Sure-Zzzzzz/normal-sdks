package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.constant.FulfillmentConstant;
import io.github.surezzzzzz.sdk.license.server.entity.FulfillmentGrantEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.integration.FulfillmentGrantCreationService;
import io.github.surezzzzzz.sdk.license.server.repository.FulfillmentGrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 履约关联单测：不可改绑、歧义拒绝、断裂拒绝和事务退出后恢复赢家。
 * 真实提交、跨线程唯一约束与草稿回滚由 MySQL 集成用例验证。
 *
 * @author surezzzzzz
 */
@Slf4j
class FulfillmentGrantCreationTest {

    private GrantRepository grants;
    private FulfillmentGrantRepository associations;
    private PlatformTransactionManager transactions;
    private FulfillmentGrantCreationService service;

    @BeforeEach
    void setUp() {
        grants = Mockito.mock(GrantRepository.class);
        associations = Mockito.mock(FulfillmentGrantRepository.class);
        transactions = Mockito.mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(associations.findById("ff-1")).thenReturn(Optional.empty());
        when(grants.findByBusinessOrderRef("ff-1")).thenReturn(Collections.emptyList());
        when(grants.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new FulfillmentGrantCreationService(grants, associations, transactions);
    }

    @Test
    void createsDraftAndAssociationInOneTransaction() {
        FulfillmentGrantCreationService.CreationResult result = service.createOrRecover("ff-1", "cu-1", "prod-1");
        log.info("履约草稿建立：created={}，state={}", result.isCreated(), result.getGrant().getState());
        assertThat(result.isCreated()).isTrue();
        assertThat(result.getGrant().getState()).isEqualTo("DRAFT");
        ArgumentCaptor<FulfillmentGrantEntity> association = ArgumentCaptor.forClass(FulfillmentGrantEntity.class);
        InOrder calls = Mockito.inOrder(transactions, grants, associations);
        calls.verify(transactions).getTransaction(any());
        calls.verify(associations).findById("ff-1");
        calls.verify(grants).findByBusinessOrderRef("ff-1");
        calls.verify(grants).save(any());
        calls.verify(associations).saveAndFlush(association.capture());
        calls.verify(transactions).commit(any());
        assertThat(association.getValue().getGrantId()).isEqualTo(result.getGrant().getGrantId());
        assertThat(association.getValue().getRowVersion()).isNull();
    }

    @Test
    void existingAssociationWinsWithoutRebuildingOrLegacyLookup() {
        GrantRevisionEntity winner = grant("gr-1", "ff-1");
        when(associations.findById("ff-1")).thenReturn(Optional.of(new FulfillmentGrantEntity("ff-1", "gr-1")));
        when(grants.findById("gr-1")).thenReturn(Optional.of(winner));
        FulfillmentGrantCreationService.CreationResult result = service.createOrRecover("ff-1", "cu-1", "prod-1");
        log.info("履约重放复用：grantId={}", result.getGrant().getGrantId());
        assertThat(result.isCreated()).isFalse();
        assertThat(result.getGrant()).isSameAs(winner);
        verify(grants, never()).save(any());
        verify(grants, never()).findByBusinessOrderRef(any());
        verify(associations, never()).saveAndFlush(any());
    }

    @Test
    void uniqueLegacyGrantIsAdoptedWithoutCreatingAnotherDraft() {
        GrantRevisionEntity legacy = grant("gr-legacy", "ff-1");
        when(grants.findByBusinessOrderRef("ff-1")).thenReturn(Collections.singletonList(legacy));
        Optional<GrantRevisionEntity> result = service.findExisting("ff-1");
        log.info("存量履约补台账：present={}", result.isPresent());
        assertThat(result).contains(legacy);
        verify(associations).saveAndFlush(any(FulfillmentGrantEntity.class));
        verify(grants, never()).save(any());
    }

    @Test
    void ambiguousLegacyReferencesNeverCreateAnAssociation() {
        when(grants.findByBusinessOrderRef("ff-1"))
                .thenReturn(Arrays.asList(grant("gr-a", "ff-1"), grant("gr-b", "ff-1")));
        log.info("多授权履约关联应停止处理");
        assertThatThrownBy(() -> service.createOrRecover("ff-1", "cu-1", "prod-1"))
                .hasMessage(FulfillmentConstant.AMBIGUOUS_GRANT);
        verify(grants, never()).save(any());
        verify(associations, never()).saveAndFlush(any());
        verify(transactions).rollback(any());
    }

    @Test
    void missingAssociatedGrantNeverRegenerates() {
        when(associations.findById("ff-1")).thenReturn(Optional.of(new FulfillmentGrantEntity("ff-1", "gr-gone")));
        when(grants.findById("gr-gone")).thenReturn(Optional.empty());
        log.info("关联目标缺失应拒绝换发");
        assertThatThrownBy(() -> service.createOrRecover("ff-1", "cu-1", "prod-1"))
                .hasMessage(FulfillmentConstant.BROKEN_ASSOCIATION);
        verify(grants, never()).save(any());
    }

    @Test
    void mismatchedBusinessReferenceNeverRegenerates() {
        when(associations.findById("ff-1")).thenReturn(Optional.of(new FulfillmentGrantEntity("ff-1", "gr-1")));
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant("gr-1", "ff-other")));
        log.info("关联业务引用不符应拒绝恢复");
        assertThatThrownBy(() -> service.findExisting("ff-1"))
                .hasMessage(FulfillmentConstant.BROKEN_ASSOCIATION);
        verify(grants, never()).save(any());
    }

    @Test
    void uniqueConflictRollsBackBeforeReadingWinnerInNewTransaction() {
        GrantRevisionEntity winner = grant("gr-winner", "ff-1");
        when(associations.findById("ff-1")).thenReturn(Optional.empty(),
                Optional.of(new FulfillmentGrantEntity("ff-1", "gr-winner")));
        when(associations.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate fulfillment key"));
        when(grants.findById("gr-winner")).thenReturn(Optional.of(winner));
        FulfillmentGrantCreationService.CreationResult result = service.createOrRecover("ff-1", "cu-1", "prod-1");
        log.info("唯一冲突后恢复：created={}，grantId={}", result.isCreated(), result.getGrant().getGrantId());
        assertThat(result.isCreated()).isFalse();
        assertThat(result.getGrant()).isSameAs(winner);
        InOrder calls = Mockito.inOrder(transactions, associations);
        calls.verify(transactions).getTransaction(any());
        calls.verify(associations).findById("ff-1");
        calls.verify(associations).saveAndFlush(any());
        calls.verify(transactions).rollback(any());
        calls.verify(transactions).getTransaction(any());
        calls.verify(associations).findById("ff-1");
        calls.verify(transactions).commit(any());
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, Mockito.times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(definition ->
                assertThat(definition.getPropagationBehavior())
                        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void unrelatedConstraintFailureIsNotPretendedToBeSuccessfulReplay() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint");
        when(associations.saveAndFlush(any())).thenThrow(failure);
        log.info("无已提交赢家的约束错误必须透传");
        assertThatThrownBy(() -> service.createOrRecover("ff-1", "cu-1", "prod-1")).isSameAs(failure);
    }

    @Test
    void invalidReferencesDoNotReachPersistence() {
        log.info("空白或超长业务编号必须在持久化前拒绝");
        assertThatThrownBy(() -> service.findExisting(" ")).hasMessage(FulfillmentConstant.INVALID_REFERENCE);
        assertThatThrownBy(() -> service.createOrRecover("ff-1", null, "prod-1"))
                .hasMessage(FulfillmentConstant.INVALID_REFERENCE);
        String overlong = String.join("", Collections.nCopies(129, "x"));
        assertThatThrownBy(() -> service.createOrRecover(overlong, "cu-1", "prod-1"))
                .hasMessage(FulfillmentConstant.INVALID_REFERENCE);
        Mockito.verifyNoInteractions(grants, associations, transactions);
    }

    private GrantRevisionEntity grant(String grantId, String fulfillmentId) {
        return new GrantRevisionEntity(grantId, "cu-1", "prod-1", "PERSON", "CUSTOMER_MEMBERS",
                null, "ONLINE", fulfillmentId);
    }
}
