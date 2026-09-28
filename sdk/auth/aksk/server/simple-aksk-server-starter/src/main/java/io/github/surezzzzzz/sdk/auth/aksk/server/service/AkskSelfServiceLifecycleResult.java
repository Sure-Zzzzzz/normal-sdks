package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 自助生命周期命令的受控结果，供 HTTP 层映射 404/409/412。
 */
@Getter
@AllArgsConstructor
public class AkskSelfServiceLifecycleResult {

    private final State state;
    private final ClientInfoResponse client;

    public static AkskSelfServiceLifecycleResult success(ClientInfoResponse client) {
        return new AkskSelfServiceLifecycleResult(State.SUCCESS, client);
    }

    public static AkskSelfServiceLifecycleResult replayed(ClientInfoResponse client) {
        return new AkskSelfServiceLifecycleResult(State.REPLAYED, client);
    }

    public static AkskSelfServiceLifecycleResult state(State state) {
        return new AkskSelfServiceLifecycleResult(state, null);
    }

    public enum State {
        SUCCESS,
        REPLAYED,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_FAILED
    }
}
