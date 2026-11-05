package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

/** JVM checks for the public/private boundary in the Aurum integration. */
public class OemDvbFeatureGatewayTest {
    @Test public void privateRecordingHasNoExternalIntent() {
        assertNull(OemDvbFeatureGateway.intent(null, OemDvbFeatureGateway.Feature.OEM_PVR_PRIVATE));
        assertNull(OemDvbFeatureGateway.intent(null, OemDvbFeatureGateway.Feature.OEM_TIMESHIFT_PRIVATE));
    }
    @Test public void oemMenuIdsMatchPublishedRoutes() {
        assertEquals("satellite", OemDvbFeatureGateway.Feature.OEM_SATELLITES.manufacturerId);
        assertEquals("motor", OemDvbFeatureGateway.Feature.OEM_MOTOR.manufacturerId);
        assertEquals("edit_tv", OemDvbFeatureGateway.Feature.OEM_EDIT_TV.manufacturerId);
        assertEquals("edit_favorites", OemDvbFeatureGateway.Feature.OEM_FAVOURITES.manufacturerId);
        assertTrue(OemDvbFeatureGateway.Feature.OEM_NET_UPDATE.confirmationRequired);
        assertTrue(OemDvbFeatureGateway.Feature.OEM_FILE_UPDATE.confirmationRequired);
    }
}
