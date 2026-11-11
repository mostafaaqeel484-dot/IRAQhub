package com.smarthub.plus;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import java.io.IOException;

/** Optional muted Home background. Release the decoder before leaving Home. */
final class RxMotionWallpaper extends TextureView implements TextureView.SurfaceTextureListener {
    private final String asset;
    private MediaPlayer player;
    private Surface surface;
    private boolean enabled, failed;

    RxMotionWallpaper(Context context, String asset) {
        super(context);
        this.asset = asset;
        setOpaque(false);
        setAlpha(0f);
        setSurfaceTextureListener(this);
    }

    void setPlaying(boolean playing) {
        enabled = playing;
        if (playing) startIfReady();
        else release();
    }

    private void startIfReady() {
        if (!enabled || failed || player != null || !isAvailable()) return;
        SurfaceTexture texture = getSurfaceTexture();
        if (texture == null) return;
        surface = new Surface(texture);
        try (AssetFileDescriptor file = getContext().getAssets().openFd(asset)) {
            player = new MediaPlayer();
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            player.setSurface(surface);
            player.setVolume(0f, 0f);
            player.setLooping(true);
            player.setOnPreparedListener(ready -> {
                if (enabled && ready == player) ready.start();
            });
            player.setOnErrorListener((source, what, extra) -> {
                if (source == player) {
                    failed = true;
                    release();
                }
                return true;
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException unavailable) {
            failed = true;
            release();
        }
    }

    private void release() {
        setAlpha(0f);
        if (player != null) {
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        startIfReady();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        if (enabled && player != null && getAlpha() == 0f) setAlpha(1f);
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        return true;
    }
    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }
}
