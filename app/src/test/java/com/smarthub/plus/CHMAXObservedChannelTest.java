package com.smarthub.plus;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Regression: an EPG programme title must never override the actual DVB channel label. */
public class CHMAXObservedChannelTest {
 private static SatelliteState channel(){
  SatelliteState state=new SatelliteState();
  state.channelId="Aurum:35";
  state.channelName="AL SHARQIYA NEWS HD";
  return state;
 }
 @Test public void programmeTitleDoesNotMaskVisibleChannelName(){
  Map<String,String> visible=new HashMap<>();
  visible.put("detail_info_tv_program_name","Eastern News Channel");
  visible.put("tv_channel_info_program_name","AL SHARQIYA NEWS HD");
  assertEquals("AL SHARQIYA NEWS HD",CHMAXIntegration.visibleChannelName(visible,channel()));
 }
 @Test public void unrelatedProgrammeCannotBeSavedAsLogo(){
  Map<String,String> visible=new HashMap<>();
  visible.put("detail_info_tv_program_name","Eastern News Channel");
  assertEquals("",CHMAXIntegration.visibleChannelName(visible,channel()));
 }
 @Test public void exactDetailChannelCanBeUsedWhenBannerIsHidden(){
  Map<String,String> visible=new HashMap<>();
  visible.put("detail_info_tv_program_name","AL SHARQIYA NEWS HD");
  assertEquals("AL SHARQIYA NEWS HD",CHMAXIntegration.visibleChannelName(visible,channel()));
 }
}
