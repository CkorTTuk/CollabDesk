package collabdesk.account.avatar;

import collabdesk.account.AccountQueryService;
import collabdesk.account.AccountResponse;
import collabdesk.user.entity.User;
import collabdesk.user.repository.UserRepository;
import collabdesk.infrastructure.cache.WorkspaceProjectAccessChangePublisher;
import collabdesk.workspace.member.repository.WorkspaceMemberRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarService.class);
    private final AvatarValidator validator;
    private final AvatarStorage storage;
    private final UserRepository userRepository;
    private final AccountQueryService accountQueryService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceProjectAccessChangePublisher accessChangePublisher;

    public AvatarService(
            AvatarValidator validator,
            AvatarStorage storage,
            UserRepository userRepository,
            AccountQueryService accountQueryService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceProjectAccessChangePublisher accessChangePublisher
    ) {
        this.validator = validator;
        this.storage = storage;
        this.userRepository = userRepository;
        this.accountQueryService = accountQueryService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.accessChangePublisher = accessChangePublisher;
    }

    @Transactional
    public AccountResponse replace(Long userId, MultipartFile file) {
        User user = accountQueryService.requireAvailableUser(userId);
        ValidatedAvatar validated = validator.validate(file);
        StoredAvatar stored = storage.store(validated);
        String oldKey = user.getAvatarKey();
        registerReplaceCleanup(stored.key(), oldKey);
        user.changeAvatar(stored.key());
        userRepository.saveAndFlush(user);
        publishProfileChange(userId);
        return accountQueryService.toResponse(user);
    }

    @Transactional
    public AccountResponse remove(Long userId) {
        User user = accountQueryService.requireAvailableUser(userId);
        String oldKey = user.getAvatarKey();
        if (oldKey == null) {
            return accountQueryService.toResponse(user);
        }
        user.changeAvatar(null);
        userRepository.saveAndFlush(user);
        publishProfileChange(userId);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() { deleteQuietly(oldKey); }
                }
        );
        return accountQueryService.toResponse(user);
    }

    @Transactional(readOnly = true)
    public AvatarContent load(String key) {
        try {
            return storage.load(key);
        } catch (IllegalArgumentException exception) {
            throw new AvatarNotFoundException();
        }
    }

    private void registerReplaceCleanup(String newKey, @Nullable String oldKey) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (oldKey != null) deleteQuietly(oldKey);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteQuietly(newKey);
                    }
                }
        );
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.warn("Avatar cleanup failed for key {}", key, exception);
        }
    }

    private void publishProfileChange(Long userId) {
        workspaceMemberRepository.findByUser_IdOrderByWorkspace_CreatedAtAsc(userId)
                .stream()
                .map(member -> member.getWorkspace().getId())
                .distinct()
                .forEach(accessChangePublisher::publish);
    }
}
