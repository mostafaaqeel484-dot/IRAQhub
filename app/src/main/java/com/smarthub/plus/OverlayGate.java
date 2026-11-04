package com.smarthub.plus;

import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

final class OverlayGate {
    private OverlayGate() { }
    static boolean configured(SharedPreferences prefs) { return !prefs.getString("parental_pin_hash", "").isEmpty(); }
    static boolean verify(SharedPreferences prefs, String pin) {
        String expected = prefs.getString("parental_pin_hash", "");
        if (expected.isEmpty() || pin == null || !pin.matches("\\d{4}")) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),hash(prefs.getString("parental_pin_salt", ""),pin).getBytes(StandardCharsets.US_ASCII));
    }
    static void save(SharedPreferences prefs, String pin) {
        if (!pin.matches("\\d{4}")) throw new IllegalArgumentException("PIN must have four digits");
        byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes); StringBuilder salt = new StringBuilder();
        for (byte value : bytes) salt.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        prefs.edit().putString("parental_pin_salt",salt.toString()).putString("parental_pin_hash",hash(salt.toString(),pin)).apply();
    }
    private static String hash(String salt,String pin) {
        try { byte[] digest=MessageDigest.getInstance("SHA-256").digest((salt+":"+pin).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte value:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString(); }
        catch(Exception error){throw new IllegalStateException(error);}
    }
}
