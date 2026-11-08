package com.ben10.matchaudio.online;

/**
 * JNI descriptor compatibility for the unmodified original BEN10 ARMv7 library.
 * This is deliberately not an alternative license manager or fake activation.
 * NativeSecurity's original authorization state belongs to the developer's core.
 */
public final class NativeSecurity {
    private NativeSecurity() {}
    public static native byte[] begin(byte[] data);
    public static native boolean accept(byte[] challenge, byte[] response);
    public static native byte[] decryptCore(byte[] data);
    public static native boolean initialize(byte[] payload, String identity, boolean strict);
    public static native long remainingMillis();
    public static native void revoke();
    public static native boolean valid();
}
