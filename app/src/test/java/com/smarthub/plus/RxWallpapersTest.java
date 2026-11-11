package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RxWallpapersTest {
    @Test public void newWallpapersResolveWithoutMovingExistingThemes() {
        assertEquals(0, RxSkin.index("unknown"));
        assertEquals(1, RxSkin.index("rx-light"));
        assertEquals(11, RxSkin.index("minimal"));
        assertEquals("waterfall", RxOriginalAssets.wallpaperKey(RxSkin.index("waterfall")));
        assertEquals("wolf", RxOriginalAssets.wallpaperKey(RxSkin.index("wolf-moon")));
        assertEquals("pyramids", RxOriginalAssets.wallpaperKey(RxSkin.index("pyramids")));
        assertNull(RxOriginalAssets.wallpaperKey(RxSkin.index("minimal")));
        assertEquals(RxSkin.IDS.length, RxSkin.AR.length);
        assertEquals(RxSkin.IDS.length, RxSkin.EN.length);
        assertEquals(RxSkin.IDS.length, RxSkin.ACCENTS.length);
    }
}
