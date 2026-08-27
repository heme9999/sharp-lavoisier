package com.falcon.tvlive.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Channel implements Serializable {
    private int num;
    private String name;
    private String category;
    private String logo;
    private List<Source> sources = new ArrayList<>();
    private int currentSourceIndex = 0;

    public Channel(int num, String name, String category, String logo) {
        this.num = num;
        this.name = name;
        this.category = category;
        this.logo = logo;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void addSource(Source source) {
        this.sources.add(source);
    }

    public int getCurrentSourceIndex() {
        return currentSourceIndex;
    }

    public void setCurrentSourceIndex(int index) {
        if (index >= 0 && index < sources.size()) {
            this.currentSourceIndex = index;
        }
    }

    public Source getCurrentSource() {
        if (sources.isEmpty()) return null;
        if (currentSourceIndex >= sources.size()) currentSourceIndex = 0;
        return sources.get(currentSourceIndex);
    }

    public boolean nextSource() {
        if (sources.size() <= 1) return false;
        currentSourceIndex = (currentSourceIndex + 1) % sources.size();
        return true;
    }

    public boolean prevSource() {
        if (sources.size() <= 1) return false;
        currentSourceIndex = (currentSourceIndex - 1 + sources.size()) % sources.size();
        return true;
    }
}
