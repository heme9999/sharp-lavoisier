package com.falcon.tvlive.manager;

import android.content.Context;
import android.util.Log;

import com.falcon.tvlive.model.Category;
import com.falcon.tvlive.parser.M3uParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SubscriptionManager {
    private static final String TAG = "SubscriptionManager";
    private static final String CACHE_FILE_NAME = "live_cache.m3u";

    // 用户 GitHub 订阅源列表（包含加速镜像源）
    private static final List<String> REMOTE_URLS = Arrays.asList(
            "https://raw.githubusercontent.com/heme9999/sharp-lavoisier/master/live.m3u",
            "https://cdn.jsdelivr.net/gh/heme9999/sharp-lavoisier@master/live.m3u",
            "https://fastly.jsdelivr.net/gh/heme9999/sharp-lavoisier@master/live.m3u",
            "https://live.fanmingming.cn/tv/m3u/ipv6.m3u"
    );

    public interface OnUpdateListener {
        void onSuccess(List<Category> categories);
        void onFailure(String message);
    }

    private final Context context;
    private final OkHttpClient httpClient;

    public SubscriptionManager(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 同步/快速加载本地缓存或 assets 内置的 M3U
     */
    public List<Category> loadLocalSources() {
        File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try (InputStream is = new FileInputStream(cacheFile)) {
                Log.d(TAG, "Loading channels from local cache file");
                return M3uParser.parse(is);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load cache M3U", e);
            }
        }

        // 回退加载 assets 内置清单
        try (InputStream is = context.getAssets().open("live.m3u")) {
            Log.d(TAG, "Loading channels from assets live.m3u");
            return M3uParser.parse(is);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load assets M3U", e);
        }
        return null;
    }

    /**
     * 异步检测并更新远程 GitHub 订阅源
     */
    public void fetchRemoteSources(OnUpdateListener listener) {
        new Thread(() -> {
            boolean success = false;
            for (String url : REMOTE_URLS) {
                try {
                    Log.d(TAG, "Fetching remote M3U: " + url);
                    Request request = new Request.Builder()
                            .url(url)
                            .header("User-Agent", "FalconTvLive/1.0")
                            .build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String m3uContent = response.body().string();
                            if (m3uContent.contains("#EXTM3U")) {
                                List<Category> categories = M3uParser.parse(m3uContent);
                                if (!categories.isEmpty()) {
                                    // 写入本地缓存
                                    File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
                                    try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                                        fos.write(m3uContent.getBytes("UTF-8"));
                                    }
                                    Log.d(TAG, "Remote M3U updated successfully from " + url);
                                    if (listener != null) listener.onSuccess(categories);
                                    success = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to fetch from " + url + ": " + e.getMessage());
                }
            }

            if (!success && listener != null) {
                listener.onFailure("所有远程订阅源尝试完毕");
            }
        }).start();
    }
}
