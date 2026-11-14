package com.jingting.tests;
import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.*;
import android.view.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public final class DeviceTests extends Instrumentation {
 private WebView web;private Activity activity;private final CountDownLatch done=new CountDownLatch(1);private String report="";private boolean battery;
 private synchronized void saveReport(String text){try{java.io.File folder=new java.io.File(getTargetContext().getExternalFilesDir(null),"test-results");folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,battery?"battery-report.json":"device-report.json"))){out.write(text.getBytes("UTF-8"));}}catch(Exception ignored){}}
 private WebView findWeb(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=findWeb(g.getChildAt(i));if(w!=null)return w;}}return null;}
 public final class Bridge {
  @JavascriptInterface public void done(String text){report=text;saveReport(text);done.countDown();}
  @JavascriptInterface public void checkpoint(String text){saveReport(text);}
  @JavascriptInterface public String sample(){
   JSONObject value=new JSONObject();
   try{
    android.os.BatteryManager manager=(android.os.BatteryManager)getTargetContext().getSystemService(Context.BATTERY_SERVICE);
    Intent status=getTargetContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    value.put("interactive",((android.os.PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).isInteractive());value.put("elapsedMs",SystemClock.elapsedRealtime());value.put("chargeUah",manager.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));value.put("currentUa",manager.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
    value.put("plugged",status.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,-1));value.put("temperatureC",status.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,0)/10d);value.put("voltageMv",status.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE,0));value.put("level",status.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,0));value.put("refreshHz",activity.getWindowManager().getDefaultDisplay().getRefreshRate());value.put("requestedHz",activity.getWindow().getAttributes().preferredRefreshRate);
   }catch(Exception e){try{value.put("error",e.toString());}catch(Exception ignored){}}
   return value.toString();
  }
  @JavascriptInterface public void stage(String text){Bundle b=new Bundle();b.putString("stage",text);sendStatus(0,b);}
  @JavascriptInterface public float refreshRate(){return activity.getWindow().getAttributes().preferredRefreshRate;}
  @JavascriptInterface public void noisy(){runOnMainSync(()->{try{java.lang.reflect.Field f=activity.getClass().getDeclaredField("noisyReceiver");f.setAccessible(true);((BroadcastReceiver)f.get(activity)).onReceive(activity,new Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY));}catch(Exception e){throw new RuntimeException(e);}});}
 }
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);battery=arguments!=null&&"battery".equals(arguments.getString("suite"));start();}
 @Override public void callActivityOnCreate(Activity target,Bundle state){super.callActivityOnCreate(target,state);if(target.getClass().getName().equals("com.jingting.app.MainActivity"))activity=target;}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   Intent intent=new Intent();intent.setClassName("app.jtpractice.offlineplayer","com.jingting.app.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   getTargetContext().startActivity(intent);long launchDeadline=System.currentTimeMillis()+15000;while(activity==null&&System.currentTimeMillis()<launchDeadline)Thread.sleep(30);if(activity==null)throw new Exception("Activity did not start");runOnMainSync(()->{web=findWeb(activity.getWindow().getDecorView());if(battery){activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);WindowManager.LayoutParams attrs=activity.getWindow().getAttributes();attrs.screenBrightness=.35f;activity.getWindow().setAttributes(attrs);}});
   if(web==null)throw new Exception("WebView not found");
   Thread.sleep(1800);
   byte[] bytes;try(java.io.InputStream in=getContext().getAssets().open(battery?"battery-tests.js":"device-tests.js")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);bytes=out.toByteArray();}
   String script=new String(bytes,"UTF-8");runOnMainSync(()->{web.addJavascriptInterface(new Bridge(),"DeviceTests");});
   // addJavascriptInterface is visible after the next document load.
   runOnMainSync(()->web.reload());Thread.sleep(1800);
   runOnMainSync(()->web.evaluateJavascript(script,null));
   if(!done.await(battery?2700:180,TimeUnit.SECONDS))throw new Exception("Device suite timeout");
   result.putString("report",report);
   runOnMainSync(()->{web.removeJavascriptInterface("DeviceTests");if(battery){activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);WindowManager.LayoutParams attrs=activity.getWindow().getAttributes();attrs.screenBrightness=-1f;activity.getWindow().setAttributes(attrs);}});
   finish(new JSONObject(report).optBoolean("passed")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
  }catch(Exception e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
 }
}
