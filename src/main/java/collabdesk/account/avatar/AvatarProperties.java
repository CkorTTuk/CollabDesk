package collabdesk.account.avatar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.avatar")
public class AvatarProperties {
    @NotBlank
    private String localDirectory = "./data/avatars";
    @Min(1)
    @Max(20_000_000)
    private long maxBytes = 5_242_880;
    @Min(1)
    private int maxWidth = 4096;
    @Min(1)
    private int maxHeight = 4096;

    public String getLocalDirectory() { return localDirectory; }
    public void setLocalDirectory(String localDirectory) { this.localDirectory = localDirectory; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int maxHeight) { this.maxHeight = maxHeight; }
}
