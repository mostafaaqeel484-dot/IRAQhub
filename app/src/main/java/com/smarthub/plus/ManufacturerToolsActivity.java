package com.smarthub.plus;
import android.content.Intent;
import android.os.Bundle;
/** Retained alias for old navigation links: no OEM menu is ever launched. */
public final class ManufacturerToolsActivity extends HubActivity {
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  startActivity(new Intent(this,ReceiverToolsActivity.class));
  finish();
 }
}
