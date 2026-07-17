package com.ptchess.club.data.model;

public class NewsItem {
    public long id;
    public String title;
    public String body;
    public String date;
    public String authorName;

    public NewsItem(long id, String title, String body, String date, String authorName) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.date = date;
        this.authorName = authorName;
    }
}
