/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gujarat.videoplayaer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.accessibility.CaptioningManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.metadata.GeobMetadata;
import com.google.android.exoplayer.metadata.PrivMetadata;
import com.google.android.exoplayer.metadata.TxxxMetadata;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.DebugTextViewHelper;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;
import com.google.android.exoplayer.util.VerboseLogUtil;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gujarat.videoplayaer.player.DashRendererBuilder;
import gujarat.videoplayaer.player.DemoPlayer;
import gujarat.videoplayaer.player.ExtractorRendererBuilder;
import gujarat.videoplayaer.player.HlsRendererBuilder;
import gujarat.videoplayaer.player.SmoothStreamingRendererBuilder;

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback, OnClickListener,
        DemoPlayer.Listener, DemoPlayer.CaptionListener, DemoPlayer.Id3MetadataListener,
        AudioCapabilitiesReceiver.Listener {

  // For use within demo app code.
  public static final String CONTENT_ID_EXTRA = "content_id";
  public static final String CONTENT_TYPE_EXTRA = "content_type";
  public static final int TYPE_DASH = 0;
  public static final int TYPE_SS = 1;
  public static final int TYPE_HLS = 2;
  public static final int TYPE_OTHER = 3;

  // For use when launching the demo app using adb.
  private static final String CONTENT_EXT_EXTRA = "type";
  private static final String EXT_DASH = ".mpd";
  private static final String EXT_SS = ".ism";
  private static final String EXT_HLS = ".m3u8";

  private static final String TAG = "PlayerActivity";
  private static final int MENU_GROUP_TRACKS = 1;
  private static final int ID_OFFSET = 2;

  private static final CookieManager defaultCookieManager;
  static {
    defaultCookieManager = new CookieManager();
    defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private EventLogger eventLogger;
  private MediaController mediaController;
  private View debugRootView;
  private View shutterView;
  private AspectRatioFrameLayout videoFrame;
  private SurfaceView surfaceView;
  private TextView debugTextView;
  private TextView playerStateTextView;
  private SubtitleLayout subtitleLayout;
  private Button videoButton;
  private Button audioButton;
  private Button textButton;
  private Button retryButton;

  private DemoPlayer player;
  private DebugTextViewHelper debugViewHelper;
  private boolean playerNeedsPrepare;

  private long playerPosition;
  private boolean enableBackgroundAudio;

  private Uri contentUri;
  private int contentType;
  private String contentId;

  private AudioCapabilitiesReceiver audioCapabilitiesReceiver;

  // Activity lifecycle

  private static String buildTrackName(MediaFormat format) {
    if (format.adaptive) {
      return "auto";
    }
    String trackName;
    if (MimeTypes.isVideo(format.mimeType)) {
      trackName = joinWithSeparator(joinWithSeparator(buildResolutionString(format),
              buildBitrateString(format)), buildTrackIdString(format));
    } else if (MimeTypes.isAudio(format.mimeType)) {
      trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                      buildAudioPropertyString(format)), buildBitrateString(format)),
              buildTrackIdString(format));
    } else {
      trackName = joinWithSeparator(joinWithSeparator(buildLanguageString(format),
              buildBitrateString(format)), buildTrackIdString(format));
    }
    return trackName.length() == 0 ? "unknown" : trackName;
  }

private static String buildResolutionString(MediaFormat format) {
    return format.width == MediaFormat.NO_VALUE || format.height == MediaFormat.NO_VALUE
            ? "" : format.width + "x" + format.height;
  }

private static String buildAudioPropertyString(MediaFormat format) {
    return format.channelCount == MediaFormat.NO_VALUE || format.sampleRate == MediaFormat.NO_VALUE
            ? "" : format.channelCount + "ch, " + format.sampleRate + "Hz";
  }

private static String buildLanguageString(MediaFormat format) {
    return TextUtils.isEmpty(format.language) || "und".equals(format.language) ? ""
            : format.language;
  }

private static String buildBitrateString(MediaFormat format) {
    return format.bitrate == MediaFormat.NO_VALUE ? ""
            : String.format(Locale.US, "%.2fMbit", format.bitrate / 1000000f);
  }

  // OnClickListener methods

private static String joinWithSeparator(String first, String second) {
    return first.length() == 0 ? second : (second.length() == 0 ? first : first + ", " + second);
  }

  // AudioCapabilitiesReceiver.Listener methods

