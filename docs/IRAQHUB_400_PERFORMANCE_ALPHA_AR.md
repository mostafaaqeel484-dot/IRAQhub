# IRAQhub 4.0.0-alpha1 — Tiger Lite + Auto 4:2:2 + Fast Logos

This is a **source-level preview on top of PR #24**, not an X5M hardware acceptance or production release.

## Implemented
- Automatic lightweight Tiger panels during a live DVB software 4:2:2 session. The original visual style and EPG/broadcast features remain. No changes to tuner, OEM process, BISS keys or protected firmware.
- Less frequent unchanged InfoBar redraws (~4 seconds while video priority is active), immediate redraw on channel/programme/artwork changes; hidden posters/branding references are released.
- Compact <=512 px InfoBar poster decoding; 512 px bounded channel icon decoding, 3 MiB LRU and smaller remote SVG/raster images.
- Tiger channel ListView rows now recycle their views and verify the physical service identity before asynchronous logo callbacks are displayed.
- CHMAX/EPG/film/sports jobs continue to use cached data during software 4:2:2, with online work deferred through existing PlaybackResourceMode and worker guards. FactoryProvider does not begin network EPG refresh during decoder priority.
- Online logos prefer local/verified channel sources, and reuse stored exact channel name + physical-ID-bound previously verified HTTPS URLs instead of repeating online name searches. No anonymous scraping or guessed logos.
- Auto 4:2:2 samples process CPU time, Android system low-memory flag and exposed thermal status every 8 seconds. It chooses a conservative Smooth/Balanced target for the NEXT decoder open. There is **no automatic restart of a live decoder** and no fabricated GPU/fps metrics. Both VLC and mpv use bounded thread/cache parameters. Explicit manual presets remain available.
- Dedicated CI JUnit + Debug signature + unsigned Release workflow. VersionName 4.0.0-alpha1, versionCode 40.

## Limitations & known blockers
- Live DVB MPEG-TS previously returned **zero captured bytes** on X5M. A UI/performance patch does not fix privileged firmware TS access, or guarantee 4:2:2 output.
- Correct colour, actual rendered frames, audio sync, hardware temperature and effective frame rate must be checked on the physical receiver. CPU load is APP process elapsed CPU time, not whole-chip CPU load or GPU load.
- The project cannot select protected OEM subtitle PIDs from this alpha.
- The existing production release signing key is required for installing as an update to a signed application; a CI Debug build or unsigned Release is NOT a replacement production APK.
- Runtime image savings are estimates from decode bounds, not measurements of the complete Android process PSS.

## X5M verification plan
1. Compare unchanged 3.7.7 and alpha1 on the same free-to-air H.264 High422 10-bit transport stream. Measure start latency, active TS bytes, process PSS, CPU%, actual video frames, picture colours, continuity and A/V sync for 10 minutes.
2. Repeat on HEVC 4:2:2, if an authorized playable sample exists. Do not assume hardware support.
3. Open Tiger channel list, scroll 200 rows, verify stable selected channel, logos and no wrong images after rapid navigation.
4. Press INFO repeatedly; confirm poster panels, no-signal status, progress bar, clock and EPG stay correct while monitoring CPU/fps.
5. Verify cached logos offline; then online logo matching and verified URL caching while not decoding. Network calls and logo errors must not trigger blanking unrelated content.
6. Compare Auto/Smooth/Balanced on X5M for 10 minutes. Auto applies updated target only on a subsequent decoder open/recovery; it must not cause playback restarts.
7. Check pause/resume, channel switch, audio/remote keys, standby, RAM pressure, Android TV 720p/1080p menus and app signing before merging.

## Rollback
Draft PR only, base PR #24; no merge to main, no migration of receiver private files and no changes to the signed stable application.
