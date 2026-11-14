package com.jingting.app;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** Keeps an intentional WebView listening session in the foreground service lifecycle. */
public final class PlaybackService extends Service {
    private static PlaybackService running;
    private static boolean starting, desiredPlaying, closing;
    private static Notification latest;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable settlePause=()->{if(!desiredPlaying){stopForeground(closing);stopSelf();}};
    /** All calls run on the main thread. A pending start is always acknowledged before stopping. */
    public static void update(android.content.Context context,Notification notification,boolean playing){
        latest=notification;desiredPlaying=playing;closing=false;
        if(running!=null){running.applyLatest();return;}
        if(!playing||starting)return;
        starting=true;
        try{Intent intent=new Intent(context,PlaybackService.class);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);}
        catch(RuntimeException error){starting=false;desiredPlaying=false;throw error;}
    }
    public static void shutdown(android.content.Context context){
        desiredPlaying=false;closing=true;
        if(running!=null){running.handler.removeCallbacks(running.settlePause);running.stopForeground(true);running.stopSelf();}
    }
    private void applyLatest(){
        handler.removeCallbacks(settlePause);
        if(desiredPlaying){if(latest!=null)startForeground(101,latest);}
        else handler.postDelayed(settlePause,500);
    }
    @Override public void onCreate(){
        super.onCreate();
        running=this;starting=false;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("playback","播放控制",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"playback"):new Notification.Builder(this);
        Notification initial=latest!=null?latest:builder.setSmallIcon(R.drawable.ic_notification).setContentTitle("精听练习").setContentText("正在准备播放").setOnlyAlertOnce(true).build();
        startForeground(101,initial);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){desiredPlaying=false;closing=true;}
        applyLatest();
        return START_NOT_STICKY;
    }
    @Override public void onTaskRemoved(Intent rootIntent){desiredPlaying=false;closing=true;MainActivity.mediaCommand("pause");stopForeground(true);stopSelf();}
    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);running=null;starting=false;stopForeground(closing);super.onDestroy();
        if(desiredPlaying&&!closing)update(getApplicationContext(),latest,true);
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
