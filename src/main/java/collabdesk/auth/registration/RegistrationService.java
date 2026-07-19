package collabdesk.auth.registration;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;


@Service

public class RegistrationService {
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
    }
    @Transactional
    public RegistrationResult register(String email, String displayName, String rawPassword){
        if( email == null || displayName == null || rawPassword == null){
            throw new NullPointerException("Parameters cannot be null");
        }
        if( email.isBlank() || displayName.isBlank() || rawPassword.isBlank() ){
            throw new IllegalArgumentException("You must provide all parameters non-Empty");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if( userRepository.existsByEmail(normalizedEmail) ){
            throw new EmailAlreadyExistsException("there is already an account with that email");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(normalizedEmail, displayName);

        User savedUser = userRepository.save(user);

        AuthIdentity authIdentity = AuthIdentity.local( savedUser, normalizedEmail, passwordHash);
        authIdentityRepository.save(authIdentity);

        return new RegistrationResult(savedUser.getId(), savedUser.getEmail(), savedUser.getDisplayName(), savedUser.getStatus());

    }
}