private static String buildTrackIdString(MediaFormat format) {
    return format.trackId == MediaFormat.NO_VALUE ? ""
            : String.format(Locale.US, " (%d)", format.trackId);
  }

/**
   * Makes a best guess to infer the type from a media {@link android.net.Uri} and an optional overriding file
   * extension.
   *
   * @param uri The {@link android.net.Uri} of the media.
   * @param fileExtension An overriding file extension.
   * @return The inferred type.
   */
  private static int inferContentType(Uri uri, String fileExtension) {
    String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension
            : uri.getLastPathSegment();
    if (lastPathSegment == null) {
      return TYPE_OTHER;
    } else if (lastPathSegment.endsWith(EXT_DASH)) {
      return TYPE_DASH;
    } else if (lastPathSegment.endsWith(EXT_SS)) {
      return TYPE_SS;
    } else if (lastPathSegment.endsWith(EXT_HLS)) {
      return TYPE_HLS;
    } else {
      return TYPE_OTHER;
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.player_activity);
    View root = findViewById(R.id.root);
    root.setOnTouchListener(new OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
          toggleControlsVisibility();
        } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
          view.performClick();
        }
        return true;
      }
    });
    root.setOnKeyListener(new OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_MENU) {
          return false;
        }
        return mediaController.dispatchKeyEvent(event);
      }
    });

    shutterView = findViewById(R.id.shutter);
    debugRootView = findViewById(R.id.controls_root);

    videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
    surfaceView = (SurfaceView) findViewById(R.id.surface_view);
    surfaceView.getHolder().addCallback(this);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);

    playerStateTextView = (TextView) findViewById(R.id.player_state_view);
    subtitleLayout = (SubtitleLayout) findViewById(R.id.subtitles);

    mediaController = new MediaController(this);
    mediaController.setAnchorView(root);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);
    videoButton = (Button) findViewById(R.id.video_controls);
    audioButton = (Button) findViewById(R.id.audio_controls);
    textButton = (Button) findViewById(R.id.text_controls);

    CookieHandler currentHandler = CookieHandler.getDefault();
    if (currentHandler != defaultCookieManager) {
      CookieHandler.setDefault(defaultCookieManager);
    }

    audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(this, this);
    audioCapabilitiesReceiver.register();
  }

  @Override
  public void onNewIntent(Intent intent) {
    releasePlayer();
    playerPosition = 0;
    setIntent(intent);
  }

  @Override
  public void onResume() {
    super.onResume();
    Intent intent = getIntent();
    contentUri = intent.getData();
    contentType = intent.getIntExtra(CONTENT_TYPE_EXTRA,
            inferContentType(contentUri, intent.getStringExtra(CONTENT_EXT_EXTRA)));
    contentId = intent.getStringExtra(CONTENT_ID_EXTRA);
    configureSubtitleView();
    if (player == null) {
      preparePlayer(true);
    } else {
      player.setBackgrounded(false);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!enableBackgroundAudio) {
      releasePlayer();
    } else {
      player.setBackgrounded(true);
    }
    shutterView.setVisibility(View.VISIBLE);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    audioCapabilitiesReceiver.unregister();
    releasePlayer();
  }

  @Override
  public void onClick(View view) {
    if (view == retryButton) {
      preparePlayer(true);
    }
  }

    @Override
  public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
    if (player == null) {
      return;
    }
    boolean backgrounded = player.getBackgrounded();
    boolean playWhenReady = player.getPlayWhenReady();
    releasePlayer();
    preparePlayer(playWhenReady);
    player.setBackgrounded(backgrounded);
  }
  private DemoPlayer.RendererBuilder getRendererBuilder() {
    String userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
    switch (contentType) {
      case TYPE_SS:
        return new SmoothStreamingRendererBuilder(this, userAgent, contentUri.toString(),
                new SmoothStreamingTestMediaDrmCallback());
      case TYPE_DASH:
        return new DashRendererBuilder(this, userAgent, contentUri.toString(),
                new WidevineTestMediaDrmCallback(contentId));
      case TYPE_HLS:
        return new HlsRendererBuilder(this, userAgent, contentUri.toString());
      case TYPE_OTHER:
        return new ExtractorRendererBuilder(this, userAgent, contentUri);
      default:
        throw new IllegalStateException("Unsupported type: " + contentType);
    }
  }

    private void preparePlayer(boolean playWhenReady) {
    if (player == null) {
      player = new DemoPlayer(getRendererBuilder());
      player.addListener(this);
      player.setCaptionListener(this);
      player.setMetadataListener(this);
      player.seekTo(playerPosition);
      playerNeedsPrepare = true;
      mediaController.setMediaPlayer(player.getPlayerControl());
      mediaController.setEnabled(true);
      eventLogger = new EventLogger();
      eventLogger.startSession();
      player.addListener(eventLogger);
      player.setInfoListener(eventLogger);
      player.setInternalErrorListener(eventLogger);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
    }
    if (playerNeedsPrepare) {
      player.prepare();
      playerNeedsPrepare = false;
      updateButtonVisibilities();
    }
    player.setSurface(surfaceView.getHolder().getSurface());
    player.setPlayWhenReady(playWhenReady);
  }
  private void releasePlayer() {
    if (player != null) {
      debugViewHelper.stop();
      debugViewHelper = null;
      playerPosition = player.getCurrentPosition();
      player.release();
      player = null;
      eventLogger.endSession();
      eventLogger = null;
    }
  }

    @Override
  public void onStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == ExoPlayer.STATE_ENDED) {
      showControls();
    }
    String text = "playWhenReady=" + playWhenReady + ", playbackState=";
    switch(playbackState) {
      case ExoPlayer.STATE_BUFFERING:
        text += "buffering";
        break;
      case ExoPlayer.STATE_ENDED:
        text += "ended";
        break;
      case ExoPlayer.STATE_IDLE:
        text += "idle";
        break;
      case ExoPlayer.STATE_PREPARING:
        text += "preparing";
        break;
      case ExoPlayer.STATE_READY:
        text += "ready";
        break;
      default:
        text += "unknown";
        break;
    }
    playerStateTextView.setText(text);
    updateButtonVisibilities();
  }
  @Override
  public void onError(Exception e) {
    if (e instanceof UnsupportedDrmException) {
      // Special case DRM failures.
      UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
      int stringId = Util.SDK_INT < 18 ? R.string.drm_error_not_supported
              : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
              ? R.string.drm_error_unsupported_scheme : R.string.drm_error_unknown;
      Toast.makeText(getApplicationContext(), stringId, Toast.LENGTH_LONG).show();
    }
    playerNeedsPrepare = true;
    updateButtonVisibilities();
    showControls();
  }

    @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                 float pixelWidthAspectRatio) {
    shutterView.setVisibility(View.GONE);
    videoFrame.setAspectRatio(
            height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
  }
private void updateButtonVisibilities() {
    retryButton.setVisibility(playerNeedsPrepare ? View.VISIBLE : View.GONE);
    videoButton.setVisibility(haveTracks(DemoPlayer.TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    audioButton.setVisibility(haveTracks(DemoPlayer.TYPE_AUDIO) ? View.VISIBLE : View.GONE);
    textButton.setVisibility(haveTracks(DemoPlayer.TYPE_TEXT) ? View.VISIBLE : View.GONE);
  }

  private boolean haveTracks(int type) {
    return player != null && player.getTrackCount(type) > 0;
  }
public void showVideoPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    configurePopupWithTracks(popup, null, DemoPlayer.TYPE_VIDEO);
    popup.show();
  }

  public void showAudioPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.enable_background_audio);
    final MenuItem backgroundAudioItem = menu.findItem(0);
    backgroundAudioItem.setCheckable(true);
    backgroundAudioItem.setChecked(enableBackgroundAudio);
    OnMenuItemClickListener clickListener = new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        if (item == backgroundAudioItem) {
          enableBackgroundAudio = !item.isChecked();
          return true;
        }
        return false;
      }
    };
    configurePopupWithTracks(popup, clickListener, DemoPlayer.TYPE_AUDIO);
    popup.show();
  }
