package com.ptchess.club.data.model;

public class Group {
    public long id;
    public String name;
    public int dayIndex;       // 0 = Sunday .. 6 = Saturday (formatted in UI per locale)
    public String time;        // e.g. "17:00"
    public long tutorId;
    public String tutorName;
    public int memberCount;

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
