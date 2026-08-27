package com.falcon.tvlive.parser;

import android.text.TextUtils;
import com.falcon.tvlive.model.Category;
import com.falcon.tvlive.model.Channel;
import com.falcon.tvlive.model.Source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {

    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"([^\"]+)\"");
    private static final Pattern LOGO_PATTERN = Pattern.compile("tvg-logo=\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile(",([^,]+)$");

    public static List<Category> parse(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        return parseInternal(reader);
    }

    public static List<Category> parse(String content) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(content));
        return parseInternal(reader);
    }

    private static List<Category> parseInternal(BufferedReader reader) throws IOException {
        Map<String, Category> categoryMap = new LinkedHashMap<>();
        Map<String, Channel> channelMap = new LinkedHashMap<>();

        String line;
        String currentGroup = "其他";
        String currentLogo = "";
        String currentDisplayName = "";
        int channelCounter = 1;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF:")) {
                Matcher groupMatcher = GROUP_PATTERN.matcher(line);
                if (groupMatcher.find()) {
                    currentGroup = groupMatcher.group(1).trim();
                } else {
                    currentGroup = "其他";
                }

                Matcher logoMatcher = LOGO_PATTERN.matcher(line);
                if (logoMatcher.find()) {
                    currentLogo = logoMatcher.group(1).trim();
                } else {
                    currentLogo = "";
                }

                Matcher nameMatcher = NAME_PATTERN.matcher(line);
                if (nameMatcher.find()) {
                    currentDisplayName = nameMatcher.group(1).trim();
                } else {
                    currentDisplayName = "未知频道";
                }
            } else if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtmp://")) {
                if (!TextUtils.isEmpty(currentDisplayName)) {
                    String baseChannelName = currentDisplayName;
                    String sourceName = "线路 1";

                    int parenStart = currentDisplayName.indexOf("(");
                    int parenEnd = currentDisplayName.indexOf(")");
                    if (parenStart != -1 && parenEnd > parenStart) {
                        baseChannelName = currentDisplayName.substring(0, parenStart).trim();
                        sourceName = currentDisplayName.substring(parenStart + 1, parenEnd).trim();
                    } else {
                        int bracketStart = currentDisplayName.indexOf("[");
                        int bracketEnd = currentDisplayName.indexOf("]");
                        if (bracketStart != -1 && bracketEnd > bracketStart) {
                            baseChannelName = currentDisplayName.substring(0, bracketStart).trim();
                            sourceName = currentDisplayName.substring(bracketStart + 1, bracketEnd).trim();
                        }
                    }

                    String key = currentGroup + ":" + baseChannelName;
                    Channel channel = channelMap.get(key);
                    if (channel == null) {
                        channel = new Channel(channelCounter++, baseChannelName, currentGroup, currentLogo);
                        channelMap.put(key, channel);

                        Category cat = categoryMap.get(currentGroup);
                        if (cat == null) {
                            cat = new Category(currentGroup);
                            categoryMap.put(currentGroup, cat);
                        }
                        cat.addChannel(channel);
                    }

                    if (sourceName.equals("线路 1") && channel.getSources().size() > 0) {
                        sourceName = "线路 " + (channel.getSources().size() + 1);
                    }
                    channel.addSource(new Source(sourceName, line));
                }
                currentDisplayName = "";
            }
        }

        return new ArrayList<>(categoryMap.values());
    }
}
