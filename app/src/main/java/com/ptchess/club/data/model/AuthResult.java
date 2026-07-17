package com.ptchess.club.data.model;

/** Outcome of an authentication attempt, including brute-force lockout info. */
public class AuthResult {
    public enum Status {
        SUCCESS,
        WRONG_CREDENTIALS,
        PENDING_APPROVAL,
        REJECTED,
        LOCKED
    }

    public final Status status;
    public final User user;         // non-null only on SUCCESS
    public final int lockMinutes;   // meaningful only on LOCKED

    private AuthResult(Status status, User user, int lockMinutes) {
        this.status = status;
        this.user = user;
        this.lockMinutes = lockMinutes;
    }

    public static AuthResult success(User user) {
        return new AuthResult(Status.SUCCESS, user, 0);
    }

    public static AuthResult of(Status status) {
        return new AuthResult(status, null, 0);
    }

    public static AuthResult locked(int minutes) {
        return new AuthResult(Status.LOCKED, null, minutes);
    }
}
