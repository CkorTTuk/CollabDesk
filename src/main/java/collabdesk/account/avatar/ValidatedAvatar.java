package collabdesk.account.avatar;

public record ValidatedAvatar(byte[] bytes, String contentType, String extension) {
    public ValidatedAvatar {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() { return bytes.clone(); }
}
