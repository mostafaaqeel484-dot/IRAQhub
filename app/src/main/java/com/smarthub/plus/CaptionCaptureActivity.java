package com.smarthub.plus;
import android.app.Activity;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
/** Android's own screen-sharing consent; no capture token is stored or reused. */
public final class CaptionCaptureActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);if(b==null)startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),910);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==910&&result==RESULT_OK&&data!=null)startForegroundService(new Intent(this,CaptionCaptureService.class).putExtra("result",result).putExtra("consent",data));finish();}
}
