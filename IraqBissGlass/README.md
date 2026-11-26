# IraqBISS Glass TV — v0.4 Night Scenes & Dock

Standalone native Android TV satellite-feed metadata app for 32-bit Amlogic receivers.

### Look and interaction
- Night Scenes: three original, low-cost dark landscapes with subtle depth (change from Settings).
- tvOS-style, bottom-positioned translucent Liquid Glass dock, five remote-focus filters.
- Clock and Arabic date over a featured-feed hero. Featured feed shows the original post image when available.
- Side-scrolling source-image shelf with native focus zoom and favorite indicators.
- Source diagnostics, custom feeds, 4:2:2/FTA/sports filters, background notifications and top/bottom image popups unchanged.

### Inspiration and credits
The visual reference is [techgenius1/tvOS-Launcher](https://github.com/techgenius1/tvOS-Launcher), copyright 2026 techgenius, licensed under Apache 2.0. Its design was adapted as independently written feed UI elements (no launcher internals, device-manager, proprietary media, or other apps bundled). See [ATTRIBUTION.md](ATTRIBUTION.md).

The new UI is not affiliated with or endorsed by Apple or techgenius1.

### Build
Run `.github/workflows/iraqbiss-glass-apk.yml` on the branch `feature/iraqbiss-feed-glass-20261126`; GitHub Releases should publish `IraqBISS-tvOS-NightScenes-v0.4.apk`.

Install with `adb install -r IraqBISS-tvOS-NightScenes-v0.4.apk`. Android overlay permission must be enabled for popups above TV.

Note: website feed data may fail due to source access/markup. Frequency reports are not confirmed on-air DVB signals; this app is not a satellite tuner or BISS descrambler. Testing on real Amlogic X5M and OEM TV surfaces remains necessary.
