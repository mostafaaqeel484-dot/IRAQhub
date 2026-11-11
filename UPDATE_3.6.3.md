# IRAQhub 3.6.3

## Runtime video decoder diagnostics
- Records the actual Media3 decoder name and initialization duration.
- Records the selected input format and detected codec profile hint.
- Records first rendered frame, frame-processing telemetry, and video codec errors.
- Adds these session details to Device Compatibility.
- Distinguishes profile detection from actual decoder initialization and first-frame presentation.
- Retains memory-aware buffering and decoder fallback from 3.6.2.

## 4:2:2 status semantics
A detected 4:2:2 codec string is not proof of hardware support. A decoder initialization is not proof that frames were rendered. A first rendered frame is not proof of sustained, correct 4:2:2 decoding. Verify with a real stream on the target receiver.

Version: 3.6.3 · versionCode: 26
