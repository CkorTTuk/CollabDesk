package collabdesk.auth.passwordEncode;


import collabdesk.auth.config.PasswordEncoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(PasswordEncoderConfig.class)
public class PasswordEncoderTest {
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Test
    void testRawPasswordDoesntMatchHash(){
        String rawPassword = "passwordpassword";

        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }
    @Test
    void testRawPasswordMatchesHash(){

        String encodedPassword = passwordEncoder.encode("passwordpassword");

        assertFalse(passwordEncoder.matches("password", encodedPassword));
    }
}
