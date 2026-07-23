package collabdesk.auth.registration;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.config.PasswordEncoderConfig;
import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.registration.EmailAlreadyExistsException;
import collabdesk.auth.registration.RegistrationResult;
import collabdesk.auth.registration.RegistrationService;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({TestcontainersConfiguration.class,
        RegistrationService.class,
        PasswordEncoderConfig.class})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
public class RegistrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthIdentityRepository authIdentityRepository;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Test
    public void ThereShouldBeOnlyOneUserAfterRegistrationWithCorrectDataTest() {
        RegistrationResult registrationResult = registrationService.register("  eMAAIILL@GmAiL.CoM", "Name", "password");

        int i = userRepository.findAll().size();

        assertEquals(1, i);
        User user =  userRepository.findAll().getFirst();

        i = authIdentityRepository.findAll().size();
        assertEquals(1, i);

        AuthIdentity authIdentity = authIdentityRepository.findAll().getFirst();
        assertAll(()->{
            assertEquals(user.getId(), registrationResult.id());
            assertEquals("emaaiill@gmail.com", registrationResult.email());
            assertEquals("Name", registrationResult.displayName());
            assertEquals(UserStatus.ACTIVE, registrationResult.status());
            assertEquals("emaaiill@gmail.com",user.getEmail());
            assertEquals("Name", user.getDisplayName());
            assertEquals(user.getId(), authIdentity.getUser().getId());
            assertEquals(AuthProvider.LOCAL, authIdentity.getProvider());
            assertEquals("emaaiill@gmail.com", authIdentity.getProviderSubject());
            assertTrue( passwordEncoder.matches("password",authIdentity.getPasswordHash()));
            assertNotEquals("password", authIdentity.getPasswordHash());
        });
    }
    @Test
    public void DuplicatedEmailShouldNotAddAnotherEntityTest(){
        registrationService.register("email@example.com", "Name", "password");
        assertThrowsExactly(EmailAlreadyExistsException.class, ()->{
           registrationService.register("   emAIL@eXAMPle.cOm  ", "Name", "password");
        });

        int i = userRepository.findAll().size();

        assertEquals(1, i);

        i = authIdentityRepository.findAll().size();
        assertEquals(1, i);
    }
}
