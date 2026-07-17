package com.ptchess.club.data.model;

public class TournamentResult {
    public long id;
    public String tournamentName;
    public String date;
    public long childId;
    public String childName;
    public double points;
    public int games;
    public int standing;

    public TournamentResult(long id, String tournamentName, String date, long childId,
                            String childName, double points, int games, int standing) {
        this.id = id;
        this.tournamentName = tournamentName;
        this.date = date;
        this.childId = childId;
        this.childName = childName;
        this.points = points;
        this.games = games;
        this.standing = standing;
    }
}
