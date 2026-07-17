package com.ptchess.club.data.model;

public class Club {
    public long id;
    public String name;
    public long ownerId;
    public String ownerName;
    /** Verified by the main app admin (noamslutski@gmail.com). */
    public boolean verified;

    // Profile (admin-editable)
    public String description = "";
    public String address = "";
    public String contactPhone = "";
    public String hall = "";
    public String logoUri = "";
    public String bannerUri = "";

    public Club(long id, String name, long ownerId, String ownerName, boolean verified) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.verified = verified;
    }
}
