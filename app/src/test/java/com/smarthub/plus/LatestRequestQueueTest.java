package com.smarthub.plus;
import org.junit.Test;import static org.junit.Assert.*;
public class LatestRequestQueueTest {
 @Test public void burstKeepsNewestChannelWithoutParallelTuning(){LatestRequestQueue<Long> q=new LatestRequestQueue<>();assertTrue(q.offer(1L));assertFalse(q.offer(2L));assertFalse(q.offer(3L));assertEquals(Long.valueOf(3),q.complete());assertFalse(q.offer(4L));assertEquals(Long.valueOf(4),q.complete());assertNull(q.complete());assertTrue(q.offer(5L));}
 @Test public void closeDropsPendingRequest(){LatestRequestQueue<String> q=new LatestRequestQueue<>();q.offer("old");q.offer("pending");q.clear();assertTrue(q.offer("new"));assertNull(q.complete());}
}
