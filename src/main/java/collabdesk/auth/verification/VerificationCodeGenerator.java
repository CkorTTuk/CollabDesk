package collabdesk.auth.verification;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {
    private final SecureRandom secureRandom;

    public VerificationCodeGenerator() {
        this(new SecureRandom());
    }

    VerificationCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
