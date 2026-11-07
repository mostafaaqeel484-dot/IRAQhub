package com.smarthub.plus;

/** Tracks frame-counter progress only; never treats inaccessible VDEC telemetry as a decode failure. */
final class DvbVideoHealth {
    enum State { NO_TELEMETRY, WAITING, ADVANCING, STALLED }
    static final long STALL_AFTER_MS = 12_000L;
    private long lastFrames = -1;
    private long lastMovementMs = -1;

    void reset() {
        lastFrames = -1;
        lastMovementMs = -1;
    }

    State sample(long monotonicMs, long frames) {
        if (frames < 0) {
            reset();
            return State.NO_TELEMETRY;
        }
        // A count decrease means the vendor decoder was reset. Begin a fresh grace period.
        if (lastFrames < 0 || frames < lastFrames || monotonicMs < lastMovementMs) {
            lastFrames = frames;
            lastMovementMs = monotonicMs;
            return State.WAITING;
        }
        if (frames > lastFrames) {
            lastFrames = frames;
            lastMovementMs = monotonicMs;
            return State.ADVANCING;
        }
        return monotonicMs - lastMovementMs >= STALL_AFTER_MS ? State.STALLED : State.WAITING;
    }
}
