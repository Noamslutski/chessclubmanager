package com.ptchess.club.data.model;

public class Book {
    public long id;
    public String title;
    public String author;
    public String language;
    public String category;   // opening / attack / defense / endgame / psychology ...
    public String side;       // WHITE / BLACK / BOTH
    public int ratingMin;
    public int ratingMax;
    public String fileUri;    // content:// or file path to the PDF

    public Book(long id, String title, String author, String language, String category,
                String side, int ratingMin, int ratingMax, String fileUri) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.language = language;
        this.category = category;
        this.side = side;
        this.ratingMin = ratingMin;
        this.ratingMax = ratingMax;
        this.fileUri = fileUri;
    }
}
