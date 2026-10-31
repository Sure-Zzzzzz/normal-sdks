package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.core.spi.CrmFulfillmentPort;
import io.github.surezzzzzz.sdk.license.server.integration.FulfillmentCoordinationService;
import io.github.surezzzzzz.sdk.license.server.integration.FulfillmentGrantCreationService;
import io.github.surezzzzzz.sdk.license.server.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.FulfillmentGrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * CRM 履约协调器断言：当前水位续报、草稿不冒充业务完成、已启用授权恢复完成回写，
 * 以及认领/保存/回写未明时不反向写 failed。
 *
 * @author surezzzzzz
 */
@Slf4j
class FulfillmentCoordinationTest {

    private CrmFulfillmentPort crm;
    private CustomerRepository customers;
    private ProductRepository products;
    private GrantRepository grants;
    private FulfillmentGrantCreationService grantCreation;
    private FulfillmentCoordinationService service;
    private final java.util.List<String> reports = new java.util.ArrayList<String>();

    @BeforeEach
    void setUp() {
        crm = Mockito.mock(CrmFulfillmentPort.class);
        customers = Mockito.mock(CustomerRepository.class);
        products = Mockito.mock(ProductRepository.class);
        grants = Mockito.mock(GrantRepository.class);
        reports.clear();
        when(customers.findByCode("cu-bank")).thenReturn(Optional.of(
                new CustomerEntity("cu-1", "cu-bank", "某银行")));
        when(products.findById("prod-channel")).thenReturn(Optional.of(
                new ProductEntity("prod-channel", "iss-1", "channel-sys", "渠道管理系统",
                        "[\"channel.create\"]", "[]")));
        when(grants.findByBusinessOrderRef(anyString())).thenReturn(Collections.emptyList());
        when(grants.save(any(GrantRevisionEntity.class))).thenAnswer(call -> call.getArgument(0));
        FulfillmentGrantRepository associations = Mockito.mock(FulfillmentGrantRepository.class);
        when(associations.findById(anyString())).thenReturn(Optional.empty());
        PlatformTransactionManager transactions = Mockito.mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        grantCreation = new FulfillmentGrantCreationService(grants, associations, transactions);
        Mockito.doAnswer(call -> {
            reports.add(call.getArgument(0) + ":" + call.<Integer>getArgument(1) + ":"
                    + call.<CrmFulfillmentPort.Outcome>getArgument(2));
            return null;
        }).when(crm).report(anyString(), anyInt(), any(), any(), any());
        service = new FulfillmentCoordinationService(crm, customers, products, grantCreation);
    }

    private static CrmFulfillmentPort.FulfillmentItem item(String fulfillmentId, String customerRef,
                                                           String offeringRef) {
        return item(fulfillmentId, customerRef, offeringRef, 42L);
    }

    private static CrmFulfillmentPort.FulfillmentItem item(String fulfillmentId, String customerRef,
                                                           String offeringRef, long watermark) {
        return new CrmFulfillmentPort.FulfillmentItem(fulfillmentId, customerRef, "order-1",
                offeringRef, "LICENSE", 5L, "license-consumer", watermark);
    }

    private void pull(CrmFulfillmentPort.FulfillmentItem... items) {
        when(crm.pullPending(null, 50)).thenReturn(
                new CrmFulfillmentPort.FulfillmentPull(Arrays.asList(items), null));
    }

