# Security baseline

Password hashes use a current adaptive encoder (Argon2id preferred where supported). Cookies are Secure, HttpOnly, and SameSite; state-changing browser requests receive CSRF protection. Rate-limit authentication and protect password reset/invite tokens by hashing and expiry.

Validate, size-limit, and safely render notes. Never log credentials, session IDs, API tokens, or full private notes. Secrets stay out of source control. PostgreSQL binds privately; authorization is tested so one account cannot read/write another's learning records.
