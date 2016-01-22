package com.jash.musicdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 为了让通知可以控制音乐
 */
public class MusicReceiver extends BroadcastReceiver {
    private static final String TAG = "MusicReceiver";
    private MusicService service;

    public MusicReceiver(MusicService service) {
        this.service = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()){
            case CustomAction.ACTION_PLAY:
                service.playOrPause();
                break;
            case CustomAction.ACTION_NEXT:
                service.playNext();
                break;
            case CustomAction.ACTION_PREVIOUS:
                service.playPrevious();
                break;
        }
    }
}
