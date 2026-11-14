package com.jingting.tests;
import android.app.Activity;
import android.os.Bundle;
import android.media.*;
public final class FocusActivity extends Activity {
 private AudioManager manager;private AudioFocusRequest request;private MediaPlayer player;private boolean started;
 @Override public void onCreate(Bundle b){super.onCreate(b);if(android.os.Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}android.widget.TextView text=new android.widget.TextView(this);text.setText("精听音频打断验证，约 4 秒后结束");text.setPadding(32,64,32,32);setContentView(text);manager=(AudioManager)getSystemService(AUDIO_SERVICE);}
 @Override protected void onResume(){super.onResume();if(started)return;started=true;new android.os.Handler().postDelayed(()->{
  AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();int result;
  if(android.os.Build.VERSION.SDK_INT>=26){request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{}).build();result=manager.requestAudioFocus(request);}else result=manager.requestAudioFocus(change->{},AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
  android.util.Log.i("JingtingFocusTest","request result="+result);
  try{java.io.File folder=getExternalFilesDir(null);folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"focus-result.json"))){out.write(("{\"result\":"+result+",\"elapsedMs\":"+android.os.SystemClock.elapsedRealtime()+"}").getBytes("UTF-8"));}}catch(Exception ignored){}
  try{java.io.File file=new java.io.File(getCacheDir(),"focus.wav");try(java.io.InputStream in=getAssets().open("focus.wav");java.io.FileOutputStream out=new java.io.FileOutputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);}player=new MediaPlayer();player.setAudioAttributes(attrs);player.setDataSource(file.getAbsolutePath());player.setLooping(true);player.setVolume(0,0);player.prepare();player.start();}catch(Exception e){android.util.Log.e("JingtingFocusTest",e.toString());}
  new android.os.Handler().postDelayed(()->finish(),4000);
 },100);}
 @Override protected void onDestroy(){if(player!=null)player.release();if(android.os.Build.VERSION.SDK_INT>=26&&request!=null)manager.abandonAudioFocusRequest(request);super.onDestroy();}
}
