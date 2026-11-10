# Windows-hosted LLD video library

## Audience and storage

The public viewer is intended for anyone with its link. Video files remain on the Windows laptop; they are not stored on Vercel, PostgreSQL, or a cloud media bucket. The public viewer reads only published lecture metadata and video streams. Account, progress, and upload APIs remain behind the private PrepTrack site.

On Windows, `Initialize-PrepTrack.ps1` creates `Videos\PrepTrack` under the signed-in user's profile, plus a staging folder. Docker mounts that folder into the API container. PostgreSQL stores each lecture's title, module, description, order, byte size, SHA-256, generated storage key, publishing state, attribution, and rights confirmation. File bytes stay on the SSD.

The owner uploads from the private PrepTrack video-library page. Each upload requires an MP4 or WebM file, a permission-to-share confirmation, and an explicit public-publish setting. The API checks the file container signature, size, free-space reserve, and checksum; it stages the file and moves it into place before marking the database record ready. Repeated identical files are rejected. Archived lectures disappear from the public catalogue while their local file is retained.

The initial limit is 8 GB per file, with a 10 GB minimum free-space reserve. Playback uses HTTP byte ranges and reads bounded chunks from disk, so seeking works without loading a whole lecture into memory. Browser playback also depends on the video's codec being supported by that browser; the first release accepts MP4 and WebM containers.

## Public delivery boundary

The Windows Caddy service has a second loopback-only listener on port 8082. It serves only `/videos`, static frontend assets, and `/api/public/videos/**`. It returns 404 for the account app and every other API route. The owner upload page is reachable only through the private site and authenticated admin APIs.

Tailscale Funnel publishes that dedicated listener on HTTPS port 8443, separate from the private Tailscale Serve endpoint on port 443. This avoids router port forwarding and does not move video storage off the laptop. The public URL is `https://<Windows-tailnet-name>.ts.net:8443/videos`. Visitors do not need Tailscale or a PrepTrack account. The Windows laptop and internet connection must stay online for playback.

Tailscale describes Funnel as a beta feature with non-configurable bandwidth limits. It is suitable for a small audience and may become the delivery bottleneck if viewership grows. Measure upload speed and concurrent playback; move delivery to a video CDN/object store only if that becomes necessary. Funnel traffic still passes through Tailscale's relay service, but the video files remain on Windows.

## Backups and operations

The current encrypted database backup does not include video files. Back up the `Videos\PrepTrack` folder separately to an external drive or private backup destination, and verify a restore before relying on it as the only copy. Keep the media backup encrypted and separate from the Windows laptop. Do not place video files in Git, the Docker build context, or the database volume.

Monitor the host's free SSD space and uptime. The streaming process uses bounded memory, but simultaneous viewers consume the laptop's upload bandwidth. A sleeping, shut down, or disconnected Windows laptop interrupts playback.

## Telegram files

The Telegram cache has not been inspected or ingested. Add videos by selecting files through the private owner page after exporting or saving them through a user-controlled Telegram client. Only upload items the owner owns or is authorized to redistribute. No bot, scraping, or private-channel connector is part of this feature.
