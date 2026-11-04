# Smart Hub Plus source comparison — 2026-11-04

Compared `plus.zip` against the IRAQhub 3.7.0 RX source tree.

- 170 Java/Kotlin files were present in `plus.zip`.
- 133 files are byte-identical to the corresponding IRAQhub 3.7.0 files.
- 28 files differ and require targeted review; replacing them wholesale would overwrite newer IRAQhub changes, including receiver/EPG/player integration.
- 9 files are absent from IRAQhub 3.7.0: `ReceiverFrontPanel.java`, `ChannelNumberEntry.java`, `TsSplice.java`, `GuideLookupIndex.java`, `UsalsLocation.java`, `LiveTsRelay.java`, `CaptureWatchdog.java`, `NativeCapture.java`, and `GuideObservationStore.java`.

The nine absent files were not copied automatically. Several are low-level tuner/capture/time-shift components whose firmware protocol, lifecycle, permissions, and hardware behavior must be validated before wiring them into the launcher. In particular, `ReceiverFrontPanel` sends a vendor-specific loopback UDP command and `UsalsLocation` constructs motor-control fields; static source presence alone does not prove that the receiver accepts those commands.

The existing IRAQhub 3.7.0 source already contains DVB/tuner, satellite, LNB profile, signal, receiver navigation, and software video activities. Its `TunerSettingsActivity` explicitly states that saved LNB profiles do not issue motor movement commands and delegates motor movement to the manufacturer's DiSEqC/USALS controls. This behavior was preserved.

Safe source correction in this pass:
- `SoftwareVideoActivity.onCreate(Bundle)` changed from `protected` to `public` to match the public visibility of `HubActivity.onCreate(Bundle)`.

No APK build was performed in this environment because Gradle/Android SDK are not installed. Hardware DVB, motor, and 4:2:2 behavior remain unverified on the target receiver.
