package com.ben10.matchaudio;

/**
 * ABI-level JNI bridge with the exact exported symbol names in BEN10 FTA 1.0.7.
 * Deliberately does not spoof authorization, the BEN10 application identity,
 * or initialize the protected core. Native library loading may fail if
 * OEM device permission or vendor authorization is missing.
 */
public final class FtaDvbNative {
    private static final boolean LOADED;
    private static final String LOAD_STATUS;
    static {
        boolean ok = false;
        String result;
        try {
            System.loadLibrary("ben10_security");
            System.loadLibrary("ben10_fta");
            ok = true;
            result = "ARMv7 FTA JNI libraries loaded";
        } catch (Throwable t) {
            result = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        LOADED = ok;
        LOAD_STATUS = result;
    }
    public FtaDvbNative() {}
    public static boolean isLoaded() { return LOADED; }
    public static String loadStatus() { return LOAD_STATUS; }
    public native int detectActiveAudioPid();
    public native void resetAudioTap();
    public native int prepareFreshCapture(int audioPid);
    public native long captureAudioTs(int audioPid, String outputPath, int captureMillis);
    public native long getCaptureFirstDataMs();
    public native long getTapGeneration();
    public native int getAttachedPid();
}
