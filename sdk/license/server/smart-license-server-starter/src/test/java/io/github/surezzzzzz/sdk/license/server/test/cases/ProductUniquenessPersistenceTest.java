package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 产品编码唯一性：同签发方并发写入只成功一次，不同签发方可使用相同编码。
 *
 * <p>两个线程分别经过生产仓储事务；不以顺序先查后存代替数据库并发屏障。</p>
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductUniquenessPersistenceTest {

    @Autowired
    private ProductRepository products;

    @Test
    void concurrentSameIssuerCodeHasExactlyOneCommittedWinner() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String issuer = "issuer-" + suffix;
        String code = "code-" + suffix;
        String firstId = "product-a-" + suffix;
        String secondId = "product-b-" + suffix;
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = threads.submit(() -> insert(firstId, issuer, code, ready, start));
            Future<Boolean> second = threads.submit(() -> insert(secondId, issuer, code, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean firstSucceeded = first.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(20, TimeUnit.SECONDS);
            assertThat(firstSucceeded).isNotEqualTo(secondSucceeded);
            assertThat(products.findById(firstId).isPresent()).isEqualTo(firstSucceeded);
            assertThat(products.findById(secondId).isPresent()).isEqualTo(secondSucceeded);
            assertThat(products.findByIssuerIdAndCode(issuer, code)).isPresent();
        } finally {
            start.countDown();
            threads.shutdownNow();
            assertThat(threads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            products.findById(firstId).ifPresent(products::delete);
            products.findById(secondId).ifPresent(products::delete);
        }
    }

    @Test
    void differentIssuersMayReuseTheSameProductCode() {
        String suffix = UUID.randomUUID().toString();
        ProductEntity first = new ProductEntity("product-a-" + suffix, "issuer-a-" + suffix,
                "same-code", "示例产品", "[]", "[]");
        ProductEntity second = new ProductEntity("product-b-" + suffix, "issuer-b-" + suffix,
                "same-code", "示例产品", "[]", "[]");
        try {
            products.saveAndFlush(first);
            products.saveAndFlush(second);
            assertThat(products.findByIssuerIdAndCode(first.getIssuerId(), first.getCode()))
                    .get().extracting(ProductEntity::getProductId).isEqualTo(first.getProductId());
            assertThat(products.findByIssuerIdAndCode(second.getIssuerId(), second.getCode()))
                    .get().extracting(ProductEntity::getProductId).isEqualTo(second.getProductId());
        } finally {
            products.findById(first.getProductId()).ifPresent(products::delete);
            products.findById(second.getProductId()).ifPresent(products::delete);
        }
    }

    private boolean insert(String id, String issuer, String code,
                           CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发写入屏障未就绪");
        }
        try {
            products.saveAndFlush(new ProductEntity(id, issuer, code, "示例产品", "[]", "[]"));
            return true;
        } catch (DataIntegrityViolationException expectedConflict) {
            return false;
        }
    }
}
