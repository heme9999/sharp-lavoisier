package com.falcon.tvlive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.falcon.tvlive.adapter.CategoryAdapter;
import com.falcon.tvlive.adapter.ChannelAdapter;
import com.falcon.tvlive.manager.SubscriptionManager;
import com.falcon.tvlive.model.Category;
import com.falcon.tvlive.model.Channel;
import com.falcon.tvlive.model.Source;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "falcon_tv_prefs";
    private static final String KEY_LAST_CHANNEL_NUM = "last_channel_num";
    private static final String KEY_LAST_SOURCE_IDX = "last_source_idx";

    // Views
    private FrameLayout rootLayout;
    private PlayerView playerView;
    private LinearLayout loadingLayout;
    private TextView tvLoadingMsg;
    private LinearLayout osdLayout;
    private TextView osdChannelNum;
    private TextView osdChannelName;
    private TextView osdCategory;
    private TextView osdSourceName;
    private TextView tvDigitInput;
    private LinearLayout drawerLayout;
    private TextView tvDrawerCategoryTitle;
    private RecyclerView rvCategories;
    private RecyclerView rvChannels;

    // Adapters & Managers
    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private SubscriptionManager subscriptionManager;
    private ExoPlayer player;
    private GestureDetector gestureDetector;

    // Data State
    private List<Category> allCategories = new ArrayList<>();
    private List<Channel> allChannelsFlat = new ArrayList<>();
    private Channel currentChannel;
    private int currentFlatIndex = 0;
    private int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

    // UI Handlers & Timers
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder digitInputBuilder = new StringBuilder();
    private boolean isDrawerOpen = false;
    private long lastBackPressTime = 0;

    private final Runnable hideOsdRunnable = () -> {
        if (osdLayout != null) osdLayout.setVisibility(View.GONE);
    };

    private final Runnable processDigitInputRunnable = () -> {
        if (digitInputBuilder.length() > 0) {
            try {
                int inputNum = Integer.parseInt(digitInputBuilder.toString());
                switchToChannelByNum(inputNum);
            } catch (Exception ignored) {}
            digitInputBuilder.setLength(0);
            if (tvDigitInput != null) tvDigitInput.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        initViews();
        initTouchAndGestures();
        initPlayer();
        initData();
    }

    private void initViews() {
        rootLayout = findViewById(R.id.rootLayout);
        playerView = findViewById(R.id.playerView);
        loadingLayout = findViewById(R.id.loadingLayout);
        tvLoadingMsg = findViewById(R.id.tvLoadingMsg);
        osdLayout = findViewById(R.id.osdLayout);
        osdChannelNum = findViewById(R.id.osdChannelNum);
        osdChannelName = findViewById(R.id.osdChannelName);
        osdCategory = findViewById(R.id.osdCategory);
        osdSourceName = findViewById(R.id.osdSourceName);
        tvDigitInput = findViewById(R.id.tvDigitInput);
        drawerLayout = findViewById(R.id.drawerLayout);
        tvDrawerCategoryTitle = findViewById(R.id.tvDrawerCategoryTitle);
        rvCategories = findViewById(R.id.rvCategories);
        rvChannels = findViewById(R.id.rvChannels);

        // 分类 RecyclerView
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter((category, position) -> {
            tvDrawerCategoryTitle.setText(category.getName() + " (" + category.getChannels().size() + ")");
            channelAdapter.setChannels(category.getChannels());
        });
        rvCategories.setAdapter(categoryAdapter);

        // 频道 RecyclerView
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        channelAdapter = new ChannelAdapter((channel, position) -> {
            playChannel(channel);
            closeDrawer();
        });
        rvChannels.setAdapter(channelAdapter);
    }

    private void initTouchAndGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 60;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isDrawerOpen) {
                    closeDrawer();
                } else {
                    openDrawer();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 双击屏幕切换全屏拉伸 / 原始比例
                if (playerView != null) {
                    if (currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                        currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                        Toast.makeText(MainActivity.this, "画面比例: 铺满全屏", Toast.LENGTH_SHORT).show();
                    } else {
                        currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                        Toast.makeText(MainActivity.this, "画面比例: 原始比例", Toast.LENGTH_SHORT).show();
                    }
                    playerView.setResizeMode(currentResizeMode);
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isDrawerOpen || e1 == null || e2 == null) return false;

                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                if (Math.abs(diffY) > Math.abs(diffX)) {
                    // 上下滑动手势换台
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) {
                            switchToNextChannel();
                        } else {
                            switchToPrevChannel();
                        }
                        return true;
                    }
                } else {
                    // 左右滑动切源
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            switchToNextSource();
                        } else {
                            switchToPrevSource();
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        // 监听屏幕点击和滑动事件
        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // 点击侧边栏外部区域自动关闭
        drawerLayout.setOnClickListener(v -> {
            // consume clicks
        });
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    loadingLayout.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText(R.string.channel_loading);
                } else if (state == Player.STATE_READY) {
                    loadingLayout.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                loadingLayout.setVisibility(View.VISIBLE);
                tvLoadingMsg.setText(R.string.channel_error);
                // 自动尝试下一个备用源
                handler.postDelayed(() -> {
                    if (currentChannel != null && currentChannel.getSources().size() > 1) {
                        currentChannel.nextSource();
                        playCurrentSource();
                    }
                }, 1500);
            }
        });
    }

    private void initData() {
        subscriptionManager = new SubscriptionManager(this);

        // 1. 优先加载本地 M3U
        List<Category> localCategories = subscriptionManager.loadLocalSources();
        if (localCategories != null && !localCategories.isEmpty()) {
            applyCategories(localCategories);
        }

        // 2. 异步检测云端 GitHub 订阅更新
        subscriptionManager.fetchRemoteSources(new SubscriptionManager.OnUpdateListener() {
            @Override
            public void onSuccess(List<Category> categories) {
                handler.post(() -> {
                    applyCategories(categories);
                    Toast.makeText(MainActivity.this, "已同步最新直播源", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String message) {}
        });
    }

    private void applyCategories(List<Category> categories) {
        this.allCategories = categories;
        this.allChannelsFlat.clear();
        for (Category cat : categories) {
            allChannelsFlat.addAll(cat.getChannels());
        }

        categoryAdapter.setCategories(allCategories);
        if (!allCategories.isEmpty()) {
            categoryAdapter.setSelectedPosition(0);
            tvDrawerCategoryTitle.setText(allCategories.get(0).getName() + " (" + allCategories.get(0).getChannels().size() + ")");
            channelAdapter.setChannels(allCategories.get(0).getChannels());
        }

        // 恢复上次播放状态
        SharedPreferences pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int lastNum = pref.getInt(KEY_LAST_CHANNEL_NUM, 1);
        int lastSourceIdx = pref.getInt(KEY_LAST_SOURCE_IDX, 0);

        Channel target = null;
        for (int i = 0; i < allChannelsFlat.size(); i++) {
            if (allChannelsFlat.get(i).getNum() == lastNum) {
                target = allChannelsFlat.get(i);
                currentFlatIndex = i;
                break;
            }
        }
        if (target == null && !allChannelsFlat.isEmpty()) {
            target = allChannelsFlat.get(0);
            currentFlatIndex = 0;
        }

        if (target != null) {
            target.setCurrentSourceIndex(lastSourceIdx);
            playChannel(target);
        }
    }

    private void playChannel(Channel channel) {
        if (channel == null) return;
        this.currentChannel = channel;
        channelAdapter.setCurrentPlayingChannel(channel);

        // 保存最近播放频道
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_CHANNEL_NUM, channel.getNum())
                .putInt(KEY_LAST_SOURCE_IDX, channel.getCurrentSourceIndex())
                .apply();

        playCurrentSource();
    }

    private void playCurrentSource() {
        if (currentChannel == null) return;
        Source src = currentChannel.getCurrentSource();
        if (src == null) return;

        showOsd();
        loadingLayout.setVisibility(View.VISIBLE);
        tvLoadingMsg.setText(R.string.channel_loading);

        player.stop();
        MediaItem mediaItem = MediaItem.fromUri(src.getUrl());
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    private void showOsd() {
        if (currentChannel == null) return;
        osdChannelNum.setText(String.format("%02d", currentChannel.getNum()));
        osdChannelName.setText(currentChannel.getName());
        osdCategory.setText(currentChannel.getCategory());

        Source src = currentChannel.getCurrentSource();
        int idx = currentChannel.getCurrentSourceIndex() + 1;
        int total = currentChannel.getSources().size();
        String srcName = (src != null) ? src.getName() : "";
        osdSourceName.setText("线路 " + idx + "/" + total + ": " + srcName);

        osdLayout.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 4000);
    }

    private void switchToNextChannel() {
        if (allChannelsFlat.isEmpty()) return;
        currentFlatIndex = (currentFlatIndex + 1) % allChannelsFlat.size();
        playChannel(allChannelsFlat.get(currentFlatIndex));
    }

    private void switchToPrevChannel() {
        if (allChannelsFlat.isEmpty()) return;
        currentFlatIndex = (currentFlatIndex - 1 + allChannelsFlat.size()) % allChannelsFlat.size();
        playChannel(allChannelsFlat.get(currentFlatIndex));
    }

    private void switchToNextSource() {
        if (currentChannel != null && currentChannel.nextSource()) {
            Toast.makeText(this, "切换到: " + currentChannel.getCurrentSource().getName(), Toast.LENGTH_SHORT).show();
            playCurrentSource();
        }
    }

    private void switchToPrevSource() {
        if (currentChannel != null && currentChannel.prevSource()) {
            Toast.makeText(this, "切换到: " + currentChannel.getCurrentSource().getName(), Toast.LENGTH_SHORT).show();
            playCurrentSource();
        }
    }

    private void switchToChannelByNum(int num) {
        for (int i = 0; i < allChannelsFlat.size(); i++) {
            if (allChannelsFlat.get(i).getNum() == num) {
                currentFlatIndex = i;
                playChannel(allChannelsFlat.get(i));
                return;
            }
        }
        Toast.makeText(this, "未找到频道: " + num, Toast.LENGTH_SHORT).show();
    }

    private void openDrawer() {
        isDrawerOpen = true;
        drawerLayout.setVisibility(View.VISIBLE);
        osdLayout.setVisibility(View.GONE);
        rvChannels.requestFocus();
    }

    private void closeDrawer() {
        isDrawerOpen = false;
        drawerLayout.setVisibility(View.GONE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isDrawerOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeDrawer();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        // 数字键处理 (0-9)
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int digit = keyCode - KeyEvent.KEYCODE_0;
            digitInputBuilder.append(digit);
            tvDigitInput.setText(digitInputBuilder.toString());
            tvDigitInput.setVisibility(View.VISIBLE);

            handler.removeCallbacks(processDigitInputRunnable);
            handler.postDelayed(processDigitInputRunnable, 1500);
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                switchToPrevChannel();
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                switchToNextChannel();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                switchToPrevSource();
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                switchToNextSource();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                openDrawer();
                return true;

            case KeyEvent.KEYCODE_MENU:
                subscriptionManager.fetchRemoteSources(new SubscriptionManager.OnUpdateListener() {
                    @Override
                    public void onSuccess(List<Category> categories) {
                        handler.post(() -> {
                            applyCategories(categories);
                            Toast.makeText(MainActivity.this, "订阅源已手动更新", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        handler.post(() -> Toast.makeText(MainActivity.this, "更新失败: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
                return true;

            case KeyEvent.KEYCODE_BACK:
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBackPressTime < 2000) {
                    finish();
                } else {
                    lastBackPressTime = currentTime;
                    Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
                }
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
