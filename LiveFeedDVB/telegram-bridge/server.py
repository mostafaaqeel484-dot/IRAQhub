"""Read-only HTTPS-ready Telegram Client API bridge for LiveFeed DVB Android TV.

Run behind a real TLS reverse proxy on a private URL, never expose plain HTTP
with bearer credentials over the public Internet. Access to the Telegram
account itself is local to this server, not to the APK.
"""
from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
import hmac
import os
import re
import time
from typing import Any

from fastapi import FastAPI, Header, HTTPException, status
from telethon import TelegramClient

API_ID = int(os.environ["TG_API_ID"])
API_HASH = os.environ["TG_API_HASH"]
CHANNEL = os.environ.get("TG_CHANNEL", "livefeeds1")
SESSION = os.environ.get("TG_SESSION_FILE", "./private/telegram.session")
READ_TOKEN = os.environ["BRIDGE_READ_TOKEN"]
MAX_AGE_HOURS = max(1, min(72, int(os.environ.get("BRIDGE_MAX_AGE_HOURS", "24"))))
CACHE_SECONDS = 15.0

if len(READ_TOKEN) < 32:
    raise RuntimeError("BRIDGE_READ_TOKEN must be at least 32 characters")
if not API_ID or len(API_HASH) < 15:
    raise RuntimeError("Invalid Telegram Client API credentials")

SATELLITE = re.compile(r"(?im)^\s*(?:🛰\s*)?Satellite\s*:\s*(.+)$")
FREQUENCY = re.compile(r"(?i)Frequency\s*:\s*(\d{4,5})\s*([HVLR])\s*(\d{3,6})")
EVENT = re.compile(r"(?im)^\s*(?:🎬\s*)?Event\s*:\s*(.+)$")
TRANSMISSION = re.compile(r"(?im)^\s*(?:📺\s*)?Transmission\s*:\s*(.+)$")
CATEGORY = re.compile(r"(?im)^\s*(?:🏷\s*)?Category\s*:\s*(.+)$")


def field(pattern: re.Pattern[str], body: str) -> str:
    match = pattern.search(body)
    return match.group(1).strip()[:120] if match else ""


def decode_post(message: Any) -> dict[str, Any] | None:
    body = message.message or ""
    satellite = field(SATELLITE, body)
    found = FREQUENCY.search(body)
    if not satellite or not found:
        return None
    frequency, polarization, symbol_rate = found.groups()
    transmission = field(TRANSMISSION, body)
    event = field(EVENT, body)
    msg_date = message.date or datetime.now(timezone.utc)
    if msg_date.tzinfo is None:
        msg_date = msg_date.replace(tzinfo=timezone.utc)
    return {
        "message_id": message.id,
        "satellite": satellite,
        "frequency": frequency,
        "polarization": polarization.upper(),
        "symbolRate": symbol_rate,
        "name": event or f"Ben 10 • {frequency} {polarization.upper()} {symbol_rate}",
        "codec": transmission or "Unknown",
        "chroma": "4:2:2" if "4:2:2" in transmission else ("4:2:0" if "4:2:0" in transmission else "—"),
        "category": field(CATEGORY, body),
        "status": "REPORTED",
        "source": "Telegram • Ben 10 feeds (Client API)",
        "sourceUrl": f"https://t.me/{CHANNEL}/{message.id}",
        "updated": msg_date.isoformat(),
        "imageUrl": "",
    }


@asynccontextmanager
async def lifespan(app: FastAPI):
    client = TelegramClient(SESSION, API_ID, API_HASH, request_retries=2, connection_retries=2)
    await client.connect()
    if not await client.is_user_authorized():
        await client.disconnect()
        raise RuntimeError("Telegram session not authorized: run login.py locally first")
    try:
        entity = await client.get_entity(CHANNEL)
    except Exception:
        await client.disconnect()
        raise RuntimeError("Telegram channel not accessible to authorized account") from None
    app.state.client = client
    app.state.channel = entity
    app.state.lock = asyncio.Lock()
    app.state.cached: list[dict[str, Any]] = []
    app.state.last_refresh = 0.0
    app.state.last_error = ""
    try:
        yield
    finally:
        await client.disconnect()


app = FastAPI(title="LiveFeed Telegram Bridge", version="1.0", lifespan=lifespan,
              docs_url=None, redoc_url=None, openapi_url=None)


def require_auth(header: str | None) -> None:
    if not header or not header.startswith("Bearer ") or not hmac.compare_digest(
        header[len("Bearer "):], READ_TOKEN
    ):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized")


@app.get("/v1/feeds")
async def feeds(authorization: str | None = Header(default=None)):
    require_auth(authorization)
    return await current_feed_data()


@app.get("/v1/public-feeds")
async def public_feeds():
    """Public read-only DVB metadata for end users of distributed APKs.

    No Telegram credentials, account info, raw messages or bearer tokens returned.
    Deploy behind HTTPS with CDN/rate limiting before making this publicly reachable.
    """
    return await current_feed_data()


async def current_feed_data():
    async with app.state.lock:
        now = time.monotonic()
        if now - app.state.last_refresh >= CACHE_SECONDS or not app.state.last_refresh:
            try:
                messages = await app.state.client.get_messages(app.state.channel, limit=70)
                cutoff = datetime.now(timezone.utc) - timedelta(hours=MAX_AGE_HOURS)
                fresh = []
                for message in messages:
                    date = message.date
                    if date is None:
                        continue
                    if date.tzinfo is None:
                        date = date.replace(tzinfo=timezone.utc)
                    if date < cutoff:
                        continue
                    item = decode_post(message)
                    if item is not None:
                        fresh.append(item)
                app.state.cached = fresh[:50]
                app.state.last_error = ""
            except Exception:
                # Do not leak Telegram credentials, session or phone details.
                app.state.last_error = "Telegram channel read failed; cached data may be stale"
            app.state.last_refresh = now
        return {
            "ok": not bool(app.state.last_error),
            "source": "Telegram • Ben 10 feeds (Client API)",
            "feeds": app.state.cached,
            "maxAgeHours": MAX_AGE_HOURS,
            "warning": app.state.last_error,
            "reportedNotVerifiedOnAir": True,
        }


@app.get("/health")
async def health(authorization: str | None = Header(default=None)):
    require_auth(authorization)
    return {"status": "running", "channel": "configured", "sessionAuthorized": True}
