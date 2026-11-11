package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class ReceiverLogoAssetsTest {
    @Test public void matchesExportAliasWithResolutionSuffix() {
        assertEquals("ADSPORT1", ReceiverLogoAssets.normalizedName("AD Sport 1 HD"));
        assertEquals("MBCBOLLYWOOD", ReceiverLogoAssets.normalizedName("MBC Bollywood"));
    }

    @Test public void preservesServiceNumberAndLanguage() {
        assertEquals("BEINSPORTSENGLISH2",
            ReceiverLogoAssets.normalizedName("beIN Sports English 2 HD"));
    }
}
