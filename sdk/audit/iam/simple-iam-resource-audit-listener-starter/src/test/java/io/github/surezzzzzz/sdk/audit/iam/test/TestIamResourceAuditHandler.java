package io.github.surezzzzzz.sdk.audit.iam.test;

import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用的 IAM 资源审计处理器
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@Slf4j
public class TestIamResourceAuditHandler implements IamResourceAuditHandler {

    public final List<IamResourceAuditRecord> records = new ArrayList<IamResourceAuditRecord>();
    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void handle(IamResourceAuditRecord record) {
        log.info("收到IAM资源审计记录: subjectId={}, uri={}, requestId={}",
                record.getSubjectId(), record.getRequestUri(), record.getRequestId());
        records.add(record);
        latch.countDown();
    }

    public void reset() {
        records.clear();
        latch = new CountDownLatch(1);
    }
}
