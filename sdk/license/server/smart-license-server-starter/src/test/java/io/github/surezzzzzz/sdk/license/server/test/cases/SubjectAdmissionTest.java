package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.entity.OrganizationBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.OrganizationBindingRepository;
import io.github.surezzzzzz.sdk.license.core.spi.IamDirectoryPort;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 部门准入裁决四态断言：未绑定直通/子树内放行/子树外拒/目录不可达失败关闭。
 *
 * @author surezzzzzz
 */
class SubjectAdmissionTest {

    private OrganizationBindingRepository bindings;
    private IamDirectoryPort directory;
    private SubjectAdmissionService service;

    @BeforeEach
    void setUp() {
        bindings = Mockito.mock(OrganizationBindingRepository.class);
        directory = Mockito.mock(IamDirectoryPort.class);
        service = new SubjectAdmissionService(bindings, directory);
    }

    private void bound() {
        when(bindings.findById("cu-1")).thenReturn(Optional.of(
                new OrganizationBindingEntity("cu-1", "org-1", 9L)));
    }

    @Test
    void unboundCustomerPassesThrough() {
        when(bindings.findById("cu-1")).thenReturn(Optional.empty());
        assertThat(service.isAdmitted("cu-1", "u-1"))
                .isEqualTo(SubjectAdmissionService.Admission.NOT_BOUND);
        Mockito.verifyNoInteractions(directory);
    }

    @Test
    void withinRootAdmitted() {
        bound();
        when(directory.isMemberWithinRoot(9L, "u-1")).thenReturn(Optional.of(Boolean.TRUE));
        assertThat(service.isAdmitted("cu-1", "u-1"))
                .isEqualTo(SubjectAdmissionService.Admission.ADMITTED);
    }

    @Test
    void outsideRootDeniedAndNullSubjectDenied() {
        bound();
        when(directory.isMemberWithinRoot(9L, "u-2")).thenReturn(Optional.of(Boolean.FALSE));
        assertThat(service.isAdmitted("cu-1", "u-2"))
                .isEqualTo(SubjectAdmissionService.Admission.DENIED);
        assertThat(service.isAdmitted("cu-1", null))
                .as("绑定客户主体信息不足=拒绝").isEqualTo(SubjectAdmissionService.Admission.DENIED);
    }

    @Test
    void directoryUnreachableFailsClosed() {
        bound();
        when(directory.isMemberWithinRoot(anyLong(), anyString())).thenReturn(Optional.empty());
        assertThat(service.isAdmitted("cu-1", "u-1"))
                .isEqualTo(SubjectAdmissionService.Admission.UNAVAILABLE);
        when(directory.isMemberWithinRoot(anyLong(), anyString()))
                .thenThrow(new RuntimeException("目录超时"));
        assertThat(service.isAdmitted("cu-1", "u-1"))
                .isEqualTo(SubjectAdmissionService.Admission.UNAVAILABLE);
    }

    @Test
    void boundWithoutPortFailsClosed() {
        bound();
        assertThat(new SubjectAdmissionService(bindings, null).isAdmitted("cu-1", "u-1"))
                .isEqualTo(SubjectAdmissionService.Admission.UNAVAILABLE);
    }
}
