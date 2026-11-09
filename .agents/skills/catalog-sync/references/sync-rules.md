# Sync rules

Read alongside `docs/SOURCE_SYNC.md` for the complete plan.

Required behavior: source revision tracking; transactionally applied approved diffs; source-item provenance; archive rather than hard-delete; retained sync history; safe error reporting. For the first release, non-empty imports require admin approval. GitHub webhooks are optional and unsuitable for an app reachable only through a private mesh VPN.

Source-specific boundaries:

- Company-wise DSA repository: parse the exact pinned layout; preserve company and time-window evidence; do not invent topics or difficulty.
- System Design Primer: preserve the concepts/exercises distinction and verify current licence before content import.
- The Weekend Dev: curated, link-only records after terms review; no website mirror.
