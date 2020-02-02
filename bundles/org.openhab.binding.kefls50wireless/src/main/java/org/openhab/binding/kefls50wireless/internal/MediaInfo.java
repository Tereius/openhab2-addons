package org.openhab.binding.kefls50wireless.internal;

public class MediaInfo {

    private String artist;
    private String title;

    private MediaInfo() {
    }

    public static MediaInfo fromXml(String xml) {

        return new MediaInfo();
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "Title: " + title + " Artist: " + artist;
    }
}
