# IRAQhub RX6 original · user-controlled poster sources

This feature continues the original-source RX6 branch with existing CHMAX+EPG
improvements. No file in RX6_ORIGINAL_ASSETS.sha256 or RX6_ORIGINAL_UI.sha256
is changed. The existing ArtworkSourcesActivity is extended, not replaced.

Settings: Settings -> OSD -> Fix 12 Artwork Sources.
Independently enable/disable TMDB, OpenPosterDB (OPDB), RatingPosterDB
(RPDB), BetterPosters, Archive.org, TVmaze, TheSportsDB and Wikimedia Commons.

Controls are stored in smarthub SharedPreferences, take effect without a
restart and also gate each provider's new background requests. Previously
downloaded cached images are retained offline; turning a provider off does not
erase its saved posters. Exact IMDb IDs are required for OPDB and RPDB;
shared demo keys are rate-limited. OMDb Poster API is not enabled because it
requires its own private patron key (not an RPDB key).

Package: com.smarthub.plus.rx6posterctl, a new side-by-side debug preview.
CI validates original skin/artwork hashes, JVM tests, ARMv7 ABI and APK signing.
Factory EPG, CHMAX and live image results still need receiver verification.
