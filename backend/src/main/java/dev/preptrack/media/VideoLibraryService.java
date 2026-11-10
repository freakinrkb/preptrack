package dev.preptrack.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.INSUFFICIENT_STORAGE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Service
public class VideoLibraryService {
    private final JdbcTemplate jdbc;
    private final Path mediaRoot;
    private final Path stagingRoot;
    private final long maxFileBytes;
    private final long reservedFreeBytes;

    public VideoLibraryService(
        JdbcTemplate jdbc,
        @Value("${preptrack.media.root}") String mediaRoot,
        @Value("${preptrack.media.staging-root}") String stagingRoot,
        @Value("${preptrack.media.max-file-bytes}") long maxFileBytes,
        @Value("${preptrack.media.reserve-bytes}") long reservedFreeBytes
    ) {
        this.jdbc = jdbc;
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
        this.stagingRoot = Path.of(stagingRoot).toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
        this.reservedFreeBytes = reservedFreeBytes;
        try {
            Files.createDirectories(this.mediaRoot);
            Files.createDirectories(this.stagingRoot);
            cleanAbandonedUploads();
        } catch (IOException e) {
            throw new IllegalStateException("The configured video storage folder is not available.", e);
        }
    }

    public VideoListResponse listForSignedInUser(Authentication authentication) {
        boolean admin = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        List<VideoView> items = jdbc.query("""
            SELECT id,title,module,description,sort_order,byte_size,duration_seconds,
                   original_filename,status,created_at
            FROM video_asset
            WHERE status='PUBLISHED' OR ?
            ORDER BY module,sort_order,title
            """, (rs, row) -> new VideoView(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("module"),
                rs.getString("description"), rs.getInt("sort_order"), rs.getLong("byte_size"),
                nullableLong(rs, "duration_seconds"), rs.getString("original_filename"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant()
            ), admin);
        SpaceInfo space = admin ? spaceInfo() : null;
        return new VideoListResponse(items, space);
    }

    public List<PublicVideoView> publicLibrary() {
        return jdbc.query("""
            SELECT id,title,module,description,sort_order,duration_seconds,source_attribution
            FROM video_asset
            WHERE status='PUBLISHED'
            ORDER BY module,sort_order,title
            """, (rs, row) -> new PublicVideoView(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("module"),
                rs.getString("description"), rs.getInt("sort_order"),
                nullableLong(rs, "duration_seconds"), rs.getString("source_attribution")
            ));
    }

    @Transactional
    public VideoView upload(
        Authentication authentication,
        MultipartFile file,
        String title,
        String module,
        String description,
        String attribution,
        int sortOrder,
        boolean publish,
        boolean rightsConfirmed
    ) {
        if (!rightsConfirmed)
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "Confirm that you have permission to share this video.");
        String cleanTitle = requiredText(title, 200, "Title");
        String cleanModule = requiredText(module, 120, "Module");
        String cleanDescription = cleanText(description, 1200);
        String cleanAttribution = cleanText(attribution, 500);
        if (sortOrder < 0 || sortOrder > 100_000)
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "Order must be between 0 and 100000.");
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "Choose an MP4 or WebM video file.");
        if (file.getSize() > maxFileBytes)
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "This video is larger than the configured upload limit.");

        String extension = extension(file.getOriginalFilename());
        String mediaType = extension.equals(".mp4") ? "video/mp4" : "video/webm";
        UUID id = UUID.randomUUID();
        Path staged = null;
        Path destination = null;
        try {
            FileStore store = Files.getFileStore(mediaRoot);
            if (store.getUsableSpace() - file.getSize() < reservedFreeBytes)
                throw new ResponseStatusException(INSUFFICIENT_STORAGE,
                    "There is not enough free space for this video while keeping the storage reserve.");
            staged = Files.createTempFile(stagingRoot, "upload-", ".part");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            long actualSize = Files.size(staged);
            if (actualSize == 0 || actualSize != file.getSize())
                throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "The uploaded file could not be verified.");
            validateContainer(staged, extension);
            String sha256 = sha256(staged);
            Integer duplicates = jdbc.queryForObject("SELECT count(*) FROM video_asset WHERE sha256=?", Integer.class, sha256);
            if (duplicates != null && duplicates > 0)
                throw new ResponseStatusException(CONFLICT, "This exact video is already in the library.");

            String storageKey = id + extension;
            destination = resolveStorageKey(storageKey);
            try {
                Files.move(staged, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(staged, destination);
            }
            Path storedPath = destination;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteQuietly(storedPath);
                }
            });

            UUID ownerId = jdbc.queryForObject("SELECT id FROM user_account WHERE email=?", UUID.class, authentication.getName());
            String status = publish ? "PUBLISHED" : "ARCHIVED";
            jdbc.update("""
                INSERT INTO video_asset(id,title,module,description,sort_order,storage_key,original_filename,
                    media_type,byte_size,sha256,status,source_attribution,uploaded_by,rights_confirmed_by,rights_confirmed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())
                """, id, cleanTitle, cleanModule, cleanDescription, sortOrder, storageKey,
                safeFilename(file.getOriginalFilename()), mediaType, actualSize, sha256, status,
                cleanAttribution, ownerId, ownerId);
            return findAdminView(id);
        } catch (ResponseStatusException e) {
            deleteQuietly(staged);
            if (!TransactionSynchronizationManager.isSynchronizationActive()) deleteQuietly(destination);
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteQuietly(staged);
            if (!TransactionSynchronizationManager.isSynchronizationActive()) deleteQuietly(destination);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "The video could not be saved to the Windows library.");
        } catch (RuntimeException e) {
            deleteQuietly(staged);
            if (!TransactionSynchronizationManager.isSynchronizationActive()) deleteQuietly(destination);
            throw e;
        }
    }

    @Transactional
    public void setPublished(UUID id, boolean published) {
        int changed = jdbc.update("UPDATE video_asset SET status=?,updated_at=now() WHERE id=?",
            published ? "PUBLISHED" : "ARCHIVED", id);
        if (changed == 0) throw new ResponseStatusException(NOT_FOUND, "Video not found.");
    }

    public StreamAsset streamAsset(UUID id, boolean administratorOnly, boolean publicOnly) {
        List<StreamAsset> rows = jdbc.query("""
            SELECT id,storage_key,media_type,byte_size,sha256,status
            FROM video_asset WHERE id=? AND (status='PUBLISHED' OR ?)
            """, (rs, row) -> new StreamAsset(
                rs.getObject("id", UUID.class), rs.getString("storage_key"), rs.getString("media_type"),
                rs.getLong("byte_size"), rs.getString("sha256").trim(), rs.getString("status")
            ), id, administratorOnly && !publicOnly);
        if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Video not found.");
        StreamAsset asset = rows.getFirst();
        Path path = resolveStorageKey(asset.storageKey());
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) != asset.byteSize())
                throw new ResponseStatusException(NOT_FOUND, "Video file is not available.");
        } catch (IOException e) {
            throw new ResponseStatusException(NOT_FOUND, "Video file is not available.");
        }
        return new StreamAsset(asset.id(), asset.storageKey(), asset.mediaType(), asset.byteSize(), asset.sha256(), asset.status(), path);
    }

    private VideoView findAdminView(UUID id) {
        return jdbc.queryForObject("""
            SELECT id,title,module,description,sort_order,byte_size,duration_seconds,
                   original_filename,status,created_at
            FROM video_asset WHERE id=?
            """, (rs, row) -> new VideoView(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("module"),
                rs.getString("description"), rs.getInt("sort_order"), rs.getLong("byte_size"),
                nullableLong(rs, "duration_seconds"), rs.getString("original_filename"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant()
            ), id);
    }

    private SpaceInfo spaceInfo() {
        try {
            long used = jdbc.queryForObject("SELECT coalesce(sum(byte_size),0) FROM video_asset", Long.class);
            FileStore store = Files.getFileStore(mediaRoot);
            return new SpaceInfo(used, store.getUsableSpace(), maxFileBytes, reservedFreeBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Video storage capacity could not be read.", e);
        }
    }

    private Path resolveStorageKey(String key) {
        Path resolved = mediaRoot.resolve(key).normalize();
        if (!resolved.startsWith(mediaRoot)) throw new ResponseStatusException(NOT_FOUND, "Video not found.");
        return resolved;
    }

    private void cleanAbandonedUploads() throws IOException {
        Instant cutoff = Instant.now().minusSeconds(24 * 60 * 60);
        try (var files = Files.list(stagingRoot)) {
            files.filter(path -> path.getFileName().toString().startsWith("upload-") && path.getFileName().toString().endsWith(".part"))
                .filter(path -> {
                    try { return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff); }
                    catch (IOException e) { return false; }
                })
                .forEach(VideoLibraryService::deleteQuietly);
        }
    }

    private static void validateContainer(Path path, String extension) throws IOException {
        byte[] signature;
        try (InputStream input = Files.newInputStream(path)) { signature = input.readNBytes(12); }
        boolean mp4 = extension.equals(".mp4") && signature.length >= 8 &&
            signature[4] == 'f' && signature[5] == 't' && signature[6] == 'y' && signature[7] == 'p';
        boolean webm = extension.equals(".webm") && signature.length >= 4 &&
            (signature[0] & 0xff) == 0x1a && (signature[1] & 0xff) == 0x45 &&
            (signature[2] & 0xff) == 0xdf && (signature[3] & 0xff) == 0xa3;
        if (!mp4 && !webm)
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "The file content does not match an MP4 or WebM video.");
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String extension(String original) {
        String filename = safeFilename(original).toLowerCase(Locale.ROOT);
        if (filename.endsWith(".mp4")) return ".mp4";
        if (filename.endsWith(".webm")) return ".webm";
        throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "Only MP4 and WebM videos can be played in the library.");
    }

    private static String safeFilename(String original) {
        String value = original == null ? "lecture-video" : original.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (value.isBlank()) value = "lecture-video";
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private static String requiredText(String value, int maxLength, String label) {
        String clean = cleanText(value, maxLength);
        if (clean.isBlank()) throw new ResponseStatusException(UNPROCESSABLE_ENTITY, label + " is required.");
        return clean;
    }

    private static String cleanText(String value, int maxLength) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > maxLength)
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "A video detail is longer than allowed.");
        return clean;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record PublicVideoView(UUID id, String title, String module, String description,
                                  int sortOrder, Long durationSeconds, String sourceAttribution) { }
    public record VideoView(UUID id, String title, String module, String description, int sortOrder,
                            long byteSize, Long durationSeconds, String fileName, String status, Instant uploadedAt) { }
    public record SpaceInfo(long usedBytes, long availableBytes, long maxFileBytes, long reservedFreeBytes) { }
    public record VideoListResponse(List<VideoView> items, SpaceInfo storage) { }
    public record StreamAsset(UUID id, String storageKey, String mediaType, long byteSize,
                              String sha256, String status, Path path) {
        StreamAsset(UUID id, String storageKey, String mediaType, long byteSize, String sha256, String status) {
            this(id, storageKey, mediaType, byteSize, sha256, status, null);
        }
    }
}