public void showTextPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    configurePopupWithTracks(popup, null, DemoPlayer.TYPE_TEXT);
    popup.show();
  }

public void showVerboseLogPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    menu.add(Menu.NONE, 0, Menu.NONE, R.string.logging_normal);
    menu.add(Menu.NONE, 1, Menu.NONE, R.string.logging_verbose);
    menu.setGroupCheckable(Menu.NONE, true, true);
    menu.findItem((VerboseLogUtil.areAllTagsEnabled()) ? 1 : 0).setChecked(true);
    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == 0) {
          VerboseLogUtil.setEnableAllTags(false);
        } else {
          VerboseLogUtil.setEnableAllTags(true);
        }
        return true;
      }
    });
    popup.show();
  }

  private void configurePopupWithTracks(PopupMenu popup,
                                        final OnMenuItemClickListener customActionClickListener,
                                        final int trackType) {
    if (player == null) {
      return;
    }
    int trackCount = player.getTrackCount(trackType);
    if (trackCount == 0) {
      return;
    }
    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        return (customActionClickListener != null
                && customActionClickListener.onMenuItemClick(item))
                || onTrackItemClick(item, trackType);
      }
    });
    Menu menu = popup.getMenu();
    // ID_OFFSET ensures we avoid clashing with Menu.NONE (which equals 0)
    menu.add(MENU_GROUP_TRACKS, DemoPlayer.TRACK_DISABLED + ID_OFFSET, Menu.NONE, R.string.off);
    for (int i = 0; i < trackCount; i++) {
      menu.add(MENU_GROUP_TRACKS, i + ID_OFFSET, Menu.NONE,
              buildTrackName(player.getTrackFormat(trackType, i)));
    }
    menu.setGroupCheckable(MENU_GROUP_TRACKS, true, true);
    menu.findItem(player.getSelectedTrack(trackType) + ID_OFFSET).setChecked(true);
  }

  private boolean onTrackItemClick(MenuItem item, int type) {
    if (player == null || item.getGroupId() != MENU_GROUP_TRACKS) {
      return false;
    }
    player.setSelectedTrack(type, item.getItemId() - ID_OFFSET);
    return true;
  }

  // DemoPlayer.CaptionListener implementation

  private void toggleControlsVisibility()  {
    if (mediaController.isShowing()) {
      mediaController.hide();
      debugRootView.setVisibility(View.GONE);
    } else {
      showControls();
    }
  }

  // DemoPlayer.MetadataListener implementation

  private void showControls() {
    mediaController.show(0);
    debugRootView.setVisibility(View.VISIBLE);
  }

  // SurfaceHolder.Callback implementation

  @Override
  public void onCues(List<Cue> cues) {
    subtitleLayout.setCues(cues);
  }

  @Override
  public void onId3Metadata(Map<String, Object> metadata) {
    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
      if (TxxxMetadata.TYPE.equals(entry.getKey())) {
        TxxxMetadata txxxMetadata = (TxxxMetadata) entry.getValue();
        Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s",
                TxxxMetadata.TYPE, txxxMetadata.description, txxxMetadata.value));
      } else if (PrivMetadata.TYPE.equals(entry.getKey())) {
        PrivMetadata privMetadata = (PrivMetadata) entry.getValue();
        Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s",
                PrivMetadata.TYPE, privMetadata.owner));
      } else if (GeobMetadata.TYPE.equals(entry.getKey())) {
        GeobMetadata geobMetadata = (GeobMetadata) entry.getValue();
        Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, description=%s",
                GeobMetadata.TYPE, geobMetadata.mimeType, geobMetadata.filename,
                geobMetadata.description));
      } else {
        Log.i(TAG, String.format("ID3 TimedMetadata %s", entry.getKey()));
      }
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (player != null) {
      player.setSurface(holder.getSurface());
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Do nothing.
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    if (player != null) {
      player.blockingClearSurface();
    }
  }

  private void configureSubtitleView() {
    CaptionStyleCompat style;
    float fontScale;
    if (Util.SDK_INT >= 19) {
      style = getUserCaptionStyleV19();
      fontScale = getUserCaptionFontScaleV19();
    } else {
      style = CaptionStyleCompat.DEFAULT;
      fontScale = 1.0f;
    }
    subtitleLayout.setStyle(style);
    subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
  }

    @TargetApi(19)
  private float getUserCaptionFontScaleV19() {
    CaptioningManager captioningManager =
            (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager.getFontScale();
  }
@TargetApi(19)
  private CaptionStyleCompat getUserCaptionStyleV19() {
    CaptioningManager captioningManager =
            (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
    return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
  }

}
