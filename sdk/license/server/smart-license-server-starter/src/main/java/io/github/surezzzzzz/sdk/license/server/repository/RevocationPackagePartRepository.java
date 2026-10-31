package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackagePartEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackagePartId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 吊销包分片仓储；按序号返回，内容接口无需加载其他包。 */
public interface RevocationPackagePartRepository extends JpaRepository<RevocationPackagePartEntity, RevocationPackagePartId> {
    List<RevocationPackagePartEntity> findByPackageIdOrderByPartIndexAsc(String packageId);
}
