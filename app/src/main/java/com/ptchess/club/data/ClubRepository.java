package com.ptchess.club.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.ptchess.club.data.model.Assignment;
import com.ptchess.club.data.model.AuthResult;
import com.ptchess.club.data.model.Book;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.NewsItem;
import com.ptchess.club.data.model.Puzzle;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.TournamentResult;
import com.ptchess.club.data.model.User;
import com.ptchess.club.security.InputValidator;
import com.ptchess.club.security.PasswordHasher;

import java.util.ArrayList;
import java.util.List;

/**
 * Single access point to the local data store. Every statement is parameterized
 * with {@code ?} placeholders and {@code String[]} bind args — user input never
 * becomes part of a SQL string, which is what makes injection impossible.
 *
 * <p>Also owns brute-force lockout: repeated failed logins for an email trigger
 * an escalating temporary lock.</p>
 */
public final class ClubRepository {

    // Brute-force policy
    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_LOCK_MIN = 5;

    private static volatile ClubRepository instance;

    private final DbHelper helper;

    private ClubRepository(Context context) {
        helper = new DbHelper(context);
    }

    public static ClubRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ClubRepository.class) {
                if (instance == null) {
                    instance = new ClubRepository(context);
                }
            }
        }
        return instance;
    }

    // ============================================================ AUTH

    /** Registration outcome. */
    public static class RegisterResult {
        public enum Status { SUCCESS_PENDING, SUCCESS_PARENT, EMAIL_EXISTS, INVALID }
        public final Status status;
        public final String linkedChildName; // set when SUCCESS_PARENT
        public RegisterResult(Status status, String linkedChildName) {
            this.status = status;
            this.linkedChildName = linkedChildName;
        }
    }

    /**
     * Registers a new user. If valid child credentials are supplied the account
     * is created as an active PARENT linked to that child; otherwise it is a
     * CHILD pending admin approval.
     */
    public RegisterResult register(String fullName, String email, String password,
                                   String phone, String childEmail, String childPassword) {
        String normEmail = InputValidator.normalizeEmail(email);
        if (!InputValidator.isValidName(fullName)
                || !InputValidator.isValidEmail(normEmail)
                || !InputValidator.isStrongPassword(password)) {
            return new RegisterResult(RegisterResult.Status.INVALID, null);
        }
        if (getUserByEmail(normEmail) != null) {
            return new RegisterResult(RegisterResult.Status.EMAIL_EXISTS, null);
        }

        // Optional parent linking
        User linkedChild = null;
        if (childEmail != null && !childEmail.trim().isEmpty()) {
            long childId = verifyChildCredentials(childEmail, childPassword);
            if (childId > 0) {
                linkedChild = getUserById(childId);
            }
        }

        Role role = (linkedChild != null) ? Role.PARENT : Role.CHILD;
        String status = (linkedChild != null) ? User.STATUS_ACTIVE : User.STATUS_PENDING;

        ContentValues v = new ContentValues();
        v.put("full_name", InputValidator.sanitizeLine(fullName, InputValidator.MAX_NAME));
        v.put("email", normEmail);
        v.put("password_hash", PasswordHasher.hash(password));
        v.put("phone", InputValidator.sanitizeLine(phone, 30));
        v.put("role", role.name());
        v.put("status", status);
        v.put("created_at", System.currentTimeMillis());

        long newId = helper.getWritableDatabase().insert(DbHelper.T_USERS, null, v);
        if (newId <= 0) {
            return new RegisterResult(RegisterResult.Status.INVALID, null);
        }

        if (linkedChild != null) {
            linkParentToChild(newId, linkedChild.id);
            return new RegisterResult(RegisterResult.Status.SUCCESS_PARENT, linkedChild.fullName);
        }
        return new RegisterResult(RegisterResult.Status.SUCCESS_PENDING, null);
    }

    public AuthResult authenticate(String email, String password) {
        String normEmail = InputValidator.normalizeEmail(email);
        long now = System.currentTimeMillis();

        long[] attempt = getLoginAttempt(normEmail);
        if (attempt[1] > now) {
            return AuthResult.locked(minutesUntil(attempt[1], now));
        }

        SQLiteDatabase db = helper.getReadableDatabase();
        String hash = null;
        User user = null;
        try (Cursor c = db.query(DbHelper.T_USERS,
                new String[]{"_id", "full_name", "email", "phone", "role", "status", "password_hash"},
                "email = ?", new String[]{normEmail}, null, null, null)) {
            if (c.moveToFirst()) {
                user = readUser(c);
                hash = c.getString(c.getColumnIndexOrThrow("password_hash"));
            }
        }

        boolean ok = user != null && PasswordHasher.verify(password, hash);
        if (!ok) {
            long lockUntil = recordFailure(normEmail);
            if (lockUntil > now) {
                return AuthResult.locked(minutesUntil(lockUntil, now));
            }
            return AuthResult.of(AuthResult.Status.WRONG_CREDENTIALS);
        }

        clearLoginAttempts(normEmail);
        if (User.STATUS_PENDING.equals(user.status)) {
            return AuthResult.of(AuthResult.Status.PENDING_APPROVAL);
        }
        if (User.STATUS_REJECTED.equals(user.status)) {
            return AuthResult.of(AuthResult.Status.REJECTED);
        }
        return AuthResult.success(user);
    }

    /** Password reset stub. A backend must email a signed reset link (see README). */
    public boolean requestPasswordReset(String email) {
        // Returns true regardless of existence to avoid leaking which emails exist.
        return InputValidator.isValidEmail(InputValidator.normalizeEmail(email));
    }

    public boolean changePassword(long userId, String currentPassword, String newPassword) {
        if (!InputValidator.isStrongPassword(newPassword)) return false;
        SQLiteDatabase db = helper.getWritableDatabase();
        String hash = null;
        try (Cursor c = db.query(DbHelper.T_USERS, new String[]{"password_hash"},
                "_id = ?", new String[]{String.valueOf(userId)}, null, null, null)) {
            if (c.moveToFirst()) hash = c.getString(0);
        }
        if (hash == null || !PasswordHasher.verify(currentPassword, hash)) return false;
        ContentValues v = new ContentValues();
        v.put("password_hash", PasswordHasher.hash(newPassword));
        return db.update(DbHelper.T_USERS, v, "_id = ?",
                new String[]{String.valueOf(userId)}) > 0;
    }

    private long verifyChildCredentials(String childEmail, String childPassword) {
        User child = getUserByEmail(InputValidator.normalizeEmail(childEmail));
        if (child == null || child.role != Role.CHILD) return -1;
        SQLiteDatabase db = helper.getReadableDatabase();
        String hash = null;
        try (Cursor c = db.query(DbHelper.T_USERS, new String[]{"password_hash"},
                "_id = ?", new String[]{String.valueOf(child.id)}, null, null, null)) {
            if (c.moveToFirst()) hash = c.getString(0);
        }
        return PasswordHasher.verify(childPassword, hash) ? child.id : -1;
    }

    // ------------------------------------------- brute-force bookkeeping

    private long[] getLoginAttempt(String email) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_LOGIN_ATTEMPTS,
                new String[]{"fail_count", "lock_until"},
                "email = ?", new String[]{email}, null, null, null)) {
            if (c.moveToFirst()) {
                return new long[]{c.getLong(0), c.getLong(1)};
            }
        }
        return new long[]{0, 0};
    }

    /** Records a failed attempt; returns the lock_until timestamp (0 if not locked). */
    private long recordFailure(String email) {
        long[] a = getLoginAttempt(email);
        long fail = a[0] + 1;
        long lockUntil = 0;
        if (fail >= MAX_ATTEMPTS) {
            int over = (int) (fail - MAX_ATTEMPTS);          // 0,1,2,...
            long minutes = Math.min(60, BASE_LOCK_MIN * (1L << Math.min(over, 4)));
            lockUntil = System.currentTimeMillis() + minutes * 60_000L;
        }
        ContentValues v = new ContentValues();
        v.put("email", email);
        v.put("fail_count", fail);
        v.put("lock_until", lockUntil);
        helper.getWritableDatabase().insertWithOnConflict(
                DbHelper.T_LOGIN_ATTEMPTS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return lockUntil;
    }

    private void clearLoginAttempts(String email) {
        helper.getWritableDatabase().delete(DbHelper.T_LOGIN_ATTEMPTS,
                "email = ?", new String[]{email});
    }

    private int minutesUntil(long target, long now) {
        return (int) Math.max(1, Math.ceil((target - now) / 60_000.0));
    }

    // ============================================================ USERS

    public User getUserById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS,
                new String[]{"_id", "full_name", "email", "phone", "role", "status"},
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? readUser(c) : null;
        }
    }

    public User getUserByEmail(String email) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS,
                new String[]{"_id", "full_name", "email", "phone", "role", "status"},
                "email = ?", new String[]{InputValidator.normalizeEmail(email)},
                null, null, null)) {
            return c.moveToFirst() ? readUser(c) : null;
        }
    }

    public List<User> getPendingUsers() {
        return queryUsers("status = ?", new String[]{User.STATUS_PENDING}, "created_at ASC");
    }

    public List<User> getAllUsers() {
        return queryUsers(null, null, "full_name ASC");
    }

    public boolean approveUser(long id) {
        return updateStatus(id, User.STATUS_ACTIVE);
    }

    public boolean rejectUser(long id) {
        return updateStatus(id, User.STATUS_REJECTED);
    }

    public boolean setRole(long id, Role role) {
        ContentValues v = new ContentValues();
        v.put("role", role.name());
        return helper.getWritableDatabase().update(DbHelper.T_USERS, v,
                "_id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    private boolean updateStatus(long id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        return helper.getWritableDatabase().update(DbHelper.T_USERS, v,
                "_id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    private List<User> queryUsers(String where, String[] args, String order) {
        List<User> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS,
                new String[]{"_id", "full_name", "email", "phone", "role", "status"},
                where, args, null, null, order)) {
            while (c.moveToNext()) out.add(readUser(c));
        }
        return out;
    }

    private User readUser(Cursor c) {
        return new User(
                c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getString(c.getColumnIndexOrThrow("full_name")),
                c.getString(c.getColumnIndexOrThrow("email")),
                c.getString(c.getColumnIndexOrThrow("phone")),
                Role.fromName(c.getString(c.getColumnIndexOrThrow("role"))),
                c.getString(c.getColumnIndexOrThrow("status")));
    }

    // ============================================================ PARENT / CHILD

    public void linkParentToChild(long parentId, long childId) {
        ContentValues v = new ContentValues();
        v.put("parent_id", parentId);
        v.put("child_id", childId);
        helper.getWritableDatabase().insertWithOnConflict(
                DbHelper.T_PARENT_CHILD, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Links a child to an existing parent using the child's own credentials. */
    public String addChildToParent(long parentId, String childEmail, String childPassword) {
        long childId = verifyChildCredentials(childEmail, childPassword);
        if (childId <= 0) return null;
        linkParentToChild(parentId, childId);
        User child = getUserById(childId);
        return child != null ? child.fullName : null;
    }

    public List<User> getChildrenForParent(long parentId) {
        List<User> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT u._id, u.full_name, u.email, u.phone, u.role, u.status "
                + "FROM " + DbHelper.T_USERS + " u "
                + "JOIN " + DbHelper.T_PARENT_CHILD + " pc ON pc.child_id = u._id "
                + "WHERE pc.parent_id = ? ORDER BY u.full_name";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(parentId)})) {
            while (c.moveToNext()) out.add(readUser(c));
        }
        return out;
    }

    private List<Long> childIdsForParent(long parentId) {
        List<Long> ids = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_PARENT_CHILD, new String[]{"child_id"},
                "parent_id = ?", new String[]{String.valueOf(parentId)}, null, null, null)) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        return ids;
    }

    // ============================================================ GROUPS

    public List<Group> getGroupsForUser(User user) {
        switch (user.role) {
            case ADMIN:
                return queryGroups(null, null);
            case TUTOR:
                return queryGroups("g.tutor_id = ?", new String[]{String.valueOf(user.id)});
            case CHILD:
                return queryGroups(
                        "g._id IN (SELECT group_id FROM " + DbHelper.T_GROUP_MEMBERS
                                + " WHERE child_id = ?)",
                        new String[]{String.valueOf(user.id)});
            case PARENT:
                List<Long> kids = childIdsForParent(user.id);
                if (kids.isEmpty()) return new ArrayList<>();
                String in = placeholders(kids.size());
                String[] args = toStringArgs(kids);
                return queryGroups(
                        "g._id IN (SELECT group_id FROM " + DbHelper.T_GROUP_MEMBERS
                                + " WHERE child_id IN (" + in + "))", args);
            default:
                return new ArrayList<>();
        }
    }

    private List<Group> queryGroups(String where, String[] args) {
        List<Group> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder(
                "SELECT g._id, g.name, g.day_index, g.time, g.tutor_id, "
                        + "COALESCE(t.full_name,'') AS tutor_name, "
                        + "(SELECT COUNT(*) FROM " + DbHelper.T_GROUP_MEMBERS
                        + " gm WHERE gm.group_id = g._id) AS members "
                        + "FROM " + DbHelper.T_GROUPS + " g "
                        + "LEFT JOIN " + DbHelper.T_USERS + " t ON t._id = g.tutor_id ");
        if (where != null) sql.append("WHERE ").append(where).append(' ');
        sql.append("ORDER BY g.day_index, g.time");
        try (Cursor c = db.rawQuery(sql.toString(), args)) {
            while (c.moveToNext()) {
                out.add(new Group(
                        c.getLong(0), c.getString(1), c.getInt(2), c.getString(3),
                        c.getLong(4), c.getString(5), c.getInt(6)));
            }
        }
        return out;
    }

    public List<User> getGroupMembers(long groupId) {
        List<User> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT u._id, u.full_name, u.email, u.phone, u.role, u.status "
                + "FROM " + DbHelper.T_USERS + " u "
                + "JOIN " + DbHelper.T_GROUP_MEMBERS + " gm ON gm.child_id = u._id "
                + "WHERE gm.group_id = ? ORDER BY u.full_name";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(groupId)})) {
            while (c.moveToNext()) out.add(readUser(c));
        }
        return out;
    }

    // ============================================================ PUZZLES

    public List<Puzzle> getPuzzles() {
        List<Puzzle> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_PUZZLES,
                new String[]{"_id", "title", "level", "fen", "solution_uci"},
                null, null, null, null, "level ASC, _id ASC")) {
            while (c.moveToNext()) {
                out.add(new Puzzle(c.getLong(0), c.getString(1), c.getInt(2),
                        c.getString(3), c.getString(4)));
            }
        }
        return out;
    }

    public long addPuzzle(String title, int level, String fen, String solutionUci, long by) {
        ContentValues v = new ContentValues();
        v.put("title", InputValidator.sanitizeLine(title, 120));
        v.put("level", level);
        v.put("fen", fen);
        v.put("solution_uci", solutionUci);
        v.put("created_by", by);
        return helper.getWritableDatabase().insert(DbHelper.T_PUZZLES, null, v);
    }

    // ============================================================ LIBRARY

    public List<Book> getBooks() {
        List<Book> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_BOOKS,
                new String[]{"_id", "title", "author", "language", "category",
                        "side", "rating_min", "rating_max", "file_uri"},
                null, null, null, null, "title ASC")) {
            while (c.moveToNext()) {
                out.add(new Book(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getString(5),
                        c.getInt(6), c.getInt(7), c.getString(8)));
            }
        }
        return out;
    }

    public long addBook(String title, String author, String language, String category,
                        String side, int rMin, int rMax, String fileUri, long by) {
        ContentValues v = new ContentValues();
        v.put("title", InputValidator.sanitizeLine(title, 120));
        v.put("author", InputValidator.sanitizeLine(author, 80));
        v.put("language", InputValidator.sanitizeLine(language, 40));
        v.put("category", category);
        v.put("side", side);
        v.put("rating_min", rMin);
        v.put("rating_max", rMax);
        v.put("file_uri", fileUri);
        v.put("uploaded_by", by);
        return helper.getWritableDatabase().insert(DbHelper.T_BOOKS, null, v);
    }

    // ============================================================ ASSIGNMENTS

    public List<Assignment> getAssignmentsForUser(User user) {
        List<Long> groupIds = new ArrayList<>();
        long childForStatus = -1;

        switch (user.role) {
            case ADMIN:
                return queryAssignments(null, null, -1);
            case TUTOR:
                return queryAssignments("a.group_id IN (SELECT _id FROM " + DbHelper.T_GROUPS
                        + " WHERE tutor_id = ?)", new String[]{String.valueOf(user.id)}, -1);
            case CHILD:
                childForStatus = user.id;
                groupIds = groupIdsForChild(user.id);
                break;
            case PARENT:
                List<Long> kids = childIdsForParent(user.id);
                for (long k : kids) groupIds.addAll(groupIdsForChild(k));
                break;
        }
        if (groupIds.isEmpty()) return new ArrayList<>();
        String in = placeholders(groupIds.size());
        return queryAssignments("a.group_id IN (" + in + ")",
                toStringArgs(groupIds), childForStatus);
    }

    private List<Long> groupIdsForChild(long childId) {
        List<Long> ids = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_GROUP_MEMBERS, new String[]{"group_id"},
                "child_id = ?", new String[]{String.valueOf(childId)}, null, null, null)) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        return ids;
    }

    private List<Assignment> queryAssignments(String where, String[] args, long childForStatus) {
        List<Assignment> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder(
                "SELECT a._id, a.group_id, g.name, a.title, a.description, a.file_uri, "
                        + "a.due_date, COALESCE(u.full_name,'') "
                        + "FROM " + DbHelper.T_ASSIGNMENTS + " a "
                        + "LEFT JOIN " + DbHelper.T_GROUPS + " g ON g._id = a.group_id "
                        + "LEFT JOIN " + DbHelper.T_USERS + " u ON u._id = a.created_by ");
        if (where != null) sql.append("WHERE ").append(where).append(' ');
        sql.append("ORDER BY a.created_at DESC");
        try (Cursor c = db.rawQuery(sql.toString(), args)) {
            while (c.moveToNext()) {
                long aId = c.getLong(0);
                boolean done = childForStatus > 0 && isAssignmentDone(aId, childForStatus);
                out.add(new Assignment(aId, c.getLong(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7), done));
            }
        }
        return out;
    }

    private boolean isAssignmentDone(long assignmentId, long childId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_ASSIGN_STATUS, new String[]{"completed"},
                "assignment_id = ? AND child_id = ?",
                new String[]{String.valueOf(assignmentId), String.valueOf(childId)},
                null, null, null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }

    public void markAssignmentDone(long assignmentId, long childId, boolean done) {
        ContentValues v = new ContentValues();
        v.put("assignment_id", assignmentId);
        v.put("child_id", childId);
        v.put("completed", done ? 1 : 0);
        helper.getWritableDatabase().insertWithOnConflict(
                DbHelper.T_ASSIGN_STATUS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long addAssignment(long groupId, String title, String description,
                              String fileUri, String dueDate, long by) {
        ContentValues v = new ContentValues();
        v.put("group_id", groupId);
        v.put("title", InputValidator.sanitizeLine(title, 120));
        v.put("description", InputValidator.sanitizeText(description));
        v.put("file_uri", fileUri);
        v.put("due_date", dueDate);
        v.put("created_by", by);
        v.put("created_at", System.currentTimeMillis());
        return helper.getWritableDatabase().insert(DbHelper.T_ASSIGNMENTS, null, v);
    }

    // ============================================================ TOURNAMENTS

    public List<TournamentResult> getResultsForUser(User user) {
        switch (user.role) {
            case ADMIN:
                return queryResults(null, null);
            case CHILD:
                return queryResults("r.child_id = ?", new String[]{String.valueOf(user.id)});
            case PARENT: {
                List<Long> kids = childIdsForParent(user.id);
                if (kids.isEmpty()) return new ArrayList<>();
                return queryResults("r.child_id IN (" + placeholders(kids.size()) + ")",
                        toStringArgs(kids));
            }
            case TUTOR:
                return queryResults(
                        "r.child_id IN (SELECT gm.child_id FROM " + DbHelper.T_GROUP_MEMBERS
                                + " gm JOIN " + DbHelper.T_GROUPS + " g ON g._id = gm.group_id "
                                + "WHERE g.tutor_id = ?)",
                        new String[]{String.valueOf(user.id)});
            default:
                return new ArrayList<>();
        }
    }

    private List<TournamentResult> queryResults(String where, String[] args) {
        List<TournamentResult> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder(
                "SELECT r._id, t.name, t.date, r.child_id, COALESCE(u.full_name,''), "
                        + "r.points, r.games, r.standing "
                        + "FROM " + DbHelper.T_RESULTS + " r "
                        + "LEFT JOIN " + DbHelper.T_TOURNAMENTS + " t ON t._id = r.tournament_id "
                        + "LEFT JOIN " + DbHelper.T_USERS + " u ON u._id = r.child_id ");
        if (where != null) sql.append("WHERE ").append(where).append(' ');
        sql.append("ORDER BY t.date DESC");
        try (Cursor c = db.rawQuery(sql.toString(), args)) {
            while (c.moveToNext()) {
                out.add(new TournamentResult(c.getLong(0), c.getString(1), c.getString(2),
                        c.getLong(3), c.getString(4), c.getDouble(5), c.getInt(6), c.getInt(7)));
            }
        }
        return out;
    }

    // ============================================================ NEWS

    public List<NewsItem> getNews() {
        List<NewsItem> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT n._id, n.title, n.body, n.date, COALESCE(u.full_name,'') "
                + "FROM " + DbHelper.T_NEWS + " n "
                + "LEFT JOIN " + DbHelper.T_USERS + " u ON u._id = n.created_by "
                + "ORDER BY n._id DESC";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new NewsItem(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4)));
            }
        }
        return out;
    }

    public long addNews(String title, String body, long by) {
        ContentValues v = new ContentValues();
        v.put("title", InputValidator.sanitizeLine(title, 120));
        v.put("body", InputValidator.sanitizeText(body));
        v.put("date", java.text.DateFormat.getDateInstance().format(new java.util.Date()));
        v.put("created_by", by);
        return helper.getWritableDatabase().insert(DbHelper.T_NEWS, null, v);
    }

    // ============================================================ CONTACTS

    public User getPrimaryTutorForChild(long childId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT u._id, u.full_name, u.email, u.phone, u.role, u.status "
                + "FROM " + DbHelper.T_USERS + " u "
                + "JOIN " + DbHelper.T_GROUPS + " g ON g.tutor_id = u._id "
                + "JOIN " + DbHelper.T_GROUP_MEMBERS + " gm ON gm.group_id = g._id "
                + "WHERE gm.child_id = ? LIMIT 1";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(childId)})) {
            return c.moveToFirst() ? readUser(c) : null;
        }
    }

    public User getAnyAdmin() {
        List<User> admins = queryUsers("role = ? AND status = ?",
                new String[]{Role.ADMIN.name(), User.STATUS_ACTIVE}, "_id ASC");
        return admins.isEmpty() ? null : admins.get(0);
    }

    // ============================================================ helpers

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    private static String[] toStringArgs(List<Long> ids) {
        String[] args = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) args[i] = String.valueOf(ids.get(i));
        return args;
    }
}
