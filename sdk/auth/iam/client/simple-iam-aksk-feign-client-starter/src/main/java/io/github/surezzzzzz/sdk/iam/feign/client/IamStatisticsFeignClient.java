package io.github.surezzzzzz.sdk.iam.feign.client;

/**
 * IAM 统计 Feign 契约接口（1 方法，1.3.7）。
 *
 * @author surezzzzzz
 */
@io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient(
        name = "iam-statistics", url = "${io.github.surezzzzzz.sdk.iam.client.base-url}",
        path = io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant.API_BASE_PATH)
public interface IamStatisticsFeignClient {

    /**
     * 统计聚合（只读；此族持 iam:dashboard:api）。
     */
    @org.springframework.web.bind.annotation.GetMapping("/statistics")
    io.github.surezzzzzz.sdk.iam.feign.client.model.IamStatisticsResponse getStatistics();
}
