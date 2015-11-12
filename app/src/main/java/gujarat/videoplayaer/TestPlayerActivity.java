package gujarat.videoplayaer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;
import android.widget.Toast;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.text.SubtitleLayout;

import gujarat.videoplayaer.controller.TestPlayerController;

/**
 * Created by Gujarat Santana on 12/11/15.
 */
public class TestPlayerActivity extends AppCompatActivity {

    private String urlVideo ="YOUR VIDEO URL HERE";

    ///////////////////////////////////////
    /**
     * variable lib and engine
     */
    private MediaController mediaController;
    private SurfaceView surfaceView;
    private AspectRatioFrameLayout videoFrame;
    private SubtitleLayout subtitleLayout;



    //////////////////////////////////////
    /**
     * variable UI / layout
     */
    private View debugRootView;
    private View shutterView;
    private View root;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_player);

        initUI();
        initAction();
    }

    private void initUI(){
        //init ui variable
        root = findViewById(R.id.root);


        shutterView = findViewById(R.id.shutter);
        debugRootView = findViewById(R.id.controls_root);

        videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
        surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        subtitleLayout = (SubtitleLayout) findViewById(R.id.subtitles);
    }

    private void initAction(){
        //setup Video Engine
        TestPlayerController.getInstance().setEngineVideo(TestPlayerActivity.this,root,shutterView,surfaceView,videoFrame,subtitleLayout);
        //set root onTouchListener
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
//                    toggleControlsVisibility();
                    Toast.makeText(TestPlayerActivity.this, "ControlVisible", Toast.LENGTH_SHORT).show();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            }
        });
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                        || keyCode == KeyEvent.KEYCODE_MENU) {
                    return false;
                }
                return TestPlayerController.getInstance().getMediaController().dispatchKeyEvent(event);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        TestPlayerController.getInstance().setOnResumeVideo(TestPlayerActivity.this,urlVideo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        TestPlayerController.getInstance().setOnPauseVideo(shutterView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TestPlayerController.getInstance().setOnDestroyVideo();
    }
}
