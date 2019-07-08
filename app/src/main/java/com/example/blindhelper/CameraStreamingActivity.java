package com.example.blindhelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;


public class CameraStreamingActivity extends Activity implements IVLCVout.Callback {
    public final static String TAG = "MainActivity";
    private String mFilePath;
    private SurfaceView mSurface;
    private SurfaceHolder holder;
    private LibVLC libvlc;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;

    private WifiManager wifi ;
    Integer count = 0;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_stream);



        utils.loadFFmpeg(getApplicationContext());
        Button restart = (Button)findViewById(R.id.startPreview);
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Stream();
                createPlayer(mFilePath);
            }
        });
        Button stop = (Button)findViewById(R.id.stoppreviewbtn);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FFmpeg ffmpeg = FFmpeg.getInstance(getApplicationContext());
                if(ffmpeg.isFFmpegCommandRunning()){
                    ffmpeg.killRunningProcesses();
                }
                releasePlayer();
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        String name = wifiInfo.getSSID();
        if(!(name.equals("\"GP24992562\""))){
            AlertDialog.Builder buildTight = new AlertDialog.Builder(this);
            buildTight.setMessage("Please connect to GP24992562 wifi");
            buildTight.setCancelable(true);

            buildTight.setPositiveButton(
                    "Back",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            Intent home = new Intent(CameraStreamingActivity.this, FirstActivity.class);
                            startActivity(home);
                        }
                    });
            AlertDialog alertTight = buildTight.create();
            alertTight.show();
        }
        if (!wifi.isWifiEnabled()) {
            AlertDialog.Builder buildTight = new AlertDialog.Builder(this);
            buildTight.setMessage("Please enable wifi and connect it to your GoPro");
            buildTight.setCancelable(true);

            buildTight.setPositiveButton(
                    "Back",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            Intent home = new Intent(CameraStreamingActivity.this, FirstActivity.class);
                            startActivity(home);
                        }
                    });
            AlertDialog alertTight = buildTight.create();
            alertTight.show();
        }
        Stream();

        mFilePath = "udp://@:8555/gopro";
        Log.d(TAG, "Playing: " + mFilePath);
        mSurface = (SurfaceView) findViewById(R.id.surface);
        holder = mSurface.getHolder();
    }
    void Stream(){

        //Call http://10.5.5.9/gp/gpControl/execute?p1=gpStream&a1=proto_v2&c1=restart

        utils.callHTTP(getApplicationContext());
        try {
            //String[] cmd = {"-fflags", "nobuffer", "-f", "mpegts", "-i", "udp://:8554", "-f", "mpegts","udp://127.0.0.1:8555/gopro?pkt_size=64"};

            String[] cmd = {"-loglevel", "panic","-fflags", "nobuffer", "-f", "mpegts", "-i", "udp://:8554", "-f", "mpegts","udp://127.0.0.1:8555/gopro?pkt_size=64"};
            //String[] cmd = {"-f", "mpegts", "-i", "udp://:8554", "-f", "mpegts","udp://127.0.0.1:8555/gopro?pkt_size=64"};
            FFmpeg ffmpeg = FFmpeg.getInstance(getApplicationContext());

            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @Override
                public void onStart() {
                    count += 1;
                    if(count == 7){
                        count = 0;
                        new utils.sendAsyncMagicPacket().execute();
                    }
                }

                @Override
                public void onProgress(String message) {
                    Log.d("FFmpeg",message);

                    //l'appel de callHTTP permet de maintenir la video en vie, mais ralentit le système.
                   utils.callHTTP(getApplicationContext());
                   // utils.keepAlive(getApplicationContext()); //marche po
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(getApplicationContext(),"Stream fail", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onSuccess(String message) {}

                @Override
                public void onFinish() {}
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
        }
        //Preview();
        createPlayer(mFilePath);
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(mVideoWidth, mVideoHeight);

    }

    @Override
    protected void onResume() {
        super.onResume();
        createPlayer(mFilePath);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }


    /**
     * Used to set size for SurfaceView
     *
     * @param width
     * @param height
     */

    private void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1)
            return;

        if (holder == null || mSurface == null)
            return;

        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        holder.setFixedSize(mVideoWidth, mVideoHeight);
        ViewGroup.LayoutParams lp = mSurface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();
        if (width * height == 0)
            return;

        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        setSize(mVideoWidth, mVideoHeight);
    }

    /**
     * Creates MediaPlayer and plays video
     *
     * @param media
     */
    private void createPlayer(String media) {
        releasePlayer();
        try {
            if (media.length() > 0) {
                Toast toast = Toast.makeText(this, media, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create LibVLC
            // TODO: make this more robust, and sync with audio demo
            ArrayList<String> options = new ArrayList<String>();
            //options.add("--subsdec-encoding <encoding>");
            options.add("--aout=opensles");
            options.add("--audio-time-stretch"); // time stretching
            options.add("-vvv"); // verbosity
            libvlc = new LibVLC(this, options);
            holder.setKeepScreenOn(true);

            // Creating media player
            mMediaPlayer = new MediaPlayer(libvlc);
            mMediaPlayer.setEventListener(mPlayerListener);

            // Seting up video output
            final IVLCVout vout = mMediaPlayer.getVLCVout();
            vout.setVideoView(mSurface);
            //vout.setSubtitlesView(mSurfaceSubtitles);
            vout.addCallback(this);
            vout.attachViews();

            Media m = new Media(libvlc, Uri.parse(media));
            mMediaPlayer.setMedia(m);
            mMediaPlayer.play();
        } catch (Exception e) {
            Toast.makeText(this, "Loading GoPro streaming...", Toast
                    .LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.removeCallback(this);
        vout.detachViews();
        holder = null;
        libvlc.release();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    /**
     * Registering callbacks
     */
    private MediaPlayer.EventListener mPlayerListener = new MyPlayerListener(this);



    @Override
    public void onSurfacesCreated(IVLCVout vout) {

    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vout) {

    }


    private static class MyPlayerListener implements MediaPlayer.EventListener {
        private WeakReference<CameraStreamingActivity> mOwner;

        public MyPlayerListener(CameraStreamingActivity owner) {
            mOwner = new WeakReference<CameraStreamingActivity>(owner);
        }

        @Override
        public void onEvent(MediaPlayer.Event event) {
            CameraStreamingActivity player = mOwner.get();

            switch (event.type) {
                case MediaPlayer.Event.EndReached:
                    Log.d(TAG, "MediaPlayerEndReached");
                    player.releasePlayer();
                    break;
                case MediaPlayer.Event.Playing:
                case MediaPlayer.Event.Paused:
                case MediaPlayer.Event.Stopped:
                default:
                    break;
            }
        }
    }

}