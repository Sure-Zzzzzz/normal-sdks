package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;

import java.util.Optional;

/** 商机权威仓储端口。 */
public interface OpportunityRepository {
    Optional<Opportunity> findById(String opportunityId);
    Opportunity insert(Opportunity opportunity);
    Opportunity update(Opportunity opportunity, long expectedAggregateVersion);
}
