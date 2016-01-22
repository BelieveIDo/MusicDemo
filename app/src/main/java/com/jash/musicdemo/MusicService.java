package com.jash.musicdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v7.app.NotificationCompat;
import android.widget.RemoteViews;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "MusicService";
    private MediaPlayer player;
    private int[] ids;
    private Map<Integer, String> map;
    private Messenger msg;
    private Notification build;
    private NotificationManager manager;
    private MusicReceiver musicReceiver;
    private int stateId;

    @Override
    public void onCreate() {
        super.onCreate();
        player = new MediaPlayer();
        //一个音频文件播放结束时
        player.setOnCompletionListener(this);
        musicReceiver = new MusicReceiver(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ids = intent.getIntArrayExtra("ids");
        String[] dates = intent.getStringArrayExtra("dates");
        map = new HashMap<>();
        for (int i = 0; i < ids.length; i++) {
            map.put(ids[i], dates[i]);
        }
        msg = intent.getParcelableExtra("msg");
        if (currentId != -1){
            sendId();
        }
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return super.onStartCommand(intent, flags, startId);
    }
    private MusicBinder binder;
    @Override
    public IBinder onBind(Intent intent) {
        if (binder == null) {
            binder = new MusicBinder();
        }
        return binder;
    }

    /**
     * 再次绑定时调用
     * @param intent
     */
    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        //关闭通知
        manager.cancel("play", 0);
        build = null;
        unregisterReceiver(musicReceiver);
    }

    /**
     * 播放完成时调用，用于做播放模式 MediaPlayer.setOnCompletionListener;
     * @param mp
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        switch (stateId){
            case 0:
                //单曲循环
                playOrPause(currentId);
                break;
            case 1:
                //循环播放
                playNext();
                break;
            case 2:
                //随机播放
                Random random = new Random(System.currentTimeMillis());
                int i = random.nextInt(ids.length);
                while (ids[i] == currentId){
                    i = random.nextInt(ids.length);
                }
                playOrPause(ids[i]);
                break;
        }







    }

    public class MusicBinder extends Binder{
        public MusicService getService() {
            return MusicService.this;
        }
    }
    private int currentId = -1;

    public void playOrPause(){
        playOrPause(currentId);
    }
    /**
     * 播放或暂停
     * @param id
     */
    public void playOrPause(int id){
        //首次点播放，播放第一次歌
        if (id == -1){
            id = ids[0];
        }
        //是否播放当前歌曲
        if (id == currentId){
            //由播放状态决定播放或暂停
            if (player.isPlaying()){
                player.pause();
            } else {
                player.start();
            }
        } else {
            //如果是新歌播放
            player.reset();
            try {
                //设置歌曲地址
                player.setDataSource(map.get(id));
                //准备
                player.prepare();
                //开始播放
                player.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        currentId = id;
        sendId();
        if (build != null) {
            updateRemoteViews();
        }
    }

    /**
     * 发送当前歌曲的ID
     */
    private void sendId() {
        Message message = Message.obtain();
        message.what = 0;
        message.arg1 = currentId;
        try {
            msg.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int getDuration(){
        //音频文件的总时长（单位：毫秒）
        return player.getDuration();
    }
    public int getCurrentPosition(){
        //当前时间（单位：毫秒）
        return player.getCurrentPosition();
    }

    /**
     * 改变音频当前时间
     * @param position
     */
    public void seekTo(int position){
        player.seekTo(position);
    }
    public boolean isPlaying(){
        return player.isPlaying();
    }

    /**
     * 上一首
     */
    public void playPrevious(){
        for (int i = 0; i < ids.length; i++) {
            if (currentId == ids[i]){
                int temp = i - 1;
                if (temp < 0){
                    temp = ids.length - 1;
                }
                playOrPause(ids[temp]);
                return;
            }
        }
    }

    /**
     * 下一首
     */
    public void playNext(){
        for (int i = 0; i < ids.length; i++) {
            if (currentId == ids[i]){
                int temp = i + 1;
                if (temp >= ids.length){
                    temp = 0;
                }
                playOrPause(ids[temp]);
                return;
            }
        }
    }

    /**
     * 解除绑定
     * @param intent
     * @return
     */
    @Override
    public boolean onUnbind(Intent intent) {
        //如果正在播放，发一个通知
        if (player.isPlaying()){
            //启动Activity的意图
            Intent intent1 = new Intent(this, MainActivity.class);
            //延时启动
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent1, PendingIntent.FLAG_ONE_SHOT);
            //自定义通知
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_layout);
            build = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher)
//                    .setContentTitle(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)))
//                    .setContentText(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)))
                    .setContentIntent(pendingIntent)
                    .setContent(views)
                    //点击后消失
                    .setAutoCancel(true)
                    .build();
            //给通知上的按钮加监听，当点击时发送广播
            views.setOnClickPendingIntent(R.id.btn_play,
                    PendingIntent.getBroadcast(this, 0, new Intent(CustomAction.ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT));
            views.setOnClickPendingIntent(R.id.btn_next,
                    PendingIntent.getBroadcast(this, 0, new Intent(CustomAction.ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT));
            views.setOnClickPendingIntent(R.id.btn_previous,
                    PendingIntent.getBroadcast(this, 0, new Intent(CustomAction.ACTION_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT));
            //大通知，API16以后用的
//            build.bigContentView = views;
            //不可以被用户取消 增加标记 |=
            build.flags |= Notification.FLAG_NO_CLEAR;
            //取消标记 &= ~
//            build.flags &= ~Notification.FLAG_NO_CLEAR;
            updateRemoteViews();
            //注册按钮发送广播的接收者
            IntentFilter filter = new IntentFilter(CustomAction.ACTION_PLAY);
            filter.addAction(CustomAction.ACTION_NEXT);
            filter.addAction(CustomAction.ACTION_PREVIOUS);
            registerReceiver(musicReceiver, filter);


        }
        return true;
    }

    /**
     * 更新通知
     */
    private void updateRemoteViews() {
        Cursor cursor = getContentResolver().query(
                //content://media/external/audio/media
                //追加一个路径
                Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentId + ""),
                null, null, null, null);
        cursor.moveToNext();
        build.contentView.setTextViewText(R.id.not_title,
                cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)));

        build.contentView.setTextViewText(R.id.not_text,
                cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)));
        build.contentView.setImageViewResource(R.id.btn_play, isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
//        views.setCharSequence(R.id.not_title, "setText",cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)));
        cursor.close();

        manager.notify("play", 0, build);
    }
}
