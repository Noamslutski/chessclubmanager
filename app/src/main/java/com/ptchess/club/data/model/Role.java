package com.ptchess.club.data.model;

import com.ptchess.club.R;

/**
 * The four access levels. CHILD is the base level assigned on registration; an
 * admin promotes users to TUTOR/ADMIN, and PARENT is granted when a user links
 * a verified child account.
 */
public enum Role {
    CHILD(1, R.string.role_child),
    PARENT(2, R.string.role_parent),
    TUTOR(3, R.string.role_tutor),
    ADMIN(4, R.string.role_admin);

    public final int level;
    public final int displayRes;

    Role(int level, int displayRes) {
        this.level = level;
        this.displayRes = displayRes;
    }

    public static Role fromName(String name) {
        if (name == null) return CHILD;
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CHILD;
        }
    }

    public boolean isStaff() {
        return this == TUTOR || this == ADMIN;
    }
}
