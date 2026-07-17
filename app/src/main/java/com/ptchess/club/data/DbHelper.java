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
 * <p>The app is multi-club: a user belongs to one club, and content (groups,
 * puzzles, books, news, assignments, tournaments) is scoped by {@code club_id}.
 * A user can create a new club on registration and becomes its admin/owner.</p>
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "ptchess.db";
    private static final int DB_VERSION = 4;

    /** The main app admin: extra powers (verify clubs) are keyed to this email. */
    public static final String SUPER_ADMIN_EMAIL = "noamslutski@gmail.com";

    // Tables
    public static final String T_CLUBS = "clubs";
    public static final String T_USERS = "users";
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
    public static final String T_PLAYERS = "club_players";

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
        db.execSQL("CREATE TABLE " + T_CLUBS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "owner_id INTEGER,"
                + "verified INTEGER NOT NULL DEFAULT 0,"
                + "description TEXT,"
                + "address TEXT,"
                + "contact_phone TEXT,"
                + "hall TEXT,"
                + "logo_uri TEXT,"
                + "banner_uri TEXT,"
                + "created_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_USERS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "full_name TEXT NOT NULL,"
                + "email TEXT NOT NULL UNIQUE,"
                + "password_hash TEXT NOT NULL,"
                + "phone TEXT,"
                + "role TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "club_id INTEGER NOT NULL DEFAULT 0,"
                + "rating INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER)");

        db.execSQL("CREATE TABLE " + T_GROUPS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "club_id INTEGER NOT NULL DEFAULT 0,"
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
                + "club_id INTEGER NOT NULL DEFAULT 0,"
                + "title TEXT,"
                + "level INTEGER NOT NULL,"
                + "fen TEXT NOT NULL,"
                + "solution_uci TEXT NOT NULL,"
                + "created_by INTEGER)");

        db.execSQL("CREATE TABLE " + T_BOOKS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "club_id INTEGER NOT NULL DEFAULT 0,"
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
                + "club_id INTEGER NOT NULL DEFAULT 0,"
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
                + "club_id INTEGER NOT NULL DEFAULT 0,"
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
                + "club_id INTEGER NOT NULL DEFAULT 0,"
                + "title TEXT NOT NULL,"
                + "body TEXT,"
                + "date TEXT,"
                + "created_by INTEGER)");

        db.execSQL("CREATE TABLE " + T_LOGIN_ATTEMPTS + " ("
                + "email TEXT PRIMARY KEY,"
                + "fail_count INTEGER NOT NULL DEFAULT 0,"
                + "lock_until INTEGER NOT NULL DEFAULT 0)");

        // Federation players imported into a club (deduped per club by external id).
        db.execSQL("CREATE TABLE " + T_PLAYERS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "club_id INTEGER NOT NULL,"
                + "external_id TEXT,"
                + "full_name TEXT NOT NULL,"
                + "rating INTEGER,"
                + "federation TEXT,"
                + "UNIQUE(club_id, external_id))");

        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Pre-release: recreate from scratch. A production build must migrate.
        for (String t : new String[]{T_PLAYERS, T_LOGIN_ATTEMPTS, T_NEWS, T_RESULTS,
                T_TOURNAMENTS, T_ASSIGN_STATUS, T_ASSIGNMENTS, T_BOOKS, T_PUZZLES,
                T_PARENT_CHILD, T_GROUP_MEMBERS, T_GROUPS, T_USERS, T_CLUBS}) {
            db.execSQL("DROP TABLE IF EXISTS " + t);
        }
        onCreate(db);
    }

    // ---------------------------------------------------------------- seeding

    private long insertUser(SQLiteDatabase db, String name, String email, String password,
                            String phone, Role role, String status, long clubId, int rating) {
        ContentValues v = new ContentValues();
        v.put("full_name", name);
        v.put("email", email.toLowerCase());
        v.put("password_hash", PasswordHasher.hash(password));
        v.put("phone", phone);
        v.put("role", role.name());
        v.put("status", status);
        v.put("club_id", clubId);
        v.put("rating", rating);
        v.put("created_at", System.currentTimeMillis());
        return db.insert(T_USERS, null, v);
    }

    private long insertClub(SQLiteDatabase db, String name, long ownerId, boolean verified) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("owner_id", ownerId);
        v.put("verified", verified ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        return db.insert(T_CLUBS, null, v);
    }

    private void seed(SQLiteDatabase db) {
        // Super admin owns the founding, verified club.
        long noam = insertUser(db, "נועם סלוצקי", SUPER_ADMIN_EMAIL,
                "Noam#2026", "+972500000000", Role.ADMIN, User.STATUS_ACTIVE, 0, 0);
        long club = insertClub(db, "מועדון השחמט פתח תקווה", noam, true);
        ContentValues cu = new ContentValues();
        cu.put("club_id", club);
        db.update(T_USERS, cu, "_id = ?", new String[]{String.valueOf(noam)});

        ContentValues cp = new ContentValues();
        cp.put("description", "מועדון השחמט של פתח תקווה — אימונים, טורנירים וקבוצות לכל הרמות.");
        cp.put("address", "רחוב ההגנה 1, פתח תקווה");
        cp.put("contact_phone", "+972500000001");
        cp.put("hall", "אולם המשחקים, מרכז הנוער");
        db.update(T_CLUBS, cp, "_id = ?", new String[]{String.valueOf(club)});

        long admin = insertUser(db, "מנהל המועדון", "admin@ptchess.co.il",
                "Admin#2026", "+972500000001", Role.ADMIN, User.STATUS_ACTIVE, club, 0);
        long tutor = insertUser(db, "דוד לוי", "tutor@ptchess.co.il",
                "Tutor#2026", "+972500000002", Role.TUTOR, User.STATUS_ACTIVE, club, 2100);
        long child1 = insertUser(db, "יונתן כהן", "child@ptchess.co.il",
                "Child#2026", null, Role.CHILD, User.STATUS_ACTIVE, club, 1600);
        long child2 = insertUser(db, "מאיה פרץ", "maya@ptchess.co.il",
                "Child#2026", null, Role.CHILD, User.STATUS_ACTIVE, club, 1200);
        long parent = insertUser(db, "רונית כהן", "parent@ptchess.co.il",
                "Parent#2026", "+972500000003", Role.PARENT, User.STATUS_ACTIVE, club, 0);
        insertUser(db, "איתי גולן", "pending@ptchess.co.il",
                "Pending#2026", null, Role.CHILD, User.STATUS_PENDING, club, 0);

        ContentValues pc = new ContentValues();
        pc.put("parent_id", parent);
        pc.put("child_id", child1);
        db.insert(T_PARENT_CHILD, null, pc);

        long gA = insertGroup(db, club, "קבוצה א׳ - מתחילים", 0, "17:00", tutor);
        long gB = insertGroup(db, club, "קבוצה ב׳ - מתקדמים", 2, "18:00", tutor);
        addMember(db, gA, child1);
        addMember(db, gA, child2);
        addMember(db, gB, child1);

        insertPuzzle(db, club, "מט בתור אחד - שורה אחורית", 1,
                "6k1/5ppp/8/8/8/8/8/R6K w - - 0 1", "a1a8", admin);
        insertPuzzle(db, club, "מזלג פרש", 1,
                "r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1", "d5c7", admin);
        insertPuzzle(db, club, "רגלי זוכה מלכה", 2,
                "8/8/8/3q4/4P3/8/8/4K1k1 w - - 0 1", "e4d5", tutor);
        insertPuzzle(db, club, "מט המלכה", 2,
                "7k/8/6KQ/8/8/8/8/8 w - - 0 1", "h6h7", tutor);
        insertPuzzle(db, club, "מט חנוק", 3,
                "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", "g5f7", admin);
        insertPuzzle(db, club, "זכייה בצריח", 3,
                "3r2k1/5ppp/8/8/8/8/8/3QK3 w - - 0 1", "d1d8", tutor);

        insertBook(db, club, "אמנות הקומבינציה", "מקסים בלוך", "עברית",
                "tactics", "BOTH", 1200, 1800, admin);
        insertBook(db, club, "My System", "Aron Nimzowitsch", "English",
                "strategy", "BOTH", 1600, 2200, admin);
        insertBook(db, club, "התקפה על המלך", "יעקב נוידיטש", "עברית",
                "attack", "WHITE", 1400, 2000, tutor);

        insertNews(db, club, "פתיחת שנת הפעילות", "ברוכים הבאים למועדון השחמט פתח תקווה! "
                + "האימונים מתחילים ביום ראשון הקרוב.", admin);
        insertNews(db, club, "טורניר פנימי", "טורניר הבזק הפנימי יתקיים בסוף החודש. "
                + "הרשמה אצל המאמנים.", admin);

        long tourney = insertTournament(db, club, "אליפות פתח תקווה לנוער", "2026-06-01");
        insertResult(db, tourney, child1, 4.5, 6, 3);
        insertResult(db, tourney, child2, 3.0, 6, 8);

        insertAssignment(db, club, gA, "תרגילי מט בשניים",
                "פתרו את 10 התרגילים המצורפים והביאו למפגש הבא.", null, "2026-07-25", tutor);

        // A small club roster so name-based semi-auto approval can be demoed.
        insertPlayer(db, club, "IL-1001", "דניאל כהן", 1450, "ISR");
        insertPlayer(db, club, "IL-1002", "נועה לוי", 1320, "ISR");
        insertPlayer(db, club, "IL-1003", "יונתן כהן", 1600, "ISR");
    }

    private void insertPlayer(SQLiteDatabase db, long clubId, String externalId,
                              String name, int rating, String fed) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("external_id", externalId);
        v.put("full_name", name);
        v.put("rating", rating);
        v.put("federation", fed);
        db.insertWithOnConflict(T_PLAYERS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private long insertGroup(SQLiteDatabase db, long clubId, String name,
                             int dayIndex, String time, long tutorId) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
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

    private void insertPuzzle(SQLiteDatabase db, long clubId, String title, int level,
                              String fen, String sol, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("title", title);
        v.put("level", level);
        v.put("fen", fen);
        v.put("solution_uci", sol);
        v.put("created_by", by);
        db.insert(T_PUZZLES, null, v);
    }

    private void insertBook(SQLiteDatabase db, long clubId, String title, String author,
                            String lang, String category, String side,
                            int rMin, int rMax, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
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

    private void insertNews(SQLiteDatabase db, long clubId, String title, String body, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("title", title);
        v.put("body", body);
        v.put("date", "2026-07-15");
        v.put("created_by", by);
        db.insert(T_NEWS, null, v);
    }

    private long insertTournament(SQLiteDatabase db, long clubId, String name, String date) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
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

    private long insertAssignment(SQLiteDatabase db, long clubId, long groupId, String title,
                                  String desc, String fileUri, String due, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
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
