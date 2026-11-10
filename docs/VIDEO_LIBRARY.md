# Windows-hosted LLD video library

## Current decision

PrepTrack can store video files on the Windows laptop's SSD, but no Telegram cache has been opened, copied, uploaded, or served. The current deployment is private to invited Tailscale devices. Before any files are served to other people, confirm that the channel owner/rights holder allows redistribution and decide whether viewers should be invited tailnet users or public internet visitors.

## Recommended first version

- Keep the current private Tailscale access boundary.
- Import only files the owner has permission to share. If rights are unclear, keep the videos for personal use and add a link to the authorized original source instead.
- Copy/export videos through a user-controlled Telegram client workflow; do not scrape private Telegram caches or automate access to the channel.
- Store video bytes on the Windows SSD in a configurable folder outside the Git checkout, PostgreSQL data, and container image. The actual path should be selected on the Windows device after checking free space.
- Store only metadata in PostgreSQL: title, module/order, duration, file size, SHA-256, generated storage key, source/attribution, rights attestation, visibility, and timestamps.
- Let an administrator import and organize files; let authenticated users play only items marked available to the current audience.
- Stream from disk with HTTP byte-range support so seeking works without loading entire videos into memory. Enforce file access through the authenticated API and use generated storage keys, never client-supplied paths.

## Storage and reliability

The upload/import flow should stage to a temporary file, enforce size and free-space limits, validate the media type, compute a checksum, then atomically move it into the media directory. A database row becomes playable only after the move succeeds. A periodic reconciliation can report missing files and orphaned files without deleting either automatically.

The media directory must be excluded from Git, Docker build context, and database backups. Add a separate encrypted backup for video files and verify a restore before relying on the laptop as the only copy. Track SSD free space and avoid filling the Windows system drive.

At 8 GB RAM, direct file streaming should keep memory use bounded. For multiple viewers, the main limits will be laptop uptime and internet upload bandwidth. The laptop must be awake and connected while a video is playing. The existing Vercel page only points to the private workspace; it does not store or stream the videos.

## Audience boundary

Tailscale Serve gives authorized tailnet devices private access. It does not make the videos public. Public viewing would require a separate public hosting and delivery design, an exposed and hardened service or video CDN, access controls, a media backup plan, and explicit redistribution rights. Do not enable public network access or Tailscale Funnel as an implicit part of the private setup.

## Suggested metadata shape

`learning_resource` (or a dedicated `media_asset`) should reference an existing LLD study item when applicable and include:

- stable UUID, title, description, module and sequence
- opaque storage key, content type, byte size, duration, SHA-256
- source attribution and rights status (`PENDING`, `PERSONAL_ONLY`, `AUTHORIZED_SHARE`)
- visibility (`OWNER_ONLY`, `SIGNED_IN`, or an explicitly approved public mode)
- processing status, uploader, created/updated timestamps

The API must refuse playback unless the resource is ready, the user is authenticated, and both visibility and rights policy allow the request. The file path is resolved only from a validated server-generated storage key under the configured media root.

## Not included yet

No Telegram integration, private channel ingestion, file upload UI, video streaming endpoint, public hosting, or third-party video publishing is implemented by this design note. Build those after rights and audience are confirmed.
