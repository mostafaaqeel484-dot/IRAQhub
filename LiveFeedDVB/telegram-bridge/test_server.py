import os
import unittest
from datetime import datetime, timezone
from types import SimpleNamespace

os.environ.setdefault("TG_API_ID", "123456")
os.environ.setdefault("TG_API_HASH", "0123456789abcdef0123456789abcdef")
os.environ.setdefault("BRIDGE_READ_TOKEN", "test-only-dummy-token-no-real-credentials-12345")
os.environ.setdefault("TG_CHANNEL", "livefeeds1")

from server import decode_post

class FeedExtractionTest(unittest.TestCase):
    def test_satellite_post(self):
        text = (
            "📡 Satellite Feed\n"
            "🛰 Satellite: Eutelsat 16A (16.0°E)\n"
            "📶 Frequency: 12737 H 2666\n"
            "⚙ FEC: -\n"
            "🏷 Category: Other\n"
            "📺 Transmission: 🔒 MPEG-4 4:2:2\n"
            "🎬 Event: Religious by TRWAM\n"
        )
        post = SimpleNamespace(id=321, message=text, date=datetime(2026, 11, 18, tzinfo=timezone.utc))
        result = decode_post(post)
        self.assertIsNotNone(result)
        self.assertEqual(result["frequency"], "12737")
        self.assertEqual(result["polarization"], "H")
        self.assertEqual(result["symbolRate"], "2666")
        self.assertEqual(result["name"], "Religious by TRWAM")
        self.assertEqual(result["chroma"], "4:2:2")
        self.assertEqual(result["status"], "REPORTED")
        self.assertEqual(result["sourceUrl"], "https://t.me/livefeeds1/321")

    def test_non_satellite_posts_are_ignored(self):
        self.assertIsNone(decode_post(SimpleNamespace(
            id=1, message="Watch today's football matches", date=datetime.now(timezone.utc)
        )))

    def test_no_user_content_leaks_into_metadata(self):
        result = decode_post(SimpleNamespace(
            id=9, message="Satellite: Hellas Sat (39.0E)\nFrequency: 11490 V 1800\nEvent: Beirut skyline",
            date=datetime.now(timezone.utc)
        ))
        self.assertEqual(result["name"], "Beirut skyline")
        self.assertNotIn("message", result)


if __name__ == "__main__":
    unittest.main()
