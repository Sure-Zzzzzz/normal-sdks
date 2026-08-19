package io.github.surezzzzzz.sdk.auth.aksk.server.e2eserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AkskE2eControlController {

    private static final String CONTROL_HEADER = "X-Iam-Aksk-E2e-Control";

    private final ConfigurableApplicationContext context;
    private final String controlToken;

    public AkskE2eControlController(ConfigurableApplicationContext context,
                                    @Value("${iam.aksk.e2e.aksk.control-token}") String controlToken) {
        this.context = context;
        this.controlToken = controlToken;
    }

    @GetMapping("/__iam-aksk-e2e/ready")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ready(@RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        requireControlToken(controlToken);
    }

    @PostMapping("/__iam-aksk-e2e/shutdown")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shutdown(@RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        requireControlToken(controlToken);
        Thread shutdownThread = new Thread(context::close, "iam-aksk-e2e-aksk-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    private void requireControlToken(String requestControlToken) {
        if (!controlToken.equals(requestControlToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
