package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.IntegrationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 同键输入核对与结果计划回放台账。 */
public interface IntegrationRequestRepository extends JpaRepository<IntegrationRequestEntity, String> { }
