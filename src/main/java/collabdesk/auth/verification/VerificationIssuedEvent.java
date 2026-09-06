package collabdesk.auth.verification;

import java.time.Duration;
import java.util.Objects;

public final class VerificationIssuedEvent {
    private final String destination;
    private final String code;
    private final Duration validity;

    public VerificationIssuedEvent(
            String destination,
            String code,
            Duration validity
    ) {
        this.destination = Objects.requireNonNull(destination);
        this.code = Objects.requireNonNull(code);
        this.validity = Objects.requireNonNull(validity);
    }

    public String destination() {
        return destination;
    }

    public String code() {
        return code;
    }

    public Duration validity() {
        return validity;
    }

    @Override
    public String toString() {
        return "VerificationIssuedEvent[destination=REDACTED, code=REDACTED]";
    }
}
