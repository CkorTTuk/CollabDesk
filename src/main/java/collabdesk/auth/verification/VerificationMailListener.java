package collabdesk.auth.verification;

import collabdesk.auth.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationMailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            VerificationMailListener.class
    );

    private final MailService mailService;

    public VerificationMailListener(MailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(VerificationIssuedEvent event) {
        try {
            mailService.sendEmailVerification(
                    event.destination(),
                    event.code(),
                    event.validity()
            );
        } catch (MailException exception) {
            LOGGER.warn("Email verification delivery failed after commit: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
