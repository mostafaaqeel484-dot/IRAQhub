package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public class MenuKeyHandlerTest {
    @Test public void opensOnceAndConsumesReleaseAfterFocusChanges(){
        int[] opens={0};MenuKeyHandler key=new MenuKeyHandler(()->opens[0]++);
        assertTrue(key.down(true,0));assertTrue(key.down(false,1));
        assertTrue(key.up());assertEquals(1,opens[0]);assertFalse(key.up());
    }
    @Test public void otherApplicationsAndDisabledShortcutKeepTheirMenu(){
        MenuKeyHandler key=new MenuKeyHandler(()->fail("Unrelated application intercepted"));
        assertFalse(key.down(false,0));assertFalse(key.up());
    }
    @Test public void orphanRepeatDoesNotOpenHome(){
        MenuKeyHandler key=new MenuKeyHandler(()->fail("Orphan repeat opened home"));
        assertFalse(key.down(true,2));assertFalse(key.up());
    }
    @Test public void newPressCanOpenAgain(){
        int[] opens={0};MenuKeyHandler key=new MenuKeyHandler(()->opens[0]++);
        key.down(true,0);key.up();key.down(true,0);key.up();assertEquals(2,opens[0]);
    }
    @Test public void disconnectClearsHeldState(){
        MenuKeyHandler key=new MenuKeyHandler(()->{});key.down(true,0);key.reset();assertFalse(key.up());
    }
}
