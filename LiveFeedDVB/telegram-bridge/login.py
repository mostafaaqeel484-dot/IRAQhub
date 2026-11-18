"""One-time Telegram StringSession setup on your own phone (Termux) or computer.

The StringSession is a reusable Telegram account login. Treat it like a
password. Paste it ONLY into the private TG_STRING_SESSION field of your Render
web service. Do not commit or send the string, SMS codes, 2FA, api_hash, or phone
number to GitHub, issue trackers, chats, or APK.
"""
import getpass
import os

from telethon.sync import TelegramClient
from telethon.sessions import StringSession


def main() -> None:
    print("LiveFeed DVB | One-time Telegram Client API login")
    print("Only run this locally on a trusted phone/computer, never on public CI.")
    raw_id = os.environ.get("TG_API_ID") or input("Your Telegram api_id: ").strip()
    api_id = int(raw_id)
    api_hash = os.environ.get("TG_API_HASH") or getpass.getpass("Your Telegram api_hash (hidden): ").strip()
    channel = os.environ.get("TG_CHANNEL", "livefeeds1")
    if not api_hash:
        raise ValueError("Telegram api_hash is required")

    # No local SQLite session file is used. Nothing is committed to GitHub.
    client = TelegramClient(StringSession(), api_id, api_hash)
    try:
        client.start(
            phone=lambda: input("Your Telegram phone with country code: ").strip(),
            code_callback=lambda: getpass.getpass("Telegram login code (hidden): ").strip(),
            password=lambda: getpass.getpass("Telegram 2FA password if asked (hidden): "),
        )
        entity = client.get_entity(channel)
        name = getattr(entity, "username", None)
        print("Channel access verified:", name if name else "authorized entity")
        session_string = client.session.save()
        if not session_string:
            raise RuntimeError("No authorized Telegram StringSession was returned")
        print("\n=== SECRET TG_STRING_SESSION — copy to Render private Environment ONLY ===")
        print(session_string)
        print("=== END SECRET ===")
        print("Do not publish/share this value; close the terminal after saving it in Render.")
    finally:
        client.disconnect()


if __name__ == "__main__":
    main()
