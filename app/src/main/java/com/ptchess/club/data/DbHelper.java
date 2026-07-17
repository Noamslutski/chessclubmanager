package com.ptchess.club.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.security.PasswordHasher;

/**
 * Local SQLite store. All reads/writes go through {@link ClubRepository} using
 * parameterized statements — no query string is ever built by concatenating
 * user input, so SQL injection is not possible.
 *
 * <p>On a real deployment this local DB becomes an offline cache in front of a
 * backend (see README). For the demo it is seeded with a working data set.</p>
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "ptchess.db";
    private static final int DB_VERSION = 1;

    // Tables
    public static final String T_USERS = "users";
    // Avoid the SQLite reserved word "groups" (window-function frame keyword).
    public static final String T_GROUPS = "club_groups";
    public static final String T_GROUP_MEMBERS = "group_members";
    public static final String T_PARENT_CHILD = "parent_child";
    public static final String T_PUZZLES = "puzzles";
    public static final String T_BOOKS = "books";
    public static final String T_ASSIGNMENTS = "assignments";
    public static final String T_ASSIGN_STATUS = "assignment_status";
    public static final String T_TOURNAMENTS = "tournaments";
    public static final String T_RESULTS = "tournament_results";
    public static final String T_NEWS = "news";
    public static final String T_LOGIN_ATTEMPTS = "login_attempts";

    public DbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_USERS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "full_name TEXT NOT NULL,"
                + "email TEXT NOT NULL UNIQUE,"
                + "password_hash TEXT NOT NULL,"
                + "phone TEXT,"
                + "role TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "created_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_GROUPS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "day_index INTEGER NOT NULL,"
                + "time TEXT,"
                + "tutor_id INTEGER)");

        db.execSQL("CREATE TABLE " + T_GROUP_MEMBERS + " ("
                + "group_id INTEGER NOT NULL,"
                + "child_id INTEGER NOT NULL,"
                + "PRIMARY KEY(group_id, child_id))");

        db.execSQL("CREATE TABLE " + T_PARENT_CHILD + " ("
                + "parent_id INTEGER NOT NULL,"
                + "child_id INTEGER NOT NULL,"
                + "PRIMARY KEY(parent_id, child_id))");

        db.execSQL("CREATE TABLE " + T_PUZZLES + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT,"
                + "level INTEGER NOT NULL,"
                + "fen TEXT NOT NULL,"
                + "solution_uci TEXT NOT NULL,"
                + "created_by INTEGER)");

        db.execSQL("CREATE TABLE " + T_BOOKS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "author TEXT,"
                + "language TEXT,"
                + "category TEXT,"
                + "side TEXT,"
                + "rating_min INTEGER,"
                + "rating_max INTEGER,"
                + "file_uri TEXT,"
                + "uploaded_by INTEGER)");

        db.execSQL("CREATE TABLE " + T_ASSIGNMENTS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "group_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "description TEXT,"
                + "file_uri TEXT,"
                + "due_date TEXT,"
                + "created_by INTEGER,"
                + "created_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_ASSIGN_STATUS + " ("
                + "assignment_id INTEGER NOT NULL,"
                + "child_id INTEGER NOT NULL,"
                + "completed INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(assignment_id, child_id))");

        db.execSQL("CREATE TABLE " + T_TOURNAMENTS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "date TEXT)");

        db.execSQL("CREATE TABLE " + T_RESULTS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "tournament_id INTEGER NOT NULL,"
                + "child_id INTEGER NOT NULL,"
                + "points REAL,"
                + "games INTEGER,"
                + "standing INTEGER)");

        db.execSQL("CREATE TABLE " + T_NEWS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "body TEXT,"
                + "date TEXT,"
                + "created_by INTEGER)");

        db.execSQL("CREATE TABLE " + T_LOGIN_ATTEMPTS + " ("
                + "email TEXT PRIMARY KEY,"
                + "fail_count INTEGER NOT NULL DEFAULT 0,"
                + "lock_until INTEGER NOT NULL DEFAULT 0)");

        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Foundation: no migrations yet. A production build must migrate, not drop.
    }

    // ---------------------------------------------------------------- seeding

    private long insertUser(SQLiteDatabase db, String name, String email,
                            String password, String phone, Role role, String status) {
        ContentValues v = new ContentValues();
        v.put("full_name", name);
        v.put("email", email.toLowerCase());
        v.put("password_hash", PasswordHasher.hash(password));
        v.put("phone", phone);
        v.put("role", role.name());
        v.put("status", status);
        v.put("created_at", System.currentTimeMillis());
        return db.insert(T_USERS, null, v);
    }

    private void seed(SQLiteDatabase db) {
        // --- Accounts (change these before going live; see README) ---
        long admin = insertUser(db, "מנהל המועדון", "admin@ptchess.co.il",
                "Admin#2026", "+972500000001", Role.ADMIN, User.STATUS_ACTIVE);
        long tutor = insertUser(db, "דוד לוי", "tutor@ptchess.co.il",
                "Tutor#2026", "+972500000002", Role.TUTOR, User.STATUS_ACTIVE);
        long child1 = insertUser(db, "יונתן כהן", "child@ptchess.co.il",
                "Child#2026", null, Role.CHILD, User.STATUS_ACTIVE);
        long child2 = insertUser(db, "מאיה פרץ", "maya@ptchess.co.il",
                "Child#2026", null, Role.CHILD, User.STATUS_ACTIVE);
        long parent = insertUser(db, "רונית כהן", "parent@ptchess.co.il",
                "Parent#2026", "+972500000003", Role.PARENT, User.STATUS_ACTIVE);
        // A pending registration so the admin approvals screen is not empty.
        insertUser(db, "איתי גולן", "pending@ptchess.co.il",
                "Pending#2026", null, Role.CHILD, User.STATUS_PENDING);

        // Parent -> child link
        ContentValues pc = new ContentValues();
        pc.put("parent_id", parent);
        pc.put("child_id", child1);
        db.insert(T_PARENT_CHILD, null, pc);

        // --- Groups ---
        long gA = insertGroup(db, "קבוצה א׳ - מתחילים", 0, "17:00", tutor);   // Sunday
        long gB = insertGroup(db, "קבוצה ב׳ - מתקדמים", 2, "18:00", tutor);   // Tuesday
        addMember(db, gA, child1);
        addMember(db, gA, child2);
        addMember(db, gB, child1);

        // --- Puzzles (FEN + UCI solution) ---
        insertPuzzle(db, "מט בתור אחד - שורה אחורית", 1,
                "6k1/5ppp/8/8/8/8/8/R6K w - - 0 1", "a1a8", admin);
        insertPuzzle(db, "מזלג פרש", 1,
                "r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1", "d5c7", admin);
        insertPuzzle(db, "רגלי זוכה מלכה", 2,
                "8/8/8/3q4/4P3/8/8/4K1k1 w - - 0 1", "e4d5", tutor);
        insertPuzzle(db, "מט המלכה", 2,
                "7k/8/6KQ/8/8/8/8/8 w - - 0 1", "h6h7", tutor);
        insertPuzzle(db, "מט חנוק", 3,
                "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", "g5f7", admin);
        insertPuzzle(db, "זכייה בצריח", 3,
                "3r2k1/5ppp/8/8/8/8/8/3QK3 w - - 0 1", "d1d8", tutor);

        // --- Library ---
        insertBook(db, "אמנות הקומבינציה", "מקסים בלוך", "עברית",
                "tactics", "BOTH", 1200, 1800, admin);
        insertBook(db, "My System", "Aron Nimzowitsch", "English",
                "strategy", "BOTH", 1600, 2200, admin);
        insertBook(db, "התקפה על המלך", "יעקב נוידיטש", "עברית",
                "attack", "WHITE", 1400, 2000, tutor);

        // --- News ---
        insertNews(db, "פתיחת שנת הפעילות", "ברוכים הבאים למועדון השחמט פתח תקווה! "
                + "האימונים מתחילים ביום ראשון הקרוב.", admin);
        insertNews(db, "טורניר פנימי", "טורניר הבזק הפנימי יתקיים בסוף החודש. "
                + "הרשמה אצל המאמנים.", admin);

        // --- Tournament + results ---
        long tourney = insertTournament(db, "אליפות פתח תקווה לנוער", "2026-06-01");
        insertResult(db, tourney, child1, 4.5, 6, 3);
        insertResult(db, tourney, child2, 3.0, 6, 8);

        // --- Assignment for group A ---
        long assign = insertAssignment(db, gA, "תרגילי מט בשניים",
                "פתרו את 10 התרגילים המצורפים והביאו למפגש הבא.", null, "2026-07-25", tutor);
        // status rows created lazily when a student marks done
    }

    private long insertGroup(SQLiteDatabase db, String name, int dayIndex, String time, long tutorId) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("day_index", dayIndex);
        v.put("time", time);
        v.put("tutor_id", tutorId);
        return db.insert(T_GROUPS, null, v);
    }

    private void addMember(SQLiteDatabase db, long groupId, long childId) {
        ContentValues v = new ContentValues();
        v.put("group_id", groupId);
        v.put("child_id", childId);
        db.insert(T_GROUP_MEMBERS, null, v);
    }

    private void insertPuzzle(SQLiteDatabase db, String title, int level,
                              String fen, String sol, long by) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("level", level);
        v.put("fen", fen);
        v.put("solution_uci", sol);
        v.put("created_by", by);
        db.insert(T_PUZZLES, null, v);
    }

    private void insertBook(SQLiteDatabase db, String title, String author, String lang,
                            String category, String side, int rMin, int rMax, long by) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("author", author);
        v.put("language", lang);
        v.put("category", category);
        v.put("side", side);
        v.put("rating_min", rMin);
        v.put("rating_max", rMax);
        v.put("uploaded_by", by);
        db.insert(T_BOOKS, null, v);
    }

    private void insertNews(SQLiteDatabase db, String title, String body, long by) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("date", "2026-07-15");
        v.put("created_by", by);
        db.insert(T_NEWS, null, v);
    }

    private long insertTournament(SQLiteDatabase db, String name, String date) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("date", date);
        return db.insert(T_TOURNAMENTS, null, v);
    }

    private void insertResult(SQLiteDatabase db, long tId, long childId,
                              double points, int games, int standing) {
        ContentValues v = new ContentValues();
        v.put("tournament_id", tId);
        v.put("child_id", childId);
        v.put("points", points);
        v.put("games", games);
        v.put("standing", standing);
        db.insert(T_RESULTS, null, v);
    }

    private long insertAssignment(SQLiteDatabase db, long groupId, String title,
                                  String desc, String fileUri, String due, long by) {
        ContentValues v = new ContentValues();
        v.put("group_id", groupId);
        v.put("title", title);
        v.put("description", desc);
        v.put("file_uri", fileUri);
        v.put("due_date", due);
        v.put("created_by", by);
        v.put("created_at", System.currentTimeMillis());
        return db.insert(T_ASSIGNMENTS, null, v);
    }
}
