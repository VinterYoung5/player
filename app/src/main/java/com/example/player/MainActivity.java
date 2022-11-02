package com.example.player;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    public static final int SEEK_PREVIOUS_SYNC    = 0x00;
    public static final int SEEK_NEXT_SYNC        = 0x01;
    public static final int SEEK_CLOSEST_SYNC     = 0x02;
    public static final int SEEK_CLOSEST          = 0x03;
    private static final String TAG = "Yangwen";
    private SurfaceView surfaceView;
    private MediaPlayer player;
    private SurfaceHolder holder;
    private ImageButton btn_start, btn_stop ,btn_pause;
    private Context context;
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
    private playState mState = playState.STOPED;
    enum playMode {
        modeMediaplayer,
        modeMediacodec,
        modeFFmpeg,
        modeEnd;
    }
    playMode mPlayMode = playMode.modeMediaplayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Permission permission = new Permission();
        permission.checkPermissions(this);
        initView();
        initPlayer();
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
        btn_start = findViewById(R.id.imageButton_start);
        btn_stop  = findViewById(R.id.imageButton_stop);
        btn_pause = findViewById(R.id.imageButton_pause);
        context = getApplicationContext();
        btn_start.setOnClickListener(new MyOnClickListener());
        btn_stop.setOnClickListener(new MyOnClickListener());
        btn_pause.setOnClickListener(new MyOnClickListener());
    }

    private void initPlayer(){
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        player = new MediaPlayer();
        holder = surfaceView.getHolder();
        holder.addCallback(new MyCallBack());
        mState = playState.IDLE;
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG,"onPrepared");
                mState = playState.PREPARED;
                player.start();
                Log.d(TAG,"player.start()ed " + mState);
                mState = playState.STARTED;
                player.setLooping(false);
            };
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG,"onCompletion");
                mState = playState.PLAYBACKCOMPLETED;
                player.seekTo(0, SEEK_PREVIOUS_SYNC);
                player.start();
                mState = playState.STARTED;
                Toast.makeText(MainActivity.this,"onCompletion "+ mState,Toast.LENGTH_SHORT).show();
            }
        });
        player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener(){
            @Override
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height){
                Log.d(TAG,"onVideoSizeChanged, w:" +  width+" h:" + height);
                //mState = playState.PREPARED;
            }
        });
        player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener(){
            @Override
            public void onSeekComplete(MediaPlayer mp){
                Log.d(TAG,"onSeekComplete");
                mState = playState.PLAYBACKCOMPLETED;
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
    //定义按钮点击监听器
    class MyOnClickListener implements View.OnClickListener{
        @Override
        public void onClick(View view) {
            if (view.getId()==R.id.imageButton_start) {
                playerStart();
                Toast.makeText(MainActivity.this,"onClick start",Toast.LENGTH_SHORT).show();
            } else if (view.getId()==R.id.imageButton_stop) {
                playerStop();
                Toast.makeText(MainActivity.this,"onClick stop",Toast.LENGTH_SHORT).show();
            } else if (view.getId()==R.id.imageButton_pause) {
                playerPause();
                Toast.makeText(MainActivity.this,"onClick pause",Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class MyCallBack implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            player.setDisplay(holder);
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) { }
    }

    private void playerStart() {

        Log.d(TAG,"playerStart " + mState);
        if (mState == playState.STARTED) {
            return;
        } else if (mState == playState.PAUSED){
            player.start();
            Log.d(TAG,"player.start()ed " + mState);
            mState = playState.STARTED;
        } else if (mState == playState.IDLE) {

            if (mPlayMode == playMode.modeMediaplayer) {
                try {
                    //String uri = "/storage/emulated/0/test.mp4";
                    //player.setDataSource(uri);
                    AssetFileDescriptor fileDescriptor = getAssets().openFd("test.mp4");
                    player.setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(), fileDescriptor.getLength());
                    player.prepare();
                    mState = playState.PREPARING;

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Toast.makeText(MainActivity.this,"playerStart error state",Toast.LENGTH_SHORT).show();
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
        Log.d(TAG,"playerstop " + mState);
        if (mState == playState.IDLE && mState != playState.STOPED) {
            return;
        }
        player.stop();
        mState = playState.STOPED;
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

}