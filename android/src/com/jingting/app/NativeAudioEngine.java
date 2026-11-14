package com.jingting.app;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.io.File;
import org.json.JSONObject;

/** Main-thread native player. Uses one private cached source at a time. */
final class NativeAudioEngine {
    interface Emitter{void send(int token,String event,JSONObject state);}
    interface Focus{boolean request();}
    private final Context context;private final Emitter emitter;private final Focus focus;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaPlayer player;private File source;private int token,operation;private boolean prepared,ended,loop,seeking,pendingPlay;
    private float rate=1f,volume=1f,appliedRate=1f;private boolean visible=true;private long stopAt=-1;
    NativeAudioEngine(Context context,Emitter emitter,Focus focus){this.context=context;this.emitter=emitter;this.focus=focus;}
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(player==null||!prepared)return;
        try{
            if(!player.isPlaying())return;
            long position=player.getCurrentPosition();
            if(stopAt>=0&&position>=stopAt-15){player.pause();stopAt=-1;send("boundary",null);return;}
            send("timeupdate",null);
            long interval=visible?40:250;
            long delay=stopAt>=0?Math.max(10,Math.min(interval,(long)((stopAt-position-10)/Math.max(.1f,rate)))):interval;
            handler.postDelayed(this,delay);
        }catch(RuntimeException e){send("error",e.getMessage());}
    }};
    private void send(String type,String message){
        JSONObject state=new JSONObject();
        try{state.put("operation",operation);state.put("position",player!=null&&prepared?player.getCurrentPosition()/1000d:0);state.put("duration",player!=null&&prepared?player.getDuration()/1000d:0);if(message!=null)state.put("message",message);}catch(Exception ignored){}
        emitter.send(token,type,state);
    }
    void reset(int next){
        handler.removeCallbacksAndMessages(null);stopAt=-1;prepared=false;ended=false;seeking=false;pendingPlay=false;token=next;operation=0;
        if(player!=null){player.release();player=null;}if(source!=null){source.delete();source=null;}
    }
    void prepare(int next,File file){
        if(next!=token){file.delete();return;}
        if(player!=null)player.release();if(source!=null)source.delete();source=file;prepared=false;ended=false;
        player=new MediaPlayer();appliedRate=1f;MediaPlayer current=player;
        try{
            current.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
            current.setWakeMode(context,PowerManager.PARTIAL_WAKE_LOCK);current.setVolume(volume,volume);current.setLooping(loop);
            current.setOnPreparedListener(p->{if(p!=player)return;prepared=true;send("loadedmetadata",null);});
            current.setOnSeekCompleteListener(p->{if(p==player){ended=false;seeking=false;send("seeked",null);handler.removeCallbacks(tick);if(pendingPlay){pendingPlay=false;play(token,operation);}else if(p.isPlaying())handler.post(tick);}});
            current.setOnCompletionListener(p->{if(p==player){ended=true;handler.removeCallbacks(tick);stopAt=-1;send("ended",null);}});
            current.setOnErrorListener((p,what,extra)->{if(p==player){handler.removeCallbacks(tick);send("error","解码失败 "+what+"/"+extra);}return true;});
            current.setDataSource(file.getAbsolutePath());current.prepareAsync();
        }catch(Exception e){send("error",e.getMessage());}
    }
    void play(int expected,int nextOperation){
        if(expected!=token||nextOperation<operation||!prepared||player==null)return;operation=nextOperation;
        if(seeking){pendingPlay=true;return;}
        if(!focus.request()){send("error","暂时无法取得音频焦点");return;}
        try{if(ended){seek(token,0,operation);pendingPlay=true;return;}player.start();applyRate();send("play",null);handler.removeCallbacks(tick);handler.post(tick);}
        catch(RuntimeException e){send("error",e.getMessage());}
    }
    void pause(int expected,int nextOperation){if(expected!=token||nextOperation<operation)return;operation=nextOperation;pendingPlay=false;handler.removeCallbacks(tick);stopAt=-1;try{if(player!=null&&prepared&&player.isPlaying()){player.pause();send("pause",null);}}catch(RuntimeException ignored){}}
    void interrupt(){pause(token,operation);}
    void seek(int expected,double seconds,int nextOperation){
        if(expected!=token||nextOperation<operation||!prepared||player==null)return;operation=nextOperation;handler.removeCallbacks(tick);
        try{long time=Math.max(0,Math.min(player.getDuration(),(long)(seconds*1000)));seeking=true;if(Build.VERSION.SDK_INT>=26)player.seekTo(time,MediaPlayer.SEEK_CLOSEST);else player.seekTo((int)time);ended=false;}
        catch(RuntimeException e){send("error",e.getMessage());}
    }
    void rate(int expected,double value){if(expected!=token)return;rate=(float)Math.max(.5,Math.min(2,value));if(prepared&&player!=null&&player.isPlaying())try{applyRate();}catch(RuntimeException e){send("error",e.getMessage());}}
    private void applyRate(){if(player==null||!prepared||!player.isPlaying()||Math.abs(rate-appliedRate)<.001f)return;player.setPlaybackParams(new PlaybackParams().setSpeed(rate).setPitch(1f));appliedRate=rate;}
    void visible(boolean value){visible=value;}
    void volume(int expected,double value){if(expected!=token)return;volume=(float)Math.max(0,Math.min(1,value));if(player!=null)player.setVolume(volume,volume);}
    void loop(int expected,boolean value){if(expected!=token)return;loop=value;if(player!=null)player.setLooping(value);}
    void stopAt(int expected,double end){if(expected!=token)return;stopAt=end<0?-1:(long)(end*1000);handler.removeCallbacks(tick);if(player!=null&&prepared&&player.isPlaying())handler.post(tick);}
    boolean isPlaying(){try{return player!=null&&prepared&&player.isPlaying();}catch(RuntimeException e){return false;}}
    void release(){reset(token+1);}
}
