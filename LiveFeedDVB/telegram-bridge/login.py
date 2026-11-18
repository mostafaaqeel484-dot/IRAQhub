"""One-time interactive Telegram Client API login, performed locally on the server.

Use only your own Telegram account, and only channels you're permitted to access.
NEVER send API hash, login code, 2FA password, or .session file to a chat.
"""
import getpass
import os
from pathlib import Path
from telethon.sync import TelegramClient


def main() -> None:
    api_id = int(os.environ["TG_API_ID"])
    api_hash = os.environ["TG_API_HASH"]
    session = Path(os.environ.get("TG_SESSION_FILE", "./private/telegram.session"))
    session.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(session.parent, 0o700)
    # The SQLite session contains a reusable authorization key.
    with TelegramClient(str(session), api_id, api_hash) as client:
        if not client.is_user_authorized():
            client.start(
                phone=lambda: input("Your own Telegram phone (including country code): ").strip(),
                code_callback=lambda: getpass.getpass("Telegram sign-in code (local terminal only): "),
                password=lambda: getpass.getpass("Telegram 2-step password, if required: "),
            )
        me = client.get_me()
        entity = client.get_entity(os.environ.get("TG_CHANNEL", "livefeeds1"))
        print("Authorized Telegram account. Channel available:", getattr(entity, "username", None) or "private entity")
        print("Ready. Never share the generated .session file.")
    if session.exists():
        os.chmod(session, 0o600)


if __name__ == "__main__":
    main()
