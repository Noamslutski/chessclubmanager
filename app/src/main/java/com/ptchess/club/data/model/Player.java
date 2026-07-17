package com.ptchess.club.data.model;

/** A federation player imported into a club's roster (from the players API). */
public class Player {
    public long id;
    public String externalId;
    public String fullName;
    public int rating;
    public String federation;

    public Player(long id, String externalId, String fullName, int rating, String federation) {
        this.id = id;
        this.externalId = externalId;
        this.fullName = fullName;
        this.rating = rating;
        this.federation = federation;
    }
}
