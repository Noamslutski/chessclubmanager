package com.ptchess.club.data.model;

import java.util.ArrayList;
import java.util.List;

public class Group {
    public long id;
    public String name;
    public int dayIndex;       // 0 = Sunday .. 6 = Saturday (formatted in UI per locale)
    public String time;        // e.g. "17:00"
    public long tutorId;
    public String tutorName;
    public int memberCount;

    // Cloud (Firestore) fields — null/empty in local-only mode. Present when the
    // group came from Firestore so membership can be resolved by email cross-device.
    public String cloudId;
    public String tutorEmail = "";
    public List<String> memberEmails = new ArrayList<>();
    public List<String> memberNames = new ArrayList<>();

    public Group(long id, String name, int dayIndex, String time,
                 long tutorId, String tutorName, int memberCount) {
        this.id = id;
        this.name = name;
        this.dayIndex = dayIndex;
        this.time = time;
        this.tutorId = tutorId;
        this.tutorName = tutorName;
        this.memberCount = memberCount;
    }
}
