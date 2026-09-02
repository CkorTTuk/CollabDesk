package collabdesk.auth.external;

import collabdesk.TestcontainersConfiguration;
import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalAccountRollbackTest {

    @Autowired
    private ExternalAccountService externalAccountService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AuthIdentityRepository authIdentityRepository;

    @Test
    void rollsBackNewUserWhenIdentityPersistenceFails() {
        when(authIdentityRepository.save(any(AuthIdentity.class)))
                .thenThrow(new IllegalStateException(
                        "Simulated auth identity persistence failure"
                ));

        ExternalIdentity identity = new ExternalIdentity(
                AuthProvider.GOOGLE,
                "rollback-subject",
                "external-rollback@example.com",
                null,
                null,
                null
        );

        assertThrowsExactly(
                IllegalStateException.class,
                () -> externalAccountService.findOrCreate(identity)
        );

        assertFalse(userRepository.existsByEmail("external-rollback@example.com"));
    }
}
