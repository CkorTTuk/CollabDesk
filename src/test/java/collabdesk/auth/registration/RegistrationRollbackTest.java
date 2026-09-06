package collabdesk.auth.registration;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.auth.verification.VerificationChallengeRepository;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistrationRollbackTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationChallengeRepository challengeRepository;

    @MockitoBean
    private AuthIdentityRepository authIdentityRepository;

    @Test
    void rollsBackUserWhenSavingAuthIdentityFails() {
        assertEquals(0, userRepository.count());

        when(authIdentityRepository.save(any(AuthIdentity.class)))
                .thenThrow(new IllegalStateException(
                        "Simulated auth identity persistence failure"
                ));

        assertThrowsExactly(
                IllegalStateException.class,
                () -> registrationService.register(
                        "rollback@example.com",
                        "password123"
                )
        );

        verify(authIdentityRepository).save(any(AuthIdentity.class));
        assertEquals(0, userRepository.count());
        assertEquals(0, challengeRepository.count());
    }
}
