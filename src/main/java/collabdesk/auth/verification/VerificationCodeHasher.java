package collabdesk.auth.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class VerificationCodeHasher {
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] pepper;

    public VerificationCodeHasher(
            @Value("${app.verification.code-pepper}") String pepper
    ) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("verification code pepper is required");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(
            Long userId,
            VerificationPurpose purpose,
            String destination,
            String code
    ) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(purpose, "purpose is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(code, "code is required");
        String context = userId + ":" + purpose.name() + ":" + destination + ":" + code;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(context.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }

    public boolean matches(
            String storedHash,
            Long userId,
            VerificationPurpose purpose,
            String destination,
            String candidateCode
    ) {
        if (storedHash == null) {
            return false;
        }
        byte[] expected = storedHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(
                userId,
                purpose,
                destination,
                candidateCode
        ).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
