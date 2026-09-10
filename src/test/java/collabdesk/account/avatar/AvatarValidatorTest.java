package collabdesk.account.avatar;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarValidatorTest {
    private final AvatarProperties properties = new AvatarProperties();
    private final AvatarValidator validator = new AvatarValidator(properties);

    @Test
    void decodesAndReencodesPngInsteadOfTrustingClientMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.svg", "image/svg+xml", png(true)
        );

        ValidatedAvatar result = validator.validate(file);

        assertEquals("image/png", result.contentType());
        assertEquals("png", result.extension());
        assertTrue(result.bytes().length > 8);
        assertEquals(0x89, result.bytes()[0] & 0xff);
    }

    @Test
    void opaqueImageIsNormalizedToJpeg() throws Exception {
        ValidatedAvatar result = validator.validate(new MockMultipartFile(
                "file", "photo.png", "image/png", png(false)
        ));

        assertEquals("image/jpeg", result.contentType());
        assertEquals(0xff, result.bytes()[0] & 0xff);
        assertEquals(0xd8, result.bytes()[1] & 0xff);
    }

    @Test
    void rejectsBrokenAndOversizedContent() {
        assertThrows(InvalidAvatarException.class, () -> validator.validate(
                new MockMultipartFile("file", "broken.png", "image/png", new byte[]{(byte) 0x89, 0x50})
        ));
        properties.setMaxBytes(3);
        assertThrows(AvatarTooLargeException.class, () -> validator.validate(
                new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[4])
        ));
    }

    private byte[] png(boolean transparent) throws Exception {
        BufferedImage image = new BufferedImage(
                8, 8, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        image.setRGB(0, 0, Color.MAGENTA.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