    private GrantRevisionEntity existingGrant(String state) {
        GrantRevisionEntity grant = new GrantRevisionEntity("gr-old", "cu-1", "prod-channel", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", "ff-1");
        grant.setState(state);
        when(grants.findByBusinessOrderRef("ff-1")).thenReturn(Collections.singletonList(grant));
        return grant;
    }

    @Test
    void newlyCreatedDraftWaitsForConfigurationAndUsesCurrentWatermark() {
        pull(item("ff-1", "cu-bank", "prod-channel"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.pulled).isEqualTo(1);
        assertThat(summary.created).isEqualTo(1);
        assertThat(summary.completed).isEqualTo(0);
        assertThat(summary.awaitingConfiguration).isEqualTo(1);
        assertThat(summary.failed).isEqualTo(0);
        // fixture 水位为 42：认领 43，草稿等待配置的进度为 44，绝不回报 completed。
        assertThat(reports).containsExactly(
                "ff-1:43:IN_PROGRESS", "ff-1:44:IN_PROGRESS");
        org.mockito.ArgumentCaptor<GrantRevisionEntity> saved =
                org.mockito.ArgumentCaptor.forClass(GrantRevisionEntity.class);
        Mockito.verify(grants).save(saved.capture());
        assertThat(saved.getValue().getState()).isEqualTo("DRAFT");
        Mockito.verify(crm).report("ff-1", 44, CrmFulfillmentPort.Outcome.IN_PROGRESS,
                "grantId=" + saved.getValue().getGrantId(), "awaiting grant configuration or enablement");
    }

    @Test
    void enabledExistingGrantReportsCompletionWithStableGrantReference() {
        existingGrant("ENABLED");
        pull(item("ff-1", "cu-bank", "prod-channel"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.skippedExisting).isEqualTo(1);
        assertThat(summary.created).isEqualTo(0);
        assertThat(summary.completed).isEqualTo(1);
        assertThat(summary.awaitingConfiguration).isEqualTo(0);
        Mockito.verify(grants, Mockito.never()).save(any());
        assertThat(reports).containsExactly("ff-1:43:COMPLETED");
        Mockito.verify(crm).report("ff-1", 43, CrmFulfillmentPort.Outcome.COMPLETED,
                "grantId=gr-old", null);
    }

    @Test
    void draftExistingGrantIsNotReportedComplete() {
        existingGrant("DRAFT");
        pull(item("ff-1", "cu-bank", "prod-channel"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.skippedExisting).isEqualTo(1);
        assertThat(summary.awaitingConfiguration).isEqualTo(1);
        assertThat(summary.completed).isEqualTo(0);
        Mockito.verify(grants, Mockito.never()).save(any());
        assertThat(reports).containsExactly("ff-1:43:IN_PROGRESS");
    }

    @Test
    void suspendedExistingGrantIsNotReportedComplete() {
        existingGrant("SUSPENDED");
        pull(item("ff-1", "cu-bank", "prod-channel"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.completed).isEqualTo(0);
        assertThat(summary.awaitingConfiguration).isEqualTo(1);
        assertThat(reports).containsExactly("ff-1:43:IN_PROGRESS");
    }

    @Test
    void missingMappingFailsDefinitelyOthersProceed() {
        when(crm.pullPending(null, 50)).thenReturn(new CrmFulfillmentPort.FulfillmentPull(
                Arrays.asList(
                        item("ff-bad", "cu-none", "prod-channel"),
                        item("ff-ok", "cu-bank", "prod-channel")),
                null));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(summary.created).isEqualTo(1);
        assertThat(summary.failures).containsKey("ff-bad");
        assertThat(reports).contains("ff-bad:44:FAILED", "ff-ok:44:IN_PROGRESS");
    }

    @Test
    void claimFailureDoesNotCreateGrantOrReportFailed() {
        pull(item("ff-1", "cu-bank", "prod-channel"));
        Mockito.doThrow(new IllegalStateException("claim response lost")).when(crm)
                .report(eq("ff-1"), eq(43), eq(CrmFulfillmentPort.Outcome.IN_PROGRESS), any(), any());
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(summary.created).isEqualTo(0);
        Mockito.verify(grants, Mockito.never()).save(any());
        Mockito.verify(customers, Mockito.never()).findByCode(anyString());
        Mockito.verify(crm, Mockito.never()).report(anyString(), anyInt(),
                eq(CrmFulfillmentPort.Outcome.FAILED), any(), any());
    }

    @Test
    void saveFailureLeavesClaimInProgressForReadBackRecovery() {
        pull(item("ff-1", "cu-bank", "prod-channel"));
        when(grants.save(any(GrantRevisionEntity.class)))
                .thenThrow(new IllegalStateException("commit result unknown"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(summary.created).isEqualTo(0);
        assertThat(reports).containsExactly("ff-1:43:IN_PROGRESS");
        Mockito.verify(crm, Mockito.never()).report(anyString(), anyInt(),
                eq(CrmFulfillmentPort.Outcome.FAILED), any(), any());
    }

    @Test
    void draftProgressReportFailureKeepsSavedDraftWithoutFailedTerminal() {
        pull(item("ff-1", "cu-bank", "prod-channel"));
        Mockito.doThrow(new IllegalStateException("progress response lost")).when(crm)
                .report(eq("ff-1"), eq(44), eq(CrmFulfillmentPort.Outcome.IN_PROGRESS), any(), any());
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.created).isEqualTo(1);
        assertThat(summary.awaitingConfiguration).isEqualTo(1);
        assertThat(summary.reportPending).isEqualTo(1);
        assertThat(summary.failed).isEqualTo(0);
        Mockito.verify(grants).save(any(GrantRevisionEntity.class));
        Mockito.verify(crm, Mockito.never()).report(anyString(), anyInt(),
                eq(CrmFulfillmentPort.Outcome.FAILED), any(), any());
    }

    @Test
    void completionReportFailureRecoversWithNewWatermarkAndSameGrant() {
        existingGrant("ENABLED");
        pull(item("ff-1", "cu-bank", "prod-channel"));
        Mockito.doThrow(new IllegalStateException("completion response lost")).when(crm)
                .report("ff-1", 43, CrmFulfillmentPort.Outcome.COMPLETED, "grantId=gr-old", null);
        FulfillmentCoordinationService.SyncSummary failedReport = service.sync(50);
        assertThat(failedReport.reportPending).isEqualTo(1);
        assertThat(failedReport.completed).isEqualTo(0);
        assertThat(failedReport.failed).isEqualTo(0);
        pull(item("ff-1", "cu-bank", "prod-channel", 43L));
        FulfillmentCoordinationService.SyncSummary recovered = service.sync(50);
        assertThat(recovered.completed).isEqualTo(1);
        assertThat(recovered.reportPending).isEqualTo(0);
        Mockito.verify(crm).report("ff-1", 44, CrmFulfillmentPort.Outcome.COMPLETED,
                "grantId=gr-old", null);
        Mockito.verify(grants, Mockito.never()).save(any());
        Mockito.verify(crm, Mockito.never()).report(anyString(), anyInt(),
                eq(CrmFulfillmentPort.Outcome.FAILED), any(), any());
    }

    @Test
    void mappingReadFailureDoesNotReportBusinessFailure() {
        pull(item("ff-1", "cu-bank", "prod-channel"));
        when(customers.findByCode("cu-bank")).thenThrow(new IllegalStateException("database unavailable"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(reports).containsExactly("ff-1:43:IN_PROGRESS");
        Mockito.verify(grants, Mockito.never()).save(any());
    }

    @Test
    void exhaustedSequenceDoesNotWrapOrWrite() {
        pull(item("ff-1", "cu-bank", "prod-channel", Integer.MAX_VALUE));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(reports).isEmpty();
        Mockito.verify(grants, Mockito.never()).save(any());
    }

    @Test
    void existingGrantMayUseLastAvailableSequence() {
        existingGrant("ENABLED");
        pull(item("ff-1", "cu-bank", "prod-channel", Integer.MAX_VALUE - 1L));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.completed).isEqualTo(1);
        Mockito.verify(crm).report("ff-1", Integer.MAX_VALUE, CrmFulfillmentPort.Outcome.COMPLETED,
                "grantId=gr-old", null);
    }

    @Test
    void newGrantRequiresTwoAvailableReportSequences() {
        pull(item("ff-1", "cu-bank", "prod-channel", Integer.MAX_VALUE - 1L));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(reports).isEmpty();
        Mockito.verify(grants, Mockito.never()).save(any());
    }

    @Test
    void ambiguousExistingGrantsStopWithoutChoosingOrWriting() {
        GrantRevisionEntity first = existingGrant("ENABLED");
        GrantRevisionEntity second = new GrantRevisionEntity("gr-other", "cu-1", "prod-channel", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", "ff-1");
        when(grants.findByBusinessOrderRef("ff-1")).thenReturn(Arrays.asList(first, second));
        pull(item("ff-1", "cu-bank", "prod-channel"));
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.failed).isEqualTo(1);
        assertThat(summary.completed).isEqualTo(0);
        assertThat(reports).isEmpty();
        Mockito.verify(grants, Mockito.never()).save(any());
    }

    @Test
    void missingPortFailsExplicitly() {
        service = new FulfillmentCoordinationService(null, customers, products, grantCreation);
        assertThat(service.portAvailable()).isFalse();
        assertThatThrownBy(() -> service.sync(50)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cursorPagingPullsUntilExhausted() {
        when(crm.pullPending(any(), anyInt())).thenAnswer(call -> {
            String cursor = call.getArgument(0);
            return "page-1".equals(cursor)
                    ? new CrmFulfillmentPort.FulfillmentPull(Arrays.asList(item("ff-2", "cu-bank", "prod-channel")), null)
                    : new CrmFulfillmentPort.FulfillmentPull(Arrays.asList(item("ff-1", "cu-bank", "prod-channel")), "page-1");
        });
        FulfillmentCoordinationService.SyncSummary summary = service.sync(50);
        assertThat(summary.pulled).isEqualTo(2);
        assertThat(summary.created).isEqualTo(2);
        assertThat(summary.awaitingConfiguration).isEqualTo(2);
        assertThat(summary.completed).isEqualTo(0);
    }
}
