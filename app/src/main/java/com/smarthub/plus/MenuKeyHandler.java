package com.smarthub.plus;

/** Consumes a complete Menu press even when opening home changes the focused window. */
final class MenuKeyHandler {
    private final Runnable open;
    private boolean held;
    MenuKeyHandler(Runnable open){this.open=open;}
    boolean down(boolean eligible,int repeats){
        if(held)return true;
        if(!eligible||repeats!=0)return false;
        held=true;open.run();return true;
    }
    boolean up(){boolean consumed=held;held=false;return consumed;}
    void reset(){held=false;}
}
