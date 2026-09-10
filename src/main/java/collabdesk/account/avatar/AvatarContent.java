package collabdesk.account.avatar;

public record AvatarContent(byte[] bytes, String contentType) {
    public AvatarContent {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() { return bytes.clone(); }
}
