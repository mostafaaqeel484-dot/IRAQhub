package com.smarthub.plus;

import android.net.Uri;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

/**
 * Explicit software-decoding fallback for IPTV/file streams.
 * Hardware decoding is disabled intentionally. This activity does not claim
 * hardware 4:2:2 support and does not control the OEM DVB tuner.
 */
public final class SoftwareVideoActivity extends HubActivity {
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private TextView status;
    private boolean released;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        videoLayout = new VLCVideoLayout(this);
        root.addView(videoLayout, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setText(tx("تهيئة فك الترميز البرمجي…", "Preparing software decoder…"));
        status.setTextColor(0xffffffff);
        status.setBackgroundColor(0xb0000000);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(status, statusParams);
        TextView tracks = new TextView(this);
        tracks.setText(tx("مسارات الصوت والترجمة", "Audio / subtitle tracks"));
        tracks.setTextColor(0xffffffff);
        tracks.setBackgroundColor(0xb0000000);
        tracks.setPadding(dp(16), dp(10), dp(16), dp(10));
        tracks.setFocusable(true);
        tracks.setOnClickListener(v -> showTrackMenu());
        FrameLayout.LayoutParams trackParams =
                new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        root.addView(tracks, trackParams);
        setContentView(root);

        String uri = getIntent().getStringExtra("media_uri");
        if (uri == null || uri.trim().isEmpty()) {
            status.setText(tx("رابط الوسائط غير موجود", "Media URI is missing"));
            return;
        }
        open(uri);
    }

    private void open(String source) {
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--avcodec-hw=none");
            options.add("--aout=audiotrack");
            options.add("--no-video-title-show");
            options.add("--no-drop-late-frames");
            options.add("--no-skip-frames");
            options.add("--deinterlace=0");
            options.add(":demux=avformat");
            options.add(":file-caching=1000");
            options.add(":network-caching=1000");

            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);
            mediaPlayer.setEventListener(event -> runOnUiThread(() -> {
                if (released || status == null) return;
                switch (event.type) {
                    case MediaPlayer.Event.Opening:
                        status.setText(tx("فتح البث…", "Opening stream…"));
                        break;
                    case MediaPlayer.Event.Buffering:
                        status.setText(tx("تخزين مؤقت…", "Buffering…"));
                        break;
                    case MediaPlayer.Event.Playing:
                        status.setText(tx("تشغيل برمجي عبر LibVLC", "Software playback · LibVLC"));
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        status.setText(tx("فشل فك الترميز أو قراءة البث", "Decoder or stream error"));
                        break;
                    case MediaPlayer.Event.EndReached:
                        status.setText(tx("انتهى البث", "Stream ended"));
                        break;
                    default:
                        break;
                }
            }));

            Media media = new Media(libVLC, Uri.parse(source));
            media.setHWDecoderEnabled(false, false);
            media.addOption(":avcodec-hw=none");
            media.addOption(":file-caching=1000");
            media.addOption(":network-caching=1000");
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
        } catch (Throwable failure) {
            if (status != null) {
                String detail = failure.getMessage();
                status.setText(tx("تعذر تشغيل LibVLC", "LibVLC startup failed")
                        + (detail == null ? "" : " · " + detail));
            }
        }
    }

    private void showTrackMenu() {
        if (mediaPlayer == null || released || !mediaPlayer.hasMedia()) {
            status.setText(tx("انتظر حتى يبدأ البث", "Wait until playback starts"));
            return;
        }
        String[] choices = {
                tx("مسار الصوت", "Audio track"),
                tx("مسار الترجمة", "Subtitle track")
        };
        new AlertDialog.Builder(this)
                .setTitle(tx("إعدادات المسارات", "Track selection"))
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) showAudioTracks();
                    else showSubtitleTracks();
                })
                .setNegativeButton(tx("إغلاق", "Close"), null)
                .show();
    }

    private void showAudioTracks() {
        MediaPlayer.TrackDescription[] tracks;
        try { tracks = mediaPlayer.getAudioTracks(); }
        catch (Throwable error) { tracks = null; }
        if (tracks == null || tracks.length == 0) {
            status.setText(tx("لا توجد مسارات صوت متاحة", "No audio tracks available"));
            return;
        }
        final MediaPlayer.TrackDescription[] audioTracks = tracks;
        String[] labels = new String[audioTracks.length];
        int selected = -1;
        int current = mediaPlayer.getAudioTrack();
        for (int i = 0; i < audioTracks.length; i++) {
            labels[i] = audioTracks[i].name == null ? ("Audio " + audioTracks[i].id) : audioTracks[i].name;
            if (audioTracks[i].id == current) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(tx("اختر مسار الصوت", "Select audio track"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    boolean ok = mediaPlayer != null && mediaPlayer.setAudioTrack(audioTracks[which].id);
                    if (status != null) status.setText(ok
                            ? tx("تم تغيير مسار الصوت", "Audio track changed")
                            : tx("تعذر تغيير مسار الصوت", "Could not change audio track"));
                    dialog.dismiss();
                })
                .setNegativeButton(tx("إغلاق", "Close"), null)
                .show();
    }

    private void showSubtitleTracks() {
        MediaPlayer.TrackDescription[] tracks;
        try { tracks = mediaPlayer.getSpuTracks(); }
        catch (Throwable error) { tracks = null; }
        if (tracks == null || tracks.length == 0) {
            status.setText(tx("لا توجد ترجمات داخل البث", "No embedded subtitles available"));
            return;
        }
        final MediaPlayer.TrackDescription[] subtitleTracks = tracks;
        String[] labels = new String[subtitleTracks.length + 1];
        labels[0] = tx("إيقاف الترجمة", "Disable subtitles");
        int selected = mediaPlayer.getSpuTrack() == -1 ? 0 : -1;
        for (int i = 0; i < subtitleTracks.length; i++) {
            labels[i + 1] = subtitleTracks[i].name == null ? ("Subtitle " + subtitleTracks[i].id) : subtitleTracks[i].name;
            if (subtitleTracks[i].id == mediaPlayer.getSpuTrack()) selected = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(tx("اختر الترجمة", "Select subtitle"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    int trackId = which == 0 ? -1 : subtitleTracks[which - 1].id;
                    boolean ok = mediaPlayer != null && mediaPlayer.setSpuTrack(trackId);
                    if (status != null) status.setText(ok
                            ? tx("تم تغيير الترجمة", "Subtitle selection changed")
                            : tx("تعذر تغيير الترجمة", "Could not change subtitle"));
                    dialog.dismiss();
                })
                .setNegativeButton(tx("إغلاق", "Close"), null)
                .show();
    }

    @Override protected void onPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !released) mediaPlayer.play();
    }

    @Override protected void onDestroy() {
        released = true;
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Throwable ignored) {}
            try { mediaPlayer.detachViews(); } catch (Throwable ignored) {}
            try { mediaPlayer.release(); } catch (Throwable ignored) {}
            mediaPlayer = null;
        }
        if (libVLC != null) {
            try { libVLC.release(); } catch (Throwable ignored) {}
            libVLC = null;
        }
        super.onDestroy();
    }
}
