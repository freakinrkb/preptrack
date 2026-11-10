package dev.preptrack.media;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.RandomAccessFile;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;

@RestController
public class VideoLibraryController {
    private final VideoLibraryService videos;

    public VideoLibraryController(VideoLibraryService videos) {
        this.videos = videos;
    }

    @GetMapping("/api/public/videos")
    List<VideoLibraryService.PublicVideoView> publicLibrary() {
        return videos.publicLibrary();
    }

    @GetMapping("/api/public/videos/{id}/stream")
    ResponseEntity<StreamingResponseBody> publicStream(@PathVariable UUID id, HttpServletRequest request) {
        return stream(id, false, true, request);
    }

    @GetMapping("/api/media/videos")
    VideoLibraryService.VideoListResponse signedInLibrary(Authentication authentication) {
        return videos.listForSignedInUser(authentication);
    }

    @GetMapping("/api/media/videos/{id}/stream")
    ResponseEntity<StreamingResponseBody> signedInStream(@PathVariable UUID id, Authentication authentication, HttpServletRequest request) {
        boolean admin = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return stream(id, admin, false, request);
    }

    @PostMapping(path="/api/admin/media/videos", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    VideoLibraryService.VideoView upload(
        Authentication authentication,
        @RequestParam("file") MultipartFile file,
        @RequestParam("title") String title,
        @RequestParam("module") String module,
        @RequestParam(value="description", defaultValue="") String description,
        @RequestParam(value="attribution", defaultValue="") String attribution,
        @RequestParam(value="sortOrder", defaultValue="0") int sortOrder,
        @RequestParam(value="publish", defaultValue="true") boolean publish,
        @RequestParam(value="rightsConfirmed", defaultValue="false") boolean rightsConfirmed
    ) {
        return videos.upload(authentication, file, title, module, description, attribution,
            sortOrder, publish, rightsConfirmed);
    }

    @PostMapping("/api/admin/media/videos/{id}/archive")
    ResponseEntity<Void> archive(@PathVariable UUID id) {
        videos.setPublished(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/media/videos/{id}/publish")
    ResponseEntity<Void> publish(@PathVariable UUID id) {
        videos.setPublished(id, true);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<StreamingResponseBody> stream(UUID id, boolean administratorOnly, boolean publicOnly, HttpServletRequest request) {
        VideoLibraryService.StreamAsset asset = videos.streamAsset(id, administratorOnly, publicOnly);
        String etag = "\"" + asset.sha256() + "\"";
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        headers.set("Accept-Ranges", "bytes");
        headers.setCacheControl(publicOnly ? "public, max-age=3600" : "private, max-age=300");
        headers.setContentType(MediaType.parseMediaType(asset.mediaType()));
        headers.setContentDisposition(ContentDisposition.inline().filename(asset.id() + extension(asset.storageKey())).build());
        headers.set("X-Content-Type-Options", "nosniff");

        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (etagMatches(ifNoneMatch, etag))
            return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        String ifRange = request.getHeader(HttpHeaders.IF_RANGE);
        if (ifRange != null && !ifRange.isBlank() && !ifRange.equals(etag)) rangeHeader = null;

        ByteRange range = null;
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                range = parseRange(rangeHeader, asset.byteSize());
            } catch (IllegalArgumentException e) {
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + asset.byteSize());
                return new ResponseEntity<>(null, headers, REQUESTED_RANGE_NOT_SATISFIABLE);
            }
        }

        long start = range == null ? 0 : range.start();
        long end = range == null ? asset.byteSize() - 1 : range.end();
        long contentLength = end - start + 1;
        if (range != null) headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + asset.byteSize());
        headers.setContentLength(contentLength);

        StreamingResponseBody body = output -> {
            try (RandomAccessFile video = new RandomAccessFile(asset.path().toFile(), "r")) {
                video.seek(start);
                byte[] buffer = new byte[64 * 1024];
                long remaining = contentLength;
                while (remaining > 0) {
                    int read = video.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) break;
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        };
        return new ResponseEntity<>(body, headers, range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    }

    private static ByteRange parseRange(String header, long size) {
        if (!header.startsWith("bytes=") || header.indexOf(',') >= 0 || size <= 0)
            throw new IllegalArgumentException("Unsupported range.");
        String value = header.substring("bytes=".length()).trim();
        int hyphen = value.indexOf('-');
        if (hyphen < 0 || value.indexOf('-', hyphen + 1) >= 0) throw new IllegalArgumentException("Invalid range.");
        String first = value.substring(0, hyphen).trim();
        String last = value.substring(hyphen + 1).trim();
        try {
            long start;
            long end;
            if (first.isEmpty()) {
                long suffixLength = Long.parseLong(last);
                if (suffixLength <= 0) throw new IllegalArgumentException("Invalid suffix range.");
                start = Math.max(0, size - suffixLength);
                end = size - 1;
            } else {
                start = Long.parseLong(first);
                end = last.isEmpty() ? size - 1 : Long.parseLong(last);
                if (start < 0 || start >= size || end < start) throw new IllegalArgumentException("Range is outside the file.");
                end = Math.min(end, size - 1);
            }
            return new ByteRange(start, end);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid range.");
        }
    }

    private static boolean etagMatches(String header, String etag) {
        if (header == null) return false;
        for (String value : header.split(",")) {
            String candidate = value.trim();
            if (candidate.equals("*") || candidate.equals(etag)) return true;
        }
        return false;
    }

    private static String extension(String key) {
        return key.endsWith(".webm") ? ".webm" : ".mp4";
    }

    private record ByteRange(long start, long end) { }
}
