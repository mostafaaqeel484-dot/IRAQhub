package com.smarthub.plus
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoImageSizingTest {
 @Test fun smallImageIsNeverUpscaled(){assertEquals(1,LogoImageSizing.sample(48,31,512))}
 @Test fun hugeLogoUsesBoundedDecode(){assertEquals(8,LogoImageSizing.sample(4000,2400,512))}
 @Test fun portraitAlsoConstrained(){assertEquals(4,LogoImageSizing.sample(300,1800,512))}
}
