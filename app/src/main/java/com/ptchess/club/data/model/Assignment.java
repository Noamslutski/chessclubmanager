package com.ptchess.club.data.model;

public class Assignment {
    public long id;
    public long groupId;
    public String groupName;
    public String title;
    public String description;
    public String fileUri;      // optional attached PDF / PGN / text
    public String dueDate;      // ISO yyyy-MM-dd
    public String createdByName;
    public boolean completed;   // for the current viewing student

    public Assignment(long id, long groupId, String groupName, String title,
                      String description, String fileUri, String dueDate,
                      String createdByName, boolean completed) {
        this.id = id;
        this.groupId = groupId;
        this.groupName = groupName;
        this.title = title;
        this.description = description;
        this.fileUri = fileUri;
        this.dueDate = dueDate;
        this.createdByName = createdByName;
        this.completed = completed;
    }
}
