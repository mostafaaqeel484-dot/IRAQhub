package com.smarthub.plus;
import org.junit.Test;import static org.junit.Assert.*;
public class DecoderDimensionsTest {
 @Test public void parsesRealDecoderFields(){DecoderDimensions s=DecoderDimensions.parse("decoder status\nwidth : 1920\nheight : 1080\n");assertNotNull(s);assertEquals(1920,s.width);assertEquals(1080,s.height);}
 @Test public void doesNotInferMissingOrInvalidDimensions(){assertNull(DecoderDimensions.parse("name: Channel HD\nwidth: 0\nheight: 1080"));assertNull(DecoderDimensions.parse("width:1920"));assertNull(DecoderDimensions.parse("width:99999\nheight:1080"));}
 @Test public void rejectsAmbiguousMultipleDecoders(){assertNull(DecoderDimensions.parse("width:720\nheight:576\nwidth:1920\nheight:1080"));}
 @Test public void handlesSdAndUhd(){assertEquals(576,DecoderDimensions.parse("width:720\nheight:576").height);assertEquals(2160,DecoderDimensions.parse("width:3840\nheight:2160").height);}
}
