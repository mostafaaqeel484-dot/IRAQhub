# Telegram Client API bridge for publicly distributed LiveFeed DVB

**Status: infrastructure template, NOT deployed.** End users do not need Telegram,
API keys, a login, or server configuration. Telegram feeds become available
automatically to already installed APKs once the application maintainer
deploys the bridge and enables the repository-controlled public configuration.

## How it works

Android app → reads a tiny public config file from this GitHub repository
(every ~5 minutes) → connects via HTTPS to `/v1/public-feeds`
(every ~10 seconds while monitoring is active) → merges DVB metadata with
Live-Feed.net and Satelliweb → sends deduplicated TV notifications.

The **5-second** app monitoring loop and **10-second** top/bottom notification
duration remain unchanged. Telegram server cache is **15 seconds**, so
Telegram posts are not guaranteed to arrive within five seconds.

## What the publisher (one time) must do

1. Obtain `api_id` and `api_hash` for their own Telegram developer application
   at <https://my.telegram.org> (see
   <https://core.telegram.org/api/obtaining_api_id>).
2. Set up a persistent Linux VPS / hosted server with Python 3.11+, outgoing
   Telegram access, and a domain with valid HTTPS. The account must have lawful
   permission to see `@livefeeds1`. Consider permission from the channel owner
   before redistributing posts to an unlimited public audience.
3. Create the Python virtual environment and install dependencies:

   ```sh
   cd LiveFeedDVB/telegram-bridge
   python3 -m venv .venv
   . .venv/bin/activate
   python -m pip install -r requirements.txt
   cp .env.example .env
   chmod 600 .env
   python3 -c 'import secrets; print(secrets.token_urlsafe(40))'
   ```

   Set `TG_API_ID`, `TG_API_HASH`, `BRIDGE_READ_TOKEN`, and the
   `TG_SESSION_FILE` location in **local .env**. Never commit these secrets.
   The read token is an admin-only token, **not used by public Android users**.
4. Run the one-time Telegram authorization **locally**:

   ```sh
   set -a
   . ./.env
   set +a
   python login.py
   ```

   Enter code/2FA only on your own trusted server, never in APK, public
   GitHub files, or this chat. Treat `*.session` as a password.
5. Start backend loopback-only:

   ```sh
   uvicorn server:app --host 127.0.0.1 --port 8090
   ```

   Put a reverse proxy with valid TLS (Nginx, Caddy, etc.) in front of it.
   Protect the public `/v1/public-feeds` endpoint with CDN caching and
   per-IP rate limiting for public distribution. Restrict `/health` and
   `/v1/feeds` to authorized administrative clients. Never expose port 8090.
6. Verify the public endpoint before activating the app:

   ```sh
   curl -f https://feeds.example.com/v1/public-feeds
   ```

   A valid JSON response must contain `ok: true` and a `feeds` array.
7. Update **only** `LiveFeedDVB/telegram-bridge/public-config.json` in this
   repository branch, e.g.:

   ```json
   {
     "version": 1,
     "enabled": true,
     "url": "https://feeds.example.com"
   }
   ```

   `feeds.example.com` is an **example**, not a deployed hostname. Do not
   enable the config until an actual HTTPS server is accessible. APKs
   distributed from this branch will discover the updated endpoint without
   the end user entering anything.

## Safety / behavior

- Only DVB fields from authorized channel posts are published; user/account
  credentials and MTProto session never leave the server.
- The public feed endpoint is **anonymous read-only**, so it must be rate limited
  at the reverse proxy. Telegram's account-wide rate limits still apply.
- A server failure appears in the APK's Sources diagnostics; other feed
  providers continue operating.
- Telegram post data is marked **REPORTED** (not verified ON AIR).
- If the publisher has not set up an authorized server, Telegram will remain
  **unavailable**. No APK can safely bypass this requirement by embedding the
  developer's Telegram session.
- Telegram's terms and third-party channel content permissions still apply.

## Bridge server private endpoints

`GET /v1/feeds` and `GET /health` require
`Authorization: Bearer <BRIDGE_READ_TOKEN>` and are for operator diagnostics.
The public client uses only `GET /v1/public-feeds`.
