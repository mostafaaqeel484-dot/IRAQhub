package com.smarthub.plus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Do not confuse an OEM-exported activity with an app-owned DVB function.
 * Only actions independently implemented in IRAQhub may navigate from
 * the native-only receiver settings screen.
 */
final class NativeOnlyFeaturePolicy {
 private NativeOnlyFeaturePolicy(){}
 private static final Set<String> OWNED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
  "local_lnb","local_sats","local_channels","local_signal",
  "local_chmax","local_logos","osd","remote"
 )));
 static boolean isOwned(String route){return route!=null&&OWNED.contains(route);}
}
