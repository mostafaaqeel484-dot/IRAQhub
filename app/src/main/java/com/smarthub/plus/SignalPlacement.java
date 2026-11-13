package com.smarthub.plus;

/** Side vs bottom tuner meters without changing DVB readings or the factory UI. */
final class SignalPlacement {
 static final String KEY="osd_signal_placement";
 static final String SIDE="side";
 static final String BOTTOM="bottom";
 static final String DEFAULT=BOTTOM;
 private SignalPlacement(){}
 static String normalize(String input){return SIDE.equals(input)?SIDE:BOTTOM;}
 static boolean bottom(String value){return BOTTOM.equals(normalize(value));}
}
