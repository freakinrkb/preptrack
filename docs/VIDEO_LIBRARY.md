# Windows-hosted LLD video library

## Audience and storage

The viewer is available only to devices connected to the owner's Tailscale network. Video files remain on the Windows laptop; they are not stored on Vercel, PostgreSQL, or a cloud media bucket. The read-only viewer shows published lecture metadata and streams. Account, progress, and upload APIs remain behind the private PrepTrack site.

On Windows, `Initialize-PrepTrack.ps1` creates `Videos\PrepTrack` under the signed-in user's profile, plus a staging folder. Docker mounts that folder into the API container. PostgreSQL stores each lecture's title, module, description, order, byte size, SHA-256, generated storage key, publishing state, attribution, and rights confirmation. File bytes stay on the SSD.

The owner uploads from the private PrepTrack video-library page. Each upload requires an MP4 or WebM file, a permission-to-share confirmation, and an explicit visibility setting. The API checks the file container signature, size, free-space reserve, and checksum; it stages the file and moves it into place before marking the database record ready. Repeated identical files are rejected. Archived lectures disappear from the viewer while their local file is retained.

The initial limit is 8 GB per file, with a 10 GB minimum free-space reserve. Playback uses HTTP byte ranges and reads bounded chunks from disk, so seeking works without loading a whole lecture into memory. Browser playback also depends on the video's codec being supported by that browser; the first release accepts MP4 and WebM containers.

## Tailscale-only delivery boundary

The private Tailscale Serve address on HTTPS port 443 serves the app and its `/videos` route only to tailnet devices. The signed-in owner upload page uses authenticated admin APIs. A second loopback-only listener on port 8082 serves only `/videos`, static frontend assets, and `/api/public/videos/**`; it returns 404 for the account app and every other API route.

Tailscale Funnel is disabled by the owner's choice. Tailscale's enable flow grants every tailnet member the ability to use Funnel, which is broader than this video library's single listener. Router port forwarding is also disabled. There is no public internet URL; use `https://<Windows-tailnet-name>.ts.net/videos` from a connected device. No network-facing settings were changed.

The Windows laptop and its Tailscale connection must stay online for playback. The separate port 8082 remains bound to loopback and is not exposed to the internet or other tailnet devices.

## Backups and operations

The current encrypted database backup does not include video files. Back up the `Videos\PrepTrack` folder separately to an external drive or private backup destination, and verify a restore before relying on it as the only copy. Keep the media backup encrypted and separate from the Windows laptop. Do not place video files in Git, the Docker build context, or the database volume.

Monitor the host's free SSD space and uptime. The streaming process uses bounded memory, but simultaneous viewers consume the laptop's upload bandwidth. A sleeping, shut down, or disconnected Windows laptop interrupts playback.

## Telegram files

The Telegram cache has not been inspected or ingested. Add videos by selecting files through the private owner page after exporting or saving them through a user-controlled Telegram client. Only upload items the owner owns or is authorized to redistribute. No bot, scraping, or private-channel connector is part of this feature.
