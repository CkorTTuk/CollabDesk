package collabdesk.auth.verification;

import collabdesk.auth.mail.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationMailListenerTest {
    @Test
    void sendsIssuedCodeThroughMailService() {
        MailService mailService = mock(MailService.class);
        VerificationMailListener listener =
                new VerificationMailListener(mailService);

        listener.sendVerificationEmail(new VerificationIssuedEvent(
                "member@example.com",
                "004271",
                Duration.ofMinutes(10)
        ));

        verify(mailService).sendEmailVerification(
                "member@example.com",
                "004271",
                Duration.ofMinutes(10)
        );
    }

    @Test
    void smtpFailureDoesNotEscapeAfterCommitListener() {
        MailService mailService = mock(MailService.class);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailService)
                .sendEmailVerification(
                        "member@example.com",
                        "004271",
                        Duration.ofMinutes(10)
                );
        VerificationMailListener listener =
                new VerificationMailListener(mailService);

        assertDoesNotThrow(() -> listener.sendVerificationEmail(
                new VerificationIssuedEvent(
                        "member@example.com",
                        "004271",
                        Duration.ofMinutes(10)
                )
        ));
    }
}
