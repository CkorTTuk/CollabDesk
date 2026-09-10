package collabdesk.account.avatar;

public interface AvatarStorage {
    StoredAvatar store(ValidatedAvatar avatar);
    AvatarContent load(String key);
    void delete(String key);
}
