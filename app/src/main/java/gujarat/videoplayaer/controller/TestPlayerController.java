package gujarat.videoplayaer.controller;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.MediaController;
import android.widget.Toast;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.Util;

import java.util.List;
import java.util.Map;

import gujarat.videoplayaer.R;
import gujarat.videoplayaer.player.DemoPlayer;
import gujarat.videoplayaer.player.ExtractorRendererBuilder;

/**
 * Created by Gujarat Santana on 12/11/15.
 */
public class TestPlayerController implements DemoPlayer.Listener, DemoPlayer.CaptionListener, DemoPlayer.Id3MetadataListener{

    public static TestPlayerController instance;
    private MediaController mediaController;
    private SurfaceView surfaceView;
    private DemoPlayer player;
    private AspectRatioFrameLayout videoFrame;
    private SubtitleLayout subtitleLayout;
    private Uri contentUri;
    private String urlVideo;
    private boolean playerNeedsPrepare;
    private long playerPosition;
    private boolean enableBackgroundAudio;
    private Context context;
    private View shutterView;
    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;

    public TestPlayerController(){
    }

    public static TestPlayerController getInstance(){
        if(instance==null)
            instance = new TestPlayerController();
        return instance;
    }

    public void setEngineVideo(final Context context,View root, View shutterView,SurfaceView surfaceView,AspectRatioFrameLayout videoFrame,SubtitleLayout subtitleLayout){
        //ini variable
        this.context =context;
        this.videoFrame = videoFrame;
        this.subtitleLayout = subtitleLayout;
        this.surfaceView = surfaceView;
        this.shutterView = shutterView;

        // init engine video
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback(){

            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                if (player != null) {
                    player.setSurface(surfaceHolder.getSurface());
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                if (player != null) {
                    player.blockingClearSurface();
                }
            }
        });
        mediaController = new MediaController(context);
        mediaController.setAnchorView(root);
        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(context, new AudioCapabilitiesReceiver.Listener(){

            @Override
            public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
                if (player == null) {
                    return;
                }
                boolean backgrounded = player.getBackgrounded();
                boolean playWhenReady = player.getPlayWhenReady();
                releasePlayer();
                preparePlayer(context,playWhenReady);
                player.setBackgrounded(backgrounded);
            }
        });
        audioCapabilitiesReceiver.register();

    }


    public MediaController getMediaController(){
        return mediaController;
    }

    public void setOnResumeVideo(Context context,String urlVideo){
        contentUri = Uri.parse(urlVideo);
        this.urlVideo = urlVideo;
//        contentType = intent.getIntExtra(CONTENT_TYPE_EXTRA,
//                inferContentType(contentUri, intent.getStringExtra(CONTENT_EXT_EXTRA)));
//        contentId = intent.getStringExtra(CONTENT_ID_EXTRA);
        configureSubtitleView(context);
        if (player == null) {
            preparePlayer(context,true);
        } else {
            player.setBackgrounded(false);
        }
    }

    public void setOnPauseVideo(View shutterView){
        if (!enableBackgroundAudio) {
            releasePlayer();
        } else {
            player.setBackgrounded(true);
        }

        shutterView.setVisibility(View.VISIBLE);
    }

    public void setOnDestroyVideo(){
        audioCapabilitiesReceiver.unregister();
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
//            debugViewHelper.stop();
//            debugViewHelper = null;
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
//            eventLogger.endSession();
//            eventLogger = null;
        }
    }

    private void configureSubtitleView(Context context) {
        CaptionStyleCompat style;
        float fontScale;
        if (Util.SDK_INT >= 19) {
            style = getUserCaptionStyleV19(context);
            fontScale = getUserCaptionFontScaleV19(context);
        } else {
            style = CaptionStyleCompat.DEFAULT;
            fontScale = 1.0f;
        }
        subtitleLayout.setStyle(style);
        subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
    }

    public void onNewIntent(Activity activity,Intent intent){
        releasePlayer();
        playerPosition = 0;
        activity.setIntent(intent);
    }

    private void preparePlayer(Context context,boolean playWhenReady) {
        if (player == null) {
            player = new DemoPlayer(getRendererBuilder(context));
            player.addListener(this);
            player.setCaptionListener(this);
            player.setMetadataListener(this);
            player.seekTo(playerPosition);
            playerNeedsPrepare = true;
            mediaController.setMediaPlayer(player.getPlayerControl());
            mediaController.setEnabled(true);
//            eventLogger = new EventLogger();
//            eventLogger.startSession();
//            player.addListener(eventLogger);
//            player.setInfoListener(eventLogger);
//            player.setInternalErrorListener(eventLogger);
//            debugViewHelper = new DebugTextViewHelper(player, debugTextView);
//            debugViewHelper.start();
        }
        if (playerNeedsPrepare) {
            player.prepare();
            playerNeedsPrepare = false;
//            updateButtonVisibilities();
        }
        player.setSurface(surfaceView.getHolder().getSurface());
        player.setPlayWhenReady(playWhenReady);
    }

    private void showControls() {
        mediaController.show(0);
//        debugRootView.setVisibility(View.VISIBLE);
    }


    @TargetApi(19)
    private float getUserCaptionFontScaleV19(Context context) {
        CaptioningManager captioningManager =
                (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        return captioningManager.getFontScale();
    }

    @TargetApi(19)
    private CaptionStyleCompat getUserCaptionStyleV19(Context context) {
        CaptioningManager captioningManager =
                (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
    }

    private DemoPlayer.RendererBuilder getRendererBuilder(Context context) {
        String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
        return new ExtractorRendererBuilder(context, userAgent, contentUri);
//        return new SmoothStreamingRendererBuilder(context, userAgent, contentUri.toString(),new SmoothStreamingTestMediaDrmCallback());

    }

    public void toggleControlsVisibility()  {
        if (mediaController.isShowing()) {
            mediaController.hide();
        } else {
            showControls();
        }
    }

    @Override
    public void onCues(List<Cue> cues) {
        subtitleLayout.setCues(cues);
    }

    @Override
    public void onId3Metadata(Map<String, Object> metadata) {

    }

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
    }

    @Override
    public void onError(Exception e) {
        if (e instanceof UnsupportedDrmException) {
            // Special case DRM failures.
            UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
            int stringId = Util.SDK_INT < 18 ? R.string.drm_error_not_supported
                    : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                    ? R.string.drm_error_unsupported_scheme : R.string.drm_error_unknown;
            Toast.makeText(context, stringId, Toast.LENGTH_LONG).show();
        }
        playerNeedsPrepare = true;
        showControls();
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            shutterView.setVisibility(View.GONE);
            videoFrame.setAspectRatio(
                    height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
        }


//    @Override
//    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
//        if (player == null) {
//            return;
//        }
//        boolean backgrounded = player.getBackgrounded();
//        boolean playWhenReady = player.getPlayWhenReady();
//        releasePlayer();
//        preparePlayer(context,playWhenReady);
//        player.setBackgrounded(backgrounded);
//    }
}
