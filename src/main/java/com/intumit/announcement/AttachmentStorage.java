package com.intumit.announcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

@Service
public class AttachmentStorage {
    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path root;

    public AttachmentStorage(@Value("${app.upload-dir}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        try { Files.createDirectories(root); }
        catch (IOException ex) { throw new UncheckedIOException("無法建立附件目錄", ex); }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || (file.isEmpty() && (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()))) {
            return null;
        }
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new InvalidAttachmentException("附件不可為空，且大小不得超過 5 MB");
        }
        String original = file.getOriginalFilename();
        if (original == null) throw new InvalidAttachmentException("附件缺少檔名");
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1).strip();
        if (original.isEmpty() || original.length() > 255 || original.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidAttachmentException("附件檔名無效或超過 255 字");
        }
        String extension = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException ex) { throw new UncheckedIOException("無法讀取附件", ex); }
        if (bytes.length > MAX_BYTES) throw new InvalidAttachmentException("附件大小不得超過 5 MB");
        String type = detectType(extension, bytes);
        String storageName = UUID.randomUUID() + "." + extension;
        Path target = resolve(storageName);
        try { Files.write(target, bytes, StandardOpenOption.CREATE_NEW); }
        catch (IOException ex) {
            deleteQuietly(storageName);
            throw new UncheckedIOException("無法儲存附件", ex);
        }
        return new StoredFile(original, storageName, type, bytes.length);
    }

    private String detectType(String extension, byte[] bytes) {
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'});
            case "png" -> startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            case "jpg", "jpeg" -> startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            default -> false;
        };
        if (!valid) throw new InvalidAttachmentException("僅支援有效的 PDF、PNG、JPG／JPEG 附件");
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private Path resolve(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.(pdf|png|jpg|jpeg)")) throw new NotFoundException();
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root)) throw new NotFoundException();
        return path;
    }

    public Resource load(String storageName) {
        Path path = resolve(storageName);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) throw new NotFoundException();
        return new PathResource(path);
    }

    public void deleteQuietly(String storageName) {
        if (storageName == null) return;
        try { Files.deleteIfExists(resolve(storageName)); }
        catch (IOException ex) { log.warn("附件清理失敗，請檢查儲存目錄：{}", storageName, ex); }
    }

    public record StoredFile(String originalFilename, String storageName, String contentType, long sizeBytes) {
        public Attachment toEntity() { return new Attachment(originalFilename, storageName, contentType, sizeBytes); }
    }
}
