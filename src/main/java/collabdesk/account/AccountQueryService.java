package collabdesk.account;

import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.repository.AuthIdentityRepository;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import collabdesk.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountQueryService {
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AvatarUrlFactory avatarUrlFactory;

    public AccountQueryService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            AvatarUrlFactory avatarUrlFactory
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.avatarUrlFactory = avatarUrlFactory;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long userId) {
        User user = requireAvailableUser(userId);
        return toResponse(user);
    }

    public User requireAvailableUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(User::isOnboardingCompleted)
                .orElseThrow(AccountUnavailableException::new);
    }

    public AccountResponse toResponse(User user) {
        List<AuthProvider> providers = authIdentityRepository
                .findProvidersByUserId(user.getId())
                .stream()
                .filter(provider -> provider != AuthProvider.LOCAL)
                .toList();
        return AccountResponse.from(user, providers, avatarUrlFactory);
    }
}
