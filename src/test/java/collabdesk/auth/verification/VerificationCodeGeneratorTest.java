package collabdesk.auth.verification;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationCodeGeneratorTest {
    @Test
    void generatesExactlySixDigitsAndPreservesLeadingZeroes() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(42);

        String code = new VerificationCodeGenerator(random).generate();

        assertEquals("000042", code);
    }
}
