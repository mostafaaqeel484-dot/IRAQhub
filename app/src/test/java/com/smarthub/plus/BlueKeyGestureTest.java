package com.smarthub.plus;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class BlueKeyGestureTest {
 private static final class Harness implements BlueKeyGesture.Clock,BlueKeyGesture.Listener{long time;int single,signal,assistant;Map<Runnable,Long> jobs=new LinkedHashMap<>();BlueKeyGesture key=new BlueKeyGesture(this,this);public long now(){return time;}public void post(Runnable r,long d){jobs.put(r,time+d);}public void cancel(Runnable r){jobs.remove(r);}public void single(){single++;}public void signal(){signal++;}public void assistant(){assistant++;}void advance(long ms){long end=time+ms;while(true){Map.Entry<Runnable,Long> next=jobs.entrySet().stream().filter(e->e.getValue()<=end).min(Map.Entry.comparingByValue()).orElse(null);if(next==null)break;time=next.getValue();jobs.remove(next.getKey());next.getKey().run();}time=end;}void tap(){key.down();advance(50);key.up();}}
 @Test public void doublePressOnlyShowsSignal(){Harness h=new Harness();h.tap();h.advance(150);h.tap();h.advance(1000);assertEquals(1,h.signal);assertEquals(0,h.single);assertEquals(0,h.assistant);}
 @Test public void longPressOnlyShowsAssistant(){Harness h=new Harness();h.key.down();h.advance(700);h.key.down();h.key.up();h.advance(1000);assertEquals(1,h.assistant);assertEquals(0,h.single);assertEquals(0,h.signal);}
 @Test public void ordinaryPressWaitsForDoubleWindow(){Harness h=new Harness();h.tap();h.advance(300);assertEquals(0,h.single);h.advance(100);assertEquals(1,h.single);}
 @Test public void cancelAfterFocusChangeConsumesReleaseWithoutAction(){Harness h=new Harness();h.key.down();h.advance(50);h.key.cancel();assertTrue(h.key.up());h.advance(1000);assertEquals(0,h.single+h.signal+h.assistant);}
 @Test public void secondLongPressDoesNotAlsoFireFirstSingle(){Harness h=new Harness();h.tap();h.advance(100);h.key.down();h.advance(700);h.key.up();h.advance(1000);assertEquals(1,h.assistant);assertEquals(0,h.single+h.signal);}
}
