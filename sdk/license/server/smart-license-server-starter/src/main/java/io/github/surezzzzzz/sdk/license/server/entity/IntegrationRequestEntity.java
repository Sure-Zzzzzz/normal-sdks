package io.github.surezzzzzz.sdk.license.server.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;

/** 本域协作请求幂等台账，只保存主体/路由/键和输入的摘要，不保存人员令牌或原始请求。 */
@Getter
@Entity
@Table(name = "license_integration_request")
public class IntegrationRequestEntity {
    @Id
    @Column(name = "request_digest", length = 64, nullable = false)
    private String requestDigest;
    @Column(name = "input_digest", length = 64, nullable = false)
    private String inputDigest;
    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    protected IntegrationRequestEntity() { }

    /** 同键同输入始终寻址同一结果计划，版本冲突刷新时更新到实际返回的新计划。 */
    public IntegrationRequestEntity(String requestDigest, String inputDigest, String planId) {
        this.requestDigest = requestDigest; this.inputDigest = inputDigest; this.planId = planId;
    }

    /** 执行只重算成新清单时，后续相同请求回读这份最终结果。 */
    public void result(String planId) { this.planId = planId; }
}
