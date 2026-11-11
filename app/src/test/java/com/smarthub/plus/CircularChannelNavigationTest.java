package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class CircularChannelNavigationTest {
 @Test public void downFromLastWrapsFirst(){assertEquals(0,CircularChannelNavigation.move(21,1,22));}
 @Test public void upFromFirstWrapsLast(){assertEquals(21,CircularChannelNavigation.move(0,-1,22));}
 @Test public void tenChannelsSkipWraps(){assertEquals(4,CircularChannelNavigation.move(19,7,22));}
 @Test public void singleAndEmpty(){assertEquals(0,CircularChannelNavigation.move(0,3,1));assertEquals(-1,CircularChannelNavigation.move(0,1,0));}
}