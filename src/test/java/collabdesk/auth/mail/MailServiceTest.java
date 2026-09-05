package collabdesk.auth.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {
    private static final String FROM = "no-reply@collabdesk.local";

    @Mock
    private JavaMailSender mailSender;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(mailSender, FROM);
    }

    @Test
    void sendsEmailVerificationMessageWithConfiguredEnvelopeAndContent() {
        mailService.sendEmailVerification(
                "member@example.com",
                "004271",
                Duration.ofMinutes(10)
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        String text = message.getText();

        assertAll(
                () -> assertEquals(FROM, message.getFrom()),
                () -> assertArrayEquals(
                        new String[]{"member@example.com"},
                        message.getTo()
                ),
                () -> assertEquals(
                        "Verify your CollabDesk email",
                        message.getSubject()
                ),
                () -> assertTrue(text.contains("Hello!")),
                () -> assertTrue(text.contains("004271")),
                () -> assertTrue(text.contains("expire in 10")),
                () -> assertTrue(text.contains(
                        "If you didn't create an account"
                ))
        );
    }

    @Test
    void propagatesSmtpFailureToTheFutureAfterCommitListener() {
        MailSendException failure = new MailSendException(
                "Simulated SMTP failure"
        );
        doThrow(failure)
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        MailSendException thrown = assertThrows(
                MailSendException.class,
                () -> mailService.sendEmailVerification(
                        "member@example.com",
                        "004271",
                        Duration.ofMinutes(10)
                )
        );

        assertEquals(failure, thrown);
    }
}
