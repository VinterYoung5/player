package com.vinter.media;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.ImageButton;

import java.io.IOException;
import android.widget.SeekBar;
import android.widget.TextView;

import com.vinter.media.R;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_SELECT_VIDEO = 0x100;
    private static final int MSG_UPDATE_PLAYPOSITION = 0x1000;
    private static final String TAG = "Vmedia";
    private  String url;
    private SurfaceView surfaceView;
    private MediaPlayer player;
    private SurfaceHolder holder;
    private ImageButton btn_open, btn_start, btn_stop ,btn_pause;
    private Context context;
    private SeekBar seekbar;
    private TextView tv_playpos,tv_seekpos,tv_filedur;
    public int filedurtion;
    enum playState {
        IDLE,
        PREPARING,
        PREPARED,
        STARTED,
        PAUSED,
        PLAYBACKCOMPLETED,
        STOPED,
        ERROR;
    };
    private playState mState = playState.IDLE;
    enum playMode {
        modeMediaplayer,
        modeMediacodec,
        modeFFmpeg;
    }
    playMode mPlayMode = playMode.modeMediaplayer;
    enum cycleMode  {
        cycleNone,
        cycleSingle,
        cycleAll;
    }
    private cycleMode cyclePlay = cycleMode.cycleNone;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Permission permission = new Permission();
        permission.checkPermissions(this);
        initView();
        //initPlayer();
        new Thread(new playerRunnable()).start();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        destoryPlayer();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Permission.RequestCode) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG,"Permission:" + permissions[i]+ "denied grantResults:" + grantResults[i]);
                }else {
                    Log.d(TAG,"Permission:" + permissions[i]+ "granted grantResults:" + grantResults[i]);
                }
            }
        }
    }
    private void initView(){
        btn_open  = findViewById(R.id.imageButton_open);
        btn_start = findViewById(R.id.imageButton_start);
        btn_stop  = findViewById(R.id.imageButton_stop);
        btn_pause = findViewById(R.id.imageButton_pause);
        tv_playpos = findViewById(R.id.textView_playpos);
        tv_seekpos = findViewById(R.id.textView_seekpos);
        tv_filedur = findViewById(R.id.textView_filedurtion);
        seekbar = findViewById(R.id.seekbar);
        context = getApplicationContext();
        btn_start.setOnClickListener(new MyOnClickListener());
        btn_stop.setOnClickListener(new MyOnClickListener());
        btn_pause.setOnClickListener(new MyOnClickListener());
        btn_open.setOnClickListener(new MyOnClickListener());
        seekbar.setOnSeekBarChangeListener(new MyOnSeekBarChangeListener());
        seekbar.setVisibility(View.INVISIBLE);
        tv_seekpos.setVisibility(View.INVISIBLE);
    }
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PLAYPOSITION:
                    int curposition = player.getCurrentPosition() / 1000; //get second
                    int hours = (int) (curposition / 3600);
                    int mins = (int)(curposition % 3600) / 60;
                    int secs = (int)(curposition % 3600) % 60;
                    tv_playpos.setText((hours < 10 ? "0":"") + hours +":"+ (mins < 10 ? "0":"") + mins +":"+ (secs < 10 ? "0":"") + secs);
                    Log.d(TAG,"curposition " + curposition);

                    seekbar.setProgress(curposition*1000/filedurtion);

                    break;
                case 1:
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    private void initPlayer(){
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        player = new MediaPlayer();
        holder = surfaceView.getHolder();
        holder.setKeepScreenOn(true);
        //holder.addCallback(new MyCallBack());
        player.setDisplay(holder);
        //mState = playState.IDLE;
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG,"onPrepared");
                mState = playState.PREPARED;
                player.start();
                Log.d(TAG,"player.start()ed " + mState);
                mState = playState.STARTED;
                player.setLooping(false);
                filedurtion = player.getDuration()/1000;// second
                int hours = (int) (filedurtion / 3600);
                int mins = (int)(filedurtion % 3600) / 60;
                int secs = (int)(filedurtion % 3600) % 60;
                tv_filedur.setText((hours < 10 ? "0":"") + hours +":"+ (mins < 10 ? "0":"") + mins +":"+ (secs < 10 ? "0":"") + secs);
            };
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG,"onCompletion");
                mState = playState.PLAYBACKCOMPLETED;
                if (cyclePlay == cycleMode.cycleSingle) {
                    player.seekTo(0, MediaPlayer.SEEK_PREVIOUS_SYNC);
                    player.start();
                    mState = playState.STARTED;
                } else if (cyclePlay == cycleMode.cycleNone) {
                    playerStop();
                }
                //Toast.makeText(MainActivity.this,"onCompletion "+ mState,Toast.LENGTH_SHORT).show();
            }
        });
        player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener(){
            @Override
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height){
                //Toast.makeText(MainActivity.this,"onVideoSizeChanged, w:" +  width+" h:" + height,Toast.LENGTH_SHORT).show();
                setSurfaceViewSize(width, height);
            }
        });
        player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener(){
            @Override
            public void onSeekComplete(MediaPlayer mp){
                Log.d(TAG,"onSeekComplete");
                mState = playState.PAUSED;
                playerPause();
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener(){
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.d(TAG,"onError");
                mState = playState.ERROR;
                player.reset();
                mState = playState.IDLE;
                return true;
            }
        });
    }
    private void destoryPlayer(){
        if (player != null) {
            player.stop();
            player.reset();
            player.release();
            player = null;
        }
    }

    class MyOnClickListener implements View.OnClickListener{
        @Override
        public void onClick(View view) {
            if (view.getId()==R.id.imageButton_start) {
                playerStart();
                //Toast.makeText(MainActivity.this,"onClick start",Toast.LENGTH_SHORT).show();
            } else if (view.getId()==R.id.imageButton_stop) {
                playerStop();
                //Toast.makeText(MainActivity.this,"onClick stop",Toast.LENGTH_SHORT).show();
            } else if (view.getId()==R.id.imageButton_pause) {
                playerPause();
                //Toast.makeText(MainActivity.this,"onClick pause",Toast.LENGTH_SHORT).show();
            } else if (view.getId()==R.id.imageButton_open){
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                intent.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*");
                startActivityForResult(intent, REQUEST_CODE_SELECT_VIDEO);
            }
        }
    }
    class MyOnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        private int seek_postion;
        @Override
        public void onProgressChanged (SeekBar seekbar,int progress, boolean fromUser){
            seek_postion = progress * filedurtion;
            int postion = seek_postion / 1000;
            int hours = (int) (postion / 3600);
            int mins = (int)(postion % 3600) / 60;
            int secs = (int)(postion % 3600) % 60;
            tv_seekpos.setText((hours < 10 ? "0":"") + hours +":"+ (mins < 10 ? "0":"") + mins +":"+ (secs < 10 ? "0":"") + secs);
            //tv_seekpos.setText("seek time: " + progress +" ‰");
            //tv_playpos.setText("00:00:01");
            //tv_filedur.setText(""+filedurtion);
        }
        @Override
        public void onStartTrackingTouch (SeekBar seekbar){
            tv_seekpos.setVisibility(View.VISIBLE);
        }

        @Override
        public void onStopTrackingTouch (SeekBar seekbar){
            tv_seekpos.setVisibility(View.INVISIBLE);
            player.pause();
            player.seekTo(seek_postion);
            player.start();
            Log.d(TAG,"seek to "+ seek_postion);
        }
    }

    @SuppressLint("Range")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_SELECT_VIDEO) {
                Uri uri = data.getData();
                if (uri != null) {
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null) {
                        cursor.moveToFirst();

                        url = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA));
                        if (url != null) {
                            //mEdit_file_path.setText(url);
                        }
                        @SuppressLint("Range")
                        long size = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.SIZE));
                        @SuppressLint("Range")
                        long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.DURATION));

                        @SuppressLint("Range")
                        int width = cursor.getInt(cursor.getColumnIndex(MediaStore.Video.Media.WIDTH));
                        @SuppressLint("Range")
                        int height = cursor.getInt(cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT));
                        //mText_resolution.setText(width + "x" + height);
                        //mText_duration.setText(getStrTime(duration));
                        //mText_size.setText(getVideoSize(size));
                        //mLinerLayout_video_infomation.setVisibility(View.VISIBLE);
                        //Toast.makeText(MainActivity.this, "onClick url :" + url, Toast.LENGTH_SHORT).show();
                        cursor.close();
                    }
                }
            }
        }
    }

    private class MyCallBack implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            //player.setDisplay(holder);
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) { }
    }
    private void setSurfaceViewSize(int videoWidth, int videoHeight){

        DisplayMetrics dm = getResources().getDisplayMetrics();
        System.out.println("heigth2 : " + dm.heightPixels);
        System.out.println("width2 : " + dm.widthPixels);
        //int surfaceWidth = surfaceView.getWidth();
        //int surfaceHeight = surfaceView.getHeight();
        int surfaceWidth = dm.widthPixels;
        int surfaceHeight = dm.heightPixels;
                //Toast.makeText(MainActivity.this,"onVideoSizeChanged, w:" +  surfaceWidth+" h:" + surfaceHeight+" orien:"+getResources().getConfiguration().orientation,Toast.LENGTH_SHORT).show();
        Log.d(TAG,"onVideoSizeChanged, w:" +  surfaceWidth+" h:" + surfaceHeight
                +" match_parent "+ConstraintLayout.LayoutParams.MATCH_PARENT
                +" orien:"+getResources().getConfiguration().orientation);
        //MediaFormat.KEY_ROTATION
        float max;
        if (getResources().getConfiguration().orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            max = Math.max((float) videoWidth / (float) surfaceWidth, (float) videoHeight / (float) surfaceHeight);
        } else {
            max = Math.max(((float) videoWidth / (float) surfaceHeight), (float) videoHeight / (float) surfaceWidth);
        }
        int displayWidth = (int) Math.ceil((float) videoWidth / max);
        int displayHeight = (int) Math.ceil((float) videoHeight / max);
        ConstraintLayout.LayoutParams params2 = new ConstraintLayout.LayoutParams(displayWidth, displayHeight);

        surfaceView.setLayoutParams(params2);
    }
    private void playerStart() {
        seekbar.setVisibility(View.VISIBLE);
        Log.d(TAG,"playerStart " + mState);
        if (mState == playState.STARTED) {
            return;
        } else if (mState == playState.PAUSED){
            player.start();
            Log.d(TAG,"player.start()ed " + mState);
            mState = playState.STARTED;

        } else if (mState == playState.IDLE) {

            if (mPlayMode == playMode.modeMediaplayer) {
                initPlayer();
                try {
                    //String url = "/storage/emulated/0/test.mp4";
                    //player.setDataSource(url);
                    if (url == null) {
                        AssetFileDescriptor fileDescriptor = getAssets().openFd("test.mp4");
                        player.setDataSource(fileDescriptor.getFileDescriptor(), fileDescriptor.getStartOffset(), fileDescriptor.getLength());
                    } else {
                        player.setDataSource(url);
                    }
                    player.prepare();
                    mState = playState.PREPARING;


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            //Toast.makeText(MainActivity.this,"playerStart error state",Toast.LENGTH_SHORT).show();
        }
    }
/*
    private void playerStart() {
        Log.d(TAG,"playerstart " + mState);
        if (mState != playState.IDLE) {
            playerPrepare();
        }
        if (mState != playState.PREPARED && mState != playState.PAUSED &&
            mState != playState.STOPED && mState != playState.PLAYBACKCOMPLETED) {
            return;
        }


    }
*/
    private void playerStop() {
        seekbar.setVisibility(View.INVISIBLE);

        Log.d(TAG,"playerStop " + mState);
        if (mState == playState.IDLE && mState != playState.STOPED) {
            return;
        }
        player.stop();
        player.reset();
        player.release();
        mState = playState.IDLE;
    }

    private void playerPause(){
        Log.d(TAG,"playerPause " + mState);
        if (mState != playState.STARTED) {
            return;
        }
        player.pause();
        mState = playState.PAUSED;
    }
    private class playerRunnable implements Runnable {
        long cur_time;
        long last_time = System.currentTimeMillis();
        @Override
        public void run() {
            while (true) {
                Log.d(TAG, "mState: "+mState);
                if (mState == playState.STARTED || mState == playState.PAUSED) {
                    cur_time = System.currentTimeMillis();
                    if (cur_time - last_time > 500) {
                        last_time = cur_time;
                        handler.sendEmptyMessage(MSG_UPDATE_PLAYPOSITION);
                    }
                } else if (mState == playState.STOPED) {
                    break;
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}