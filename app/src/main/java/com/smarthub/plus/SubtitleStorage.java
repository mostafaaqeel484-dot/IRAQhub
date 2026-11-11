package com.smarthub.plus;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SubtitleStorage {
    static final String FILE = "screen_subtitles.srt";
    private SubtitleStorage() { }
    static String read(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) { return read(input); }
    }
    static String readInternal(Context context) throws IOException {
        try (InputStream input = new FileInputStream(new File(context.getFilesDir(), FILE))) { return read(input); }
    }
    private static String read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Cannot open subtitle document");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] bytes = new byte[8192]; int count;
        while ((count = input.read(bytes)) != -1) {
            if (output.size() + count > 5 * 1024 * 1024) throw new IOException("Subtitle exceeds 5 MB");
            output.write(bytes, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    static synchronized void save(Context context, String text) throws IOException {
        File temporary = new File(context.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
        File target = new File(context.getFilesDir(), FILE);
        if (!temporary.renameTo(target)) { temporary.delete(); throw new IOException("Cannot save subtitle file"); }
    }
}
