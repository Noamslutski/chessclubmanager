package com.ptchess.club.data.model;

public class User {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REJECTED = "REJECTED";

    public long id;
    public String fullName;
    public String email;
    public String phone;
    public Role role;
    public String status;
    public long clubId;

    public User() { }

    public User(long id, String fullName, String email, String phone,
                Role role, String status, long clubId) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.clubId = clubId;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
