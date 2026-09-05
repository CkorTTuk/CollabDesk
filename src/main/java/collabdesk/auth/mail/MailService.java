package collabdesk.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MailService {
    private final String from;

    private final JavaMailSender mailSender;
    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }
    public void sendEmailVerification(String toEmail, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Verify your email address");
        message.setText(String.format(
                """
                Hello!
                
                You're almost there! Enter the verification code below to verify your email address:
                
                %s
                
                This code will expire in %d minutes.
                
                If you didn't create an account, you can safely ignore this email.
                
                Best wishes!
                """,
                code,
                validity.toMinutes()
                ));
        mailSender.send(message);
    }

}
