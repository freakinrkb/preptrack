# Operating checklist

Before release: Windows updates applied; firewall allows only intended private access; TLS/HTTP2 verified; database not reachable externally; secure headers/rate limits checked; production frontend served statically; Spring and PostgreSQL survive restart.

Backups: automated encrypted database dump to a separate destination; daily and weekly retention; monthly restore test documented. Record service health, disk space, memory, last backup, and last successful source sync. Plan a migration only when availability or audience needs exceed the laptop's private-MVP fit.
