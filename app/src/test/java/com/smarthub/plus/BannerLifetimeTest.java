package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class BannerLifetimeTest {
 @Test public void disappearsAtTenSecondsAndImageRepaintsDoNotExtendIt(){BannerLifetime timer=new BannerLifetime(10000);timer.context("channel|film|next",500);assertTrue(timer.visible(10499));timer.context("channel|film|next",9999);assertFalse(timer.visible(10500));}
 @Test public void channelProgrammeOrUpcomingChangeRestartsWindow(){BannerLifetime timer=new BannerLifetime(10000);timer.context("a|film|next",0);timer.context("b|film|next",11000);assertTrue(timer.visible(20999));assertFalse(timer.visible(21000));timer.context("b|film|different-next",21000);assertTrue(timer.visible(30999));}
 @Test public void changingDurationKeepsOriginalStart(){BannerLifetime t=new BannerLifetime(10000);t.show(100);t.duration(2000);assertTrue(t.visible(2099));assertFalse(t.visible(2100));}
 @Test public void requestedDurationIsBounded(){BannerLifetime t=new BannerLifetime(10000);t.show(100);t.duration(0);assertFalse(t.visible(1100));t.show(2000);t.duration(999999);assertFalse(t.visible(12000));}
}
