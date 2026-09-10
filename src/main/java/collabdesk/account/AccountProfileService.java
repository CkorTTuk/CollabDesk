package collabdesk.account;

import collabdesk.auth.security.CurrentAuthenticationRefresher;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountProfileService {
    private final UserRepository userRepository;
    private final AccountQueryService accountQueryService;
    private final CurrentAuthenticationRefresher authenticationRefresher;

    public AccountProfileService(
            UserRepository userRepository,
            AccountQueryService accountQueryService,
            CurrentAuthenticationRefresher authenticationRefresher
    ) {
        this.userRepository = userRepository;
        this.accountQueryService = accountQueryService;
        this.authenticationRefresher = authenticationRefresher;
    }

    @Transactional
    public AccountResponse updateProfile(
            Long userId,
            UpdateProfileRequest update,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            User user = accountQueryService.requireAvailableUser(userId);
            user.changeProfile(update.firstName(), update.lastName(), update.birthDate());
            userRepository.saveAndFlush(user);
            authenticationRefresher.refresh(user, authentication, request, response);
            return accountQueryService.toResponse(user);
        } catch (OptimisticLockingFailureException exception) {
            throw new ProfileUpdateConflictException();
        }
    }

    @Transactional
    public AccountResponse updateLocale(Long userId, UpdateLocaleRequest update) {
        User user = accountQueryService.requireAvailableUser(userId);
        user.changePreferredLocale(update.locale().tag());
        userRepository.saveAndFlush(user);
        return accountQueryService.toResponse(user);
    }
}
