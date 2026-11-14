package com.jingting.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final int FILES = 41;
    private static final int FOLDER = 42;
    private static final int BACKUP_SAVE = 43;
    private java.io.OutputStream backupOutput;
    private WebView web;
    private NativeAudioEngine nativeAudio;
    private final PlayerBridge playerBridge=new PlayerBridge();
    private android.os.PowerManager.WakeLock gapWakeLock;
    private MediaSession mediaSession;
    private NotificationManager notifications;
    private static java.lang.ref.WeakReference<MainActivity> current = new java.lang.ref.WeakReference<>(null);
    private String mediaTitle = "精听", mediaSentence = "";
    private boolean mediaPlaying, mediaActualPlaying, noisyRegistered;
    private final android.os.Handler playbackHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<Integer,Runnable> playbackTimers=new java.util.HashMap<>();
    private final android.content.BroadcastReceiver noisyReceiver=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context c,Intent intent){if(mediaPlaying&&android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))interruptAudio("noisy");}
    };
    private long mediaPosition, mediaDuration;
    private float mediaRate = 1f;
    private android.media.AudioManager audioManager;
    private android.media.AudioFocusRequest audioFocusRequest;
    private volatile boolean audioFocusGranted,audioFocusRegistered;
    private final android.media.AudioManager.OnAudioFocusChangeListener audioFocusListener=change->{
        if(!audioFocusRegistered)return;
        if(change<0){audioFocusGranted=false;interruptAudio("focus");}
        else if(change==android.media.AudioManager.AUDIOFOCUS_GAIN)audioFocusGranted=true;
    };
    private ValueCallback<Uri[]> fileCallback;
    private final ArrayList<Uri> folderFiles = new ArrayList<>();
    private String folderToken = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        current = new java.lang.ref.WeakReference<>(this);
        setupMedia();
        applyRefreshRate();
        nativeAudio=new NativeAudioEngine(getApplicationContext(),(token,event,snapshot)->{
            if(web!=null)web.evaluateJavascript("window.jingtingPlayerEvent("+token+","+JSONObject.quote(event)+","+snapshot.toString()+")",null);
        },()->requestMediaFocus());
        gapWakeLock=((android.os.PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"jingting:follow-gap");gapWakeLock.setReferenceCounted(false);
        java.io.File[] oldAudio=getCacheDir().listFiles((dir,name)->name.startsWith("jt-audio-"));if(oldAudio!=null)for(java.io.File file:oldAudio)file.delete();
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new FolderBridge(), "JingtingNative");
        web.addJavascriptInterface(new MediaBridge(), "JingtingMedia");
        web.addJavascriptInterface(playerBridge,"JingtingPlayer");
        web.addJavascriptInterface(new BackupBridge(), "JingtingBackup");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) {
                    try {
                        if ("/assets/index.html".equals(uri.getPath())) return new WebResourceResponse("text/html", "UTF-8", getAssets().open("index.html"));
                        String[] parts = uri.getPath().split("/");
                        if (parts.length == 4 && "folder".equals(parts[1])) {
                            Uri file;
                            synchronized (folderFiles) {
                                int i = Integer.parseInt(parts[3]);
                                if (!parts[2].equals(folderToken) || i < 0 || i >= folderFiles.size()) return notFound();
                                file = folderFiles.get(i);
                            }
                            return new WebResourceResponse("application/octet-stream", null, getContentResolver().openInputStream(file));
                        }
                    } catch (Exception ignored) {}
                    return notFound();
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost()) && "https".equals(uri.getScheme())) return false;
                if ("eudic".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "未找到欧路词典", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(intent, FILES); return true; }
                catch (ActivityNotFoundException e) { fileCallback = null; cb.onReceiveValue(null); return false; }
            }
        });
        setContentView(web);
        web.loadUrl(HOME);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == BACKUP_SAVE) {
            boolean ready = false;
            if (result == RESULT_OK && data != null && data.getData() != null) {
                try { backupOutput = new java.io.BufferedOutputStream(getContentResolver().openOutputStream(data.getData(), "w")); ready = true; }
                catch (Exception e) { backupOutput = null; }
            }
            if (web != null) web.evaluateJavascript("window.jingtingBackupChosen(" + ready + ")", null);
            return;
        }
        if (request == FOLDER) {
            if (result == RESULT_OK && data != null && data.getData() != null) readFolder(data.getData());
            return;
        }
        if (request != FILES || fileCallback == null) return;
        Uri[] resultUris = null;
        if (result == RESULT_OK && data != null && data.getClipData() != null) {
            int count = data.getClipData().getItemCount(); resultUris = new Uri[count];
            for (int i = 0; i < count; i++) resultUris[i] = data.getClipData().getItemAt(i).getUri();
        } else resultUris = WebChromeClient.FileChooserParams.parseResult(result, data);
        fileCallback.onReceiveValue(resultUris);
        fileCallback = null;
    }
    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", java.util.Collections.emptyMap(), null);
    }
    private final class BackupBridge {
        @JavascriptInterface public void pickSave() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE, "精听备份.jtbak");
                    startActivityForResult(intent, BACKUP_SAVE);
                } catch (ActivityNotFoundException e) {
                    if (web != null) web.evaluateJavascript("window.jingtingBackupChosen(false)", null);
                }
            });
        }
        @JavascriptInterface public synchronized boolean writeChunk(String base64) {
            if (backupOutput == null) return false;
            try { backupOutput.write(android.util.Base64.decode(base64, android.util.Base64.DEFAULT)); return true; }
            catch (Exception e) { closeBackup(); return false; }
        }
        @JavascriptInterface public synchronized boolean finish() {
            if (backupOutput == null) return false;
            try { backupOutput.flush(); backupOutput.close(); backupOutput = null; return true; }
            catch (Exception e) { closeBackup(); return false; }
        }
        @JavascriptInterface public synchronized void abort() { closeBackup(); }
    }
    private void closeBackup() {
        if (backupOutput != null) try { backupOutput.close(); } catch (Exception ignored) {}
        backupOutput = null;
    }

    private final class FolderBridge {
        @JavascriptInterface public void pickFolder() {
            runOnUiThread(() -> {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER); }
                catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "无法选择文件夹，请选择文件或 ZIP", Toast.LENGTH_LONG).show(); }
            });
        }
    }
    private void readFolder(Uri tree) {
        new Thread(() -> {
            try {
                ArrayList<Uri> found = new ArrayList<>(); JSONArray items = new JSONArray();
                collectFolder(tree, DocumentsContract.getTreeDocumentId(tree), found, items);
                String token = java.util.UUID.randomUUID().toString();
                synchronized (folderFiles) { folderFiles.clear(); folderFiles.addAll(found); folderToken = token; }
                String script = "window.jingtingFolderReady(" + items.toString() + "," + JSONObject.quote(token) + ")";
                runOnUiThread(() -> { if (web != null) web.evaluateJavascript(script, null); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "读取文件夹失败，请选择文件或 ZIP", Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    private void collectFolder(Uri tree, String parent, ArrayList<Uri> found, JSONArray items) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
        String[] fields = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE };
        try (Cursor cursor = getContentResolver().query(children, fields, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) collectFolder(tree, id, found, items);
                else if (name != null && name.matches("(?i).+\\.(mp3|m4a|wav|aac|ogg|flac|mp4|m4b|opus|webm|srt|vtt|txt)$")) {
                    JSONObject item = new JSONObject(); item.put("name", name); item.put("size", cursor.isNull(3) ? 0 : cursor.getLong(3));
                    items.put(item); found.add(DocumentsContract.buildDocumentUriUsingTree(tree, id));
                }
            }
        }
    }

    private void applyRefreshRate() {
        if(Build.VERSION.SDK_INT<23)return;
        float requested=0f;
        if("smooth".equals(getPreferences(MODE_PRIVATE).getString("refreshMode","system"))) {
            for(android.view.Display.Mode mode:getWindowManager().getDefaultDisplay().getSupportedModes()){
                if(mode.getRefreshRate()>=119f&&mode.getRefreshRate()<=121f){requested=mode.getRefreshRate();break;}
            }
        }
        android.view.WindowManager.LayoutParams attrs=getWindow().getAttributes();
        attrs.preferredDisplayModeId=0;attrs.preferredRefreshRate=requested;getWindow().setAttributes(attrs);
    }
    @Override protected void onResume(){super.onResume();applyRefreshRate();if(nativeAudio!=null)nativeAudio.visible(true);}
    @Override protected void onStop(){if(nativeAudio!=null)nativeAudio.visible(false);super.onStop();}
    private void interruptAudio(String reason){
        mediaPlaying=false;mediaActualPlaying=false;
        if(nativeAudio!=null)nativeAudio.interrupt();
        playbackHandler.removeCallbacksAndMessages(null);playbackTimers.clear();
        if(web!=null)web.evaluateJavascript("window.jingtingAudioInterrupted("+JSONObject.quote(reason)+")",null);
        publishMedia();
    }
    private void updateNoisyReceiver(){
        if(mediaPlaying&&!noisyRegistered){
            android.content.IntentFilter filter=new android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(noisyReceiver,filter,android.content.Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisyReceiver,filter);
            noisyRegistered=true;
        }else if(!mediaPlaying&&noisyRegistered){try{unregisterReceiver(noisyReceiver);}catch(Exception ignored){}noisyRegistered=false;}
    }

    static void mediaCommand(String command) {
        MainActivity a = current.get();
        if (a != null) a.runOnUiThread(() -> a.controlMedia(command));
    }
    private void controlMedia(String command) {
        if (web == null) return;
        if ("play".equals(command)) web.evaluateJavascript("window.jingtingMediaCommand('play')", null);
        else if ("pause".equals(command)) { interruptAudio("pause"); }
        else if ("next".equals(command)||"previous".equals(command)) web.evaluateJavascript("window.jingtingMediaCommand("+JSONObject.quote(command)+")",null);
    }
    private void setupMedia() {
        audioManager=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            android.media.AudioAttributes attrs=new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build();
            audioFocusRequest=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(audioFocusListener,playbackHandler).build();
        }
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(new NotificationChannel("playback", "播放控制", NotificationManager.IMPORTANCE_LOW));
        mediaSession = new MediaSession(this, "JingtingPlayback");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { controlMedia("play"); }
            @Override public void onPause() { controlMedia("pause"); }
            @Override public void onStop() { controlMedia("pause"); }
            @Override public void onSkipToNext() { controlMedia("next"); }
            @Override public void onSkipToPrevious() { controlMedia("previous"); }
            @Override public void onSeekTo(long position) {
                if (web != null) web.evaluateJavascript("window.jingtingMediaSeek(" + Math.max(0, position) + ")", null);
            }
        });
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mediaSession.setSessionActivity(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
    private final class PlayerBridge {
        private int sourceToken;
        private java.io.OutputStream output;
        private java.io.File staged;
        private synchronized void clearStage(){
            if(output!=null)try{output.close();}catch(Exception ignored){}output=null;
            if(staged!=null)staged.delete();staged=null;
        }
        @JavascriptInterface public synchronized void reset(int token){
            sourceToken=token;clearStage();runOnUiThread(()->nativeAudio.reset(token));
        }
        @JavascriptInterface public synchronized boolean beginSource(int token){
            if(token!=sourceToken)return false;clearStage();
            try{staged=java.io.File.createTempFile("jt-audio-",".bin",getCacheDir());output=new java.io.BufferedOutputStream(new java.io.FileOutputStream(staged));return true;}
            catch(Exception e){clearStage();return false;}
        }
        @JavascriptInterface public synchronized boolean writeChunk(int token,String base64){
            if(token!=sourceToken||output==null)return false;
            try{output.write(android.util.Base64.decode(base64,android.util.Base64.DEFAULT));return true;}
            catch(Exception e){clearStage();return false;}
        }
        @JavascriptInterface public synchronized void finishSource(int token){
            if(token!=sourceToken||output==null)return;
            try{output.close();output=null;java.io.File file=staged;staged=null;runOnUiThread(()->nativeAudio.prepare(token,file));}
            catch(Exception e){clearStage();}
        }
        @JavascriptInterface public void play(int token,int operation){runOnUiThread(()->nativeAudio.play(token,operation));}
        @JavascriptInterface public void pause(int token,int operation){runOnUiThread(()->nativeAudio.pause(token,operation));}
        @JavascriptInterface public void seek(int token,double time,int operation){if(Double.isFinite(time))runOnUiThread(()->nativeAudio.seek(token,time,operation));}
        @JavascriptInterface public void rate(int token,double rate){if(Double.isFinite(rate))runOnUiThread(()->nativeAudio.rate(token,rate));}
        @JavascriptInterface public void volume(int token,double volume){if(Double.isFinite(volume))runOnUiThread(()->nativeAudio.volume(token,volume));}
        @JavascriptInterface public void loop(int token,boolean loop){runOnUiThread(()->nativeAudio.loop(token,loop));}
        @JavascriptInterface public void stopAt(int token,double end){if(Double.isFinite(end))runOnUiThread(()->nativeAudio.stopAt(token,end));}
        void close(){clearStage();}
    }

    private final class MediaBridge {
        @JavascriptInterface public boolean requestFocus(){return requestMediaFocus();}
        @JavascriptInterface public void setRefreshMode(String mode){
            runOnUiThread(()->{getPreferences(MODE_PRIVATE).edit().putString("refreshMode","smooth".equals(mode)?"smooth":"system").apply();applyRefreshRate();});
        }
        @JavascriptInterface public void scheduleTimer(int id,double delay){
            runOnUiThread(()->{Runnable old=playbackTimers.remove(id);if(old!=null)playbackHandler.removeCallbacks(old);
                Runnable task=()->{playbackTimers.remove(id);if(web!=null)web.evaluateJavascript("window.jingtingNativeTimer("+id+")",null);};
                playbackTimers.put(id,task);playbackHandler.postDelayed(task,Math.max(0,Math.min(2147483647L,(long)delay)));});
        }
        @JavascriptInterface public void cancelTimer(int id){runOnUiThread(()->{Runnable task=playbackTimers.remove(id);if(task!=null)playbackHandler.removeCallbacks(task);});}

        @JavascriptInterface public void update(String title, String sentence, boolean playing, double position, double duration, double rate, boolean actualPlaying) {
            runOnUiThread(() -> {
                mediaTitle = title == null || title.isEmpty() ? "精听" : title;
                mediaSentence = sentence == null ? "" : sentence;
                mediaPlaying = playing;mediaActualPlaying=actualPlaying;
                mediaPosition = Double.isFinite(position) ? Math.max(0, (long)(position * 1000)) : 0;
                mediaDuration = Double.isFinite(duration) ? Math.max(0, (long)(duration * 1000)) : 0;
                mediaRate = Double.isFinite(rate) && rate > 0 ? (float) rate : 1f;
                publishMedia();
            });
        }
    }
    private void publishMedia() {
        if (mediaSession == null) return;
        if(nativeAudio!=null&&nativeAudio.isPlaying()){mediaPlaying=true;mediaActualPlaying=true;}
        if(!mediaPlaying)abandonMediaFocus();
        if(gapWakeLock!=null){if(mediaPlaying&&!mediaActualPlaying)gapWakeLock.acquire(10000);else if(gapWakeLock.isHeld())gapWakeLock.release();}
        updateNoisyReceiver();
        mediaSession.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, mediaSentence.isEmpty() ? mediaTitle : mediaSentence)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, mediaSentence.isEmpty() ? "精听" : mediaTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, mediaDuration).build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
            .setState(mediaActualPlaying ? PlaybackState.STATE_PLAYING : mediaPlaying ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED, mediaPosition, mediaActualPlaying ? mediaRate : 0f).build());
        mediaSession.setActive(true);
        Intent cmd = new Intent(this, MediaReceiver.class).setAction(mediaPlaying ? "pause" : "play");
        PendingIntent pi = PendingIntent.getBroadcast(this, 1, cmd, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "playback") : new Notification.Builder(this);
        b.setSmallIcon(com.jingting.app.R.drawable.ic_notification).setContentTitle(mediaSentence.isEmpty() ? mediaTitle : mediaSentence)
          .setContentText(mediaSentence.isEmpty() ? "精听" : mediaTitle).setContentIntent(mediaSession.getController().getSessionActivity())
          .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
          .addAction(mediaPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, mediaPlaying ? "暂停" : "播放", pi)
          .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0));
        Notification notification=b.build();
        try{PlaybackService.update(this,notification,mediaPlaying);}
        catch(RuntimeException e){interruptAudio("background-unavailable");Toast.makeText(this,"后台播放服务暂不可用，请重新点播放",Toast.LENGTH_LONG).show();return;}
        notifications.notify(101, notification);
    }

    private synchronized boolean requestMediaFocus(){
        if(audioFocusGranted)return true;
        int result=Build.VERSION.SDK_INT>=26?audioManager.requestAudioFocus(audioFocusRequest):audioManager.requestAudioFocus(audioFocusListener,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN);
        audioFocusGranted=result==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;audioFocusRegistered=audioFocusGranted;return audioFocusGranted;
    }
    private void abandonMediaFocus(){
        if(!audioFocusRegistered||audioManager==null)return;
        audioFocusRegistered=false;audioFocusGranted=false;
        if(Build.VERSION.SDK_INT>=26)audioManager.abandonAudioFocusRequest(audioFocusRequest);else audioManager.abandonAudioFocus(audioFocusListener);
    }

    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(function(){if(document.querySelector('#sheet.on')){document.querySelector('#sheetX').click();return true;}if(document.querySelector('#vPlay.on')){document.querySelector('#btnBack').click();return true;}if(document.querySelector('#vSet.on')){document.querySelector('#setBack').click();return true;}return false;})()", result -> {
            if (!"true".equals(result)) MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        closeBackup();playerBridge.close();if(nativeAudio!=null)nativeAudio.release();
        if(gapWakeLock!=null&&gapWakeLock.isHeld())gapWakeLock.release();
        playbackHandler.removeCallbacksAndMessages(null);playbackTimers.clear();
        mediaPlaying=false;updateNoisyReceiver();
        abandonMediaFocus();
        PlaybackService.shutdown(this);
        if (notifications != null) notifications.cancel(101);
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); mediaSession = null; }
        current.clear();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}

