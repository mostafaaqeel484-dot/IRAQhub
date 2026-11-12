package com.smarthub.plus;

/** Exact DVB orbital selection and identity safety for CHMAX package downloads. */
final class ChmaxSatelliteSelection {
 private ChmaxSatelliteSelection(){}
 static boolean includes(int requestedOrbit,int candidateOrbit){
  return requestedOrbit==Integer.MIN_VALUE||requestedOrbit==candidateOrbit;
 }
 static boolean unambiguous(int matchingPhysicalServices){
  // A CHMAX ZIP filename identifies sat/frequency/SID, not polarization.
  // If that combination has multiple distinct DVB identities, never guess.
  return matchingPhysicalServices==1;
 }
}
