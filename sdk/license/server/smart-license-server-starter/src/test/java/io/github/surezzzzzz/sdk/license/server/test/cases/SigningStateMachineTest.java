package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.state.SigningOperationStateMachine;
import org.junit.jupiter.api.Test;

/**
 * 签发状态机断言：合法迁移表、CAS 版本围栏、恢复代数、终态不可逆。
 *
 * @author surezzzzzz
 */
class SigningStateMachineTest {

    @Test
    void happyPathPendingToSigningToReady() {
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.PENDING_SIGN, 1L, 1L);
        assertThat(machine.transition(OperationState.SIGNING, 1L)).isTrue();
        assertThat(machine.transition(OperationState.READY, 2L)).isTrue();
        assertThat(machine.getState()).isEqualTo(OperationState.READY);
        // READY 终态不可逆
        assertThat(machine.transition(OperationState.SIGNING, 3L)).isFalse();
        assertThat(machine.transition(OperationState.ABANDONED, 3L)).isFalse();
    }

    @Test
    void staleRowVersionRejectedByCas() {
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.PENDING_SIGN, 5L, 1L);
        assertThat(machine.transition(OperationState.SIGNING, 4L)).isFalse();
        assertThat(machine.getState()).isEqualTo(OperationState.PENDING_SIGN);
        assertThat(machine.transition(OperationState.SIGNING, 5L)).isTrue();
    }

    @Test
    void uncertainRecoversWithGenerationBump() {
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.UNCERTAIN, 3L, 2L);
        assertThat(machine.recover(2L)).isFalse(); // 版本不符
        assertThat(machine.recover(3L)).isTrue();
        assertThat(machine.getState()).isEqualTo(OperationState.SIGNING);
        assertThat(machine.getExecutionGeneration()).isEqualTo(3L);
        assertThat(machine.getRowVersion()).isEqualTo(4L);
        // 非 UNCERTAIN 不能恢复
        assertThat(machine.recover(4L)).isFalse();
    }

    @Test
    void rejectedAndAbandonedAreTerminal() {
        SigningOperationStateMachine rejected = new SigningOperationStateMachine(
                OperationState.REJECTED, 2L, 1L);
        assertThat(rejected.transition(OperationState.SIGNING, 2L)).isFalse();

        SigningOperationStateMachine abandoned = new SigningOperationStateMachine(
                OperationState.ABANDONED, 2L, 1L);
        assertThat(abandoned.transition(OperationState.SIGNING, 2L)).isFalse();
    }

    @Test
    void pendingCannotJumpToReady() {
        SigningOperationStateMachine machine = new SigningOperationStateMachine(
                OperationState.PENDING_SIGN, 1L, 1L);
        assertThat(machine.transition(OperationState.READY, 1L)).isFalse();
        assertThat(machine.transition(OperationState.UNCERTAIN, 1L)).isFalse();
    }

    @Test
    void staleSigningTakeoverIsDistinctFromNormalRecoveryAndMatchesBothFences() {
        SigningOperationStateMachine machine = new SigningOperationStateMachine(OperationState.SIGNING, 5L, 2L);
        assertThat(machine.transition(OperationState.SIGNING, 5L)).isFalse();
        assertThat(machine.recover(5L)).isFalse();
        assertThat(machine.takeOverStaleSigning(4L, 2L)).isFalse();
        assertThat(machine.takeOverStaleSigning(5L, 1L)).isFalse();
        assertThat(machine.takeOverStaleSigning(5L, 2L)).isTrue();
        assertThat(machine.getState()).isEqualTo(OperationState.SIGNING);
        assertThat(machine.getExecutionGeneration()).isEqualTo(3L);
        assertThat(machine.getRowVersion()).isEqualTo(6L);
        assertThat(machine.takeOverStaleSigning(5L, 2L)).isFalse();
        assertThat(new SigningOperationStateMachine(OperationState.READY, 5L, 2L)
                .takeOverStaleSigning(5L, 2L)).isFalse();
    }

    @Test
    void maximumVersionAndGenerationNeverWrapAround() {
        assertThat(new SigningOperationStateMachine(OperationState.PENDING_SIGN, Long.MAX_VALUE, 1L)
                .transition(OperationState.SIGNING, Long.MAX_VALUE)).isFalse();
        assertThat(new SigningOperationStateMachine(OperationState.UNCERTAIN, 5L, Long.MAX_VALUE)
                .recover(5L)).isFalse();
        assertThat(new SigningOperationStateMachine(OperationState.SIGNING, 5L, Long.MAX_VALUE)
                .takeOverStaleSigning(5L, Long.MAX_VALUE)).isFalse();
        assertThat(new SigningOperationStateMachine(OperationState.SIGNING, Long.MAX_VALUE, 2L)
                .takeOverStaleSigning(Long.MAX_VALUE, 2L)).isFalse();
    }
}
