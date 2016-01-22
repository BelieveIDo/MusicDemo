package com.jash.musicdemo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements SimpleCursorAdapter.ViewBinder, ServiceConnection, AdapterView.OnItemClickListener, Handler.Callback, View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private static final String TAG = "MainActivity";
    private Intent intent;
    private MusicService service;
    private Handler handler = new Handler(this);
    private SimpleCursorAdapter adapter;
    private int currentId = -1;
    private ImageButton play_btn;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("mm:ss", Locale.CHINA);
    static {
        SDF.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    private TextView time;
    private SeekBar seek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Audio.Media.IS_MUSIC, null, null);
//        while (cursor.moveToNext()){
//            StringBuilder builder = new StringBuilder();
//            for (int i = 0; i < cursor.getColumnCount(); i++) {
//                builder.append(cursor.getColumnName(i)).append(':').append(cursor.getString(i)).append(',');
//            }
//            Log.d(TAG, builder.toString());
//        }
        ListView list = (ListView) findViewById(R.id.list);
        adapter = new SimpleCursorAdapter(this, R.layout.item, cursor, new String[]{
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media._ID
        }, new int[]{
                R.id.item_title,
                R.id.item_album,
                R.id.item_image,
                R.id.current
        }, SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        adapter.setViewBinder(this);

        list.setAdapter(adapter);
        intent = new Intent(this, MusicService.class);
        int[] ids = new int[cursor.getCount()];
        String[] dates = new String[cursor.getCount()];
        for (int i = 0; i < ids.length; i++) {
            cursor.moveToPosition(i);
            ids[i] = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
            dates[i] = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
        }
        intent.putExtra("ids", ids);
        intent.putExtra("dates", dates);
        intent.putExtra("msg", new Messenger(handler));
        startService(intent);
        bindService(intent, this, BIND_AUTO_CREATE);
        list.setOnItemClickListener(this);
        findViewById(R.id.btn_next).setOnClickListener(this);
        findViewById(R.id.btn_play).setOnClickListener(this);
        findViewById(R.id.btn_previous).setOnClickListener(this);
        play_btn = ((ImageButton) findViewById(R.id.btn_play));
        time = ((TextView) findViewById(R.id.text_time));
        seek = ((SeekBar) findViewById(R.id.seek));
        seek.setOnSeekBarChangeListener(this);
    }

    /**
     * 把数据绑定到View上
     * @param view
     * @param cursor
     * @param columnIndex
     * @return 是否完成绑定
     */
    @Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        switch (view.getId()){
            case R.id.item_image:
                ImageView image = (ImageView) view;
                //查寻专辑库
                Cursor query = getContentResolver().query(
                        Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                                cursor.getString(columnIndex)),
                        null, null, null, null);
                String art = null;
                //检查有没有查到数据
                if (query != null && query.getColumnCount() > 0){
                    query.moveToNext();
                    art = query.getString(query.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
                    query.close();
                }
                if (!TextUtils.isEmpty(art)) {
                    image.setImageBitmap(BitmapFactory.decodeFile(art));
                } else {
                    image.setImageResource(R.mipmap.ic_launcher);
                }
                return true;
            case R.id.current:
                view.setVisibility(cursor.getInt(columnIndex) == currentId ? View.VISIBLE : View.INVISIBLE);
                return true;
        }
        return false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = ((MusicService.MusicBinder) service).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        service.playOrPause((int) id);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what){
            case 0:
                currentId = msg.arg1;
                adapter.notifyDataSetChanged();
                play_btn.setImageResource(service.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                if (service.isPlaying()){
                    handler.sendEmptyMessage(1);
                }
                break;
            case 1:
                //格式化 当前时间/总时长
                time.setText(String.format("%s/%s",
                        SDF.format(new Date(service.getCurrentPosition())),
                        SDF.format(new Date(service.getDuration()))));
                //让seekBar跟随音频移动
                seek.setProgress(service.getCurrentPosition() * seek.getMax() / service.getDuration());

                if (service.isPlaying()){
                    handler.sendEmptyMessageDelayed(1, 500);
                }
                break;
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_previous:
                service.playPrevious();
                break;
            case R.id.btn_play:
                service.playOrPause(currentId);
                break;
            case R.id.btn_next:
                service.playNext();
                break;
        }
    }

    /**
     * 进度改变
     * @param seekBar
     * @param progress 改变后的进度
     * @param fromUser 是否来自于用户
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // 进度/最大值 = 当前时间/总时长
        if (fromUser) {
            service.seekTo(progress * service.getDuration() / seekBar.getMax());
        }
    }

    /**
     * 开始触摸
     * @param seekBar
     */
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    /**
     * 停止触摸
     * @param seekBar
     */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
