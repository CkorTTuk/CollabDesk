package collabdesk.account.avatar;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class AvatarValidator {
    private final AvatarProperties properties;

    public AvatarValidator(AvatarProperties properties) {
        this.properties = properties;
    }

    public ValidatedAvatar validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarException("Choose a non-empty JPEG or PNG image");
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new AvatarTooLargeException();
        }
        try {
            byte[] source = file.getInputStream().readNBytes(
                    Math.toIntExact(properties.getMaxBytes() + 1)
            );
            if (source.length > properties.getMaxBytes()) {
                throw new AvatarTooLargeException();
            }
            requireSupportedSignature(source);
            return decodeAndNormalize(source);
        } catch (AvatarTooLargeException | InvalidAvatarException exception) {
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            throw new InvalidAvatarException("The image could not be read", exception);
        }
    }

    private ValidatedAvatar decodeAndNormalize(byte[] source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(source)
        )) {
            if (input == null) {
                throw new InvalidAvatarException("The image could not be decoded");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidAvatarException("Only JPEG and PNG images are supported");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new InvalidAvatarException("Only JPEG and PNG images are supported");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidAvatarException("The image could not be decoded");
                }
                return encodeNormalized(decoded);
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0
                || width > properties.getMaxWidth()
                || height > properties.getMaxHeight()
                || (long) width * height > (long) properties.getMaxWidth()
                * properties.getMaxHeight()) {
            throw new InvalidAvatarException("Image dimensions are not allowed");
        }
    }

    private ValidatedAvatar encodeNormalized(BufferedImage decoded) throws IOException {
        boolean transparent = decoded.getColorModel().hasAlpha();
        String format = transparent ? "png" : "jpg";
        BufferedImage output = decoded;
        if (!transparent) {
            output = new BufferedImage(
                    decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(output, format, bytes)) {
            throw new InvalidAvatarException("The image could not be normalized");
        }
        byte[] normalized = bytes.toByteArray();
        if (normalized.length > properties.getMaxBytes()) {
            throw new AvatarTooLargeException();
        }
        return new ValidatedAvatar(
                normalized,
                transparent ? "image/png" : "image/jpeg",
                transparent ? "png" : "jpg"
        );
    }

    private void requireSupportedSignature(byte[] bytes) {
        boolean jpeg = bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        boolean isPng = bytes.length >= png.length;
        for (int index = 0; isPng && index < png.length; index++) {
            isPng = bytes[index] == png[index];
        }
        if (!jpeg && !isPng) {
            throw new InvalidAvatarException("Only JPEG and PNG images are supported");
        }
    }
}
