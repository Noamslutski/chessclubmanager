package com.ptchess.club.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.ptchess.club.data.model.Assignment;
import com.ptchess.club.data.model.AuthResult;
import com.ptchess.club.data.model.Book;
import com.ptchess.club.data.model.Club;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.NewsItem;
import com.ptchess.club.data.model.Player;
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
        public enum Status {
            SUCCESS_PENDING, SUCCESS_AUTO_APPROVED, SUCCESS_PARENT, SUCCESS_CLUB_CREATED,
            EMAIL_EXISTS, INVALID
        }
        public final Status status;
        public final String linkedChildName; // set when SUCCESS_PARENT
        public RegisterResult(Status status, String linkedChildName) {
            this.status = status;
            this.linkedChildName = linkedChildName;
        }
    }

    /**
     * Registers a new user.
     * <ul>
     *   <li>Valid child credentials → active PARENT in the child's club.</li>
     *   <li>A new club name → the user creates and owns that club as an active ADMIN.</li>
     *   <li>Otherwise → a CHILD pending admin approval in the chosen existing club.</li>
     * </ul>
     */
    public RegisterResult register(String fullName, String email, String password,
                                   String phone, String childEmail, String childPassword,
                                   long joinClubId, String newClubName) {
        String normEmail = InputValidator.normalizeEmail(email);
        if (!InputValidator.isValidName(fullName)
                || !InputValidator.isValidEmail(normEmail)
                || !InputValidator.isStrongPassword(password)) {
            return new RegisterResult(RegisterResult.Status.INVALID, null);
        }
        if (getUserByEmail(normEmail) != null) {
            return new RegisterResult(RegisterResult.Status.EMAIL_EXISTS, null);
        }

        // Optional parent linking (identity verified by the child's own credentials).
        User linkedChild = null;
        if (childEmail != null && !childEmail.trim().isEmpty()) {
            long childId = verifyChildCredentials(childEmail, childPassword);
            if (childId > 0) linkedChild = getUserById(childId);
        }

        boolean creatingClub = newClubName != null && !newClubName.trim().isEmpty();

        long clubId;
        Role role;
        String status;
        int rating = 0;
        boolean autoApproved = false;
        String matchedName = null;
        if (linkedChild != null) {
            clubId = linkedChild.clubId;
            role = Role.PARENT;
            status = User.STATUS_ACTIVE;
        } else if (creatingClub) {
            clubId = 0; // set after the club row is created
            role = Role.ADMIN;
            status = User.STATUS_ACTIVE;
        } else {
            if (joinClubId <= 0) return new RegisterResult(RegisterResult.Status.INVALID, null);
            clubId = joinClubId;
            role = Role.CHILD;
            // Semi-automatic approval: if the name matches an imported club player,
            // approve immediately and adopt that player's rating.
            Player match = findRosterPlayerByName(joinClubId, fullName);
            if (match != null) {
                status = User.STATUS_ACTIVE;
                rating = match.rating;
                autoApproved = true;
                matchedName = match.fullName;
            } else {
                status = User.STATUS_PENDING;
            }
        }

        ContentValues v = new ContentValues();
        v.put("full_name", InputValidator.sanitizeLine(fullName, InputValidator.MAX_NAME));
        v.put("email", normEmail);
        v.put("password_hash", PasswordHasher.hash(password));
        v.put("phone", InputValidator.sanitizeLine(phone, 30));
        v.put("role", role.name());
        v.put("status", status);
        v.put("club_id", clubId);
        v.put("rating", rating);
        v.put("created_at", System.currentTimeMillis());

        long newId = helper.getWritableDatabase().insert(DbHelper.T_USERS, null, v);
        if (newId <= 0) {
            return new RegisterResult(RegisterResult.Status.INVALID, null);
        }

        if (creatingClub) {
            long club = createClub(newClubName, newId, false);
            ContentValues cu = new ContentValues();
            cu.put("club_id", club);
            helper.getWritableDatabase().update(DbHelper.T_USERS, cu,
                    "_id = ?", new String[]{String.valueOf(newId)});
            return new RegisterResult(RegisterResult.Status.SUCCESS_CLUB_CREATED, newClubName.trim());
        }
        if (linkedChild != null) {
            linkParentToChild(newId, linkedChild.id);
            return new RegisterResult(RegisterResult.Status.SUCCESS_PARENT, linkedChild.fullName);
        }
        if (autoApproved) {
            return new RegisterResult(RegisterResult.Status.SUCCESS_AUTO_APPROVED, matchedName);
        }
        return new RegisterResult(RegisterResult.Status.SUCCESS_PENDING, null);
    }

    /**
     * Finds a club roster player whose name matches the given name (case- and
     * spacing-insensitive; also tolerates "Last, First" vs "First Last").
     */
    public Player findRosterPlayerByName(long clubId, String name) {
        String target = normalizeName(name);
        if (target.isEmpty()) return null;
        for (Player p : getPlayers(clubId)) {
            String cand = normalizeName(p.fullName);
            if (cand.equals(target) || flipComma(cand).equals(target)) return p;
        }
        return null;
    }

    private static String normalizeName(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static String flipComma(String normalized) {
        int comma = normalized.indexOf(',');
        if (comma < 0) return normalized;
        String last = normalized.substring(0, comma).trim();
        String first = normalized.substring(comma + 1).trim();
        return (first + " " + last).trim();
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
                new String[]{"_id", "full_name", "email", "phone", "role", "status", "club_id", "rating", "password_hash"},
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
                new String[]{"_id", "full_name", "email", "phone", "role", "status", "club_id", "rating"},
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? readUser(c) : null;
        }
    }

    public User getUserByEmail(String email) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS,
                new String[]{"_id", "full_name", "email", "phone", "role", "status", "club_id", "rating"},
                "email = ?", new String[]{InputValidator.normalizeEmail(email)},
                null, null, null)) {
            return c.moveToFirst() ? readUser(c) : null;
        }
    }

    public List<User> getPendingUsers(long clubId) {
        return queryUsers("status = ? AND club_id = ?",
                new String[]{User.STATUS_PENDING, String.valueOf(clubId)}, "created_at ASC");
    }

    public List<User> getAllUsers(long clubId) {
        return queryUsers("club_id = ?",
                new String[]{String.valueOf(clubId)}, "full_name ASC");
    }

    /** Active members of a club with a given role (tutors / children pickers). */
    public List<User> getClubUsersByRole(long clubId, Role role) {
        return queryUsers("club_id = ? AND role = ? AND status = ?",
                new String[]{String.valueOf(clubId), role.name(), User.STATUS_ACTIVE},
                "full_name ASC");
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
                new String[]{"_id", "full_name", "email", "phone", "role", "status", "club_id", "rating"},
                where, args, null, null, order)) {
            while (c.moveToNext()) out.add(readUser(c));
        }
        return out;
    }

    private User readUser(Cursor c) {
        int ratingIdx = c.getColumnIndex("rating"); // not every projection selects it
        int rating = ratingIdx >= 0 ? c.getInt(ratingIdx) : 0;
        return new User(
                c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getString(c.getColumnIndexOrThrow("full_name")),
                c.getString(c.getColumnIndexOrThrow("email")),
                c.getString(c.getColumnIndexOrThrow("phone")),
                Role.fromName(c.getString(c.getColumnIndexOrThrow("role"))),
                c.getString(c.getColumnIndexOrThrow("status")),
                c.getLong(c.getColumnIndexOrThrow("club_id")),
                rating);
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
        String sql = "SELECT u._id, u.full_name, u.email, u.phone, u.role, u.status, u.club_id "
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
                return queryGroups("g.club_id = ?", new String[]{String.valueOf(user.clubId)});
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
        String sql = "SELECT u._id, u.full_name, u.email, u.phone, u.role, u.status, u.club_id "
                + "FROM " + DbHelper.T_USERS + " u "
                + "JOIN " + DbHelper.T_GROUP_MEMBERS + " gm ON gm.child_id = u._id "
                + "WHERE gm.group_id = ? ORDER BY u.full_name";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(groupId)})) {
            while (c.moveToNext()) out.add(readUser(c));
        }
        return out;
    }

    // ============================================================ PUZZLES

    public List<Puzzle> getPuzzles(long clubId) {
        List<Puzzle> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_PUZZLES,
                new String[]{"_id", "title", "level", "fen", "solution_uci"},
                "club_id = ?", new String[]{String.valueOf(clubId)},
                null, null, "level ASC, _id ASC")) {
            while (c.moveToNext()) {
                out.add(new Puzzle(c.getLong(0), c.getString(1), c.getInt(2),
                        c.getString(3), c.getString(4)));
            }
        }
        return out;
    }

    public long addPuzzle(long clubId, String title, int level,
                          String fen, String solutionUci, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("title", InputValidator.sanitizeLine(title, 120));
        v.put("level", level);
        v.put("fen", fen);
        v.put("solution_uci", solutionUci);
        v.put("created_by", by);
        return helper.getWritableDatabase().insert(DbHelper.T_PUZZLES, null, v);
    }

    // ============================================================ LIBRARY

    public List<Book> getBooks(long clubId) {
        List<Book> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_BOOKS,
                new String[]{"_id", "title", "author", "language", "category",
                        "side", "rating_min", "rating_max", "file_uri"},
                "club_id = ?", new String[]{String.valueOf(clubId)},
                null, null, "title ASC")) {
            while (c.moveToNext()) {
                out.add(new Book(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getString(5),
                        c.getInt(6), c.getInt(7), c.getString(8)));
            }
        }
        return out;
    }

    public long addBook(long clubId, String title, String author, String language, String category,
                        String side, int rMin, int rMax, String fileUri, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
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
                return queryAssignments("a.club_id = ?",
                        new String[]{String.valueOf(user.clubId)}, -1);
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

    public long addAssignment(long clubId, long groupId, String title, String description,
                              String fileUri, String dueDate, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
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
                return queryResults(
                        "r.child_id IN (SELECT _id FROM " + DbHelper.T_USERS + " WHERE club_id = ?)",
                        new String[]{String.valueOf(user.clubId)});
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

    /** Finds a club tournament by name+date, creating it if needed. Returns its id. */
    public long addOrGetTournament(long clubId, String name, String date) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try (Cursor c = db.query(DbHelper.T_TOURNAMENTS, new String[]{"_id"},
                "club_id = ? AND name = ? AND date = ?",
                new String[]{String.valueOf(clubId), name, date}, null, null, null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("name", InputValidator.sanitizeLine(name, 120));
        v.put("date", InputValidator.sanitizeLine(date, 20));
        return db.insert(DbHelper.T_TOURNAMENTS, null, v);
    }

    public long addResult(long tournamentId, long childId, double points, int games, int standing) {
        ContentValues v = new ContentValues();
        v.put("tournament_id", tournamentId);
        v.put("child_id", childId);
        v.put("points", points);
        v.put("games", games);
        v.put("standing", standing);
        return helper.getWritableDatabase().insert(DbHelper.T_RESULTS, null, v);
    }

    /**
     * Imports tournament results from CSV text. Expected columns (header row
     * optional): child_email, tournament, date, points, games, standing.
     * Rows whose email does not match a registered user are skipped.
     * Returns the number of results inserted.
     */
    public int importResultsCsv(long clubId, String csv) {
        if (csv == null || csv.trim().isEmpty()) return 0;
        int added = 0;
        for (String rawLine : csv.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",");
            if (cols.length < 6) continue;
            String email = InputValidator.normalizeEmail(cols[0].trim());
            if (!InputValidator.isValidEmail(email)) continue; // skips header row too
            User child = getUserByEmail(email);
            if (child == null || child.clubId != clubId) continue; // stay within the club
            try {
                String tournament = cols[1].trim();
                String date = cols[2].trim();
                double points = Double.parseDouble(cols[3].trim());
                int games = Integer.parseInt(cols[4].trim());
                int standing = Integer.parseInt(cols[5].trim());
                long tId = addOrGetTournament(clubId, tournament, date);
                addResult(tId, child.id, points, games, standing);
                added++;
            } catch (NumberFormatException ignored) {
                // malformed numeric cell — skip this row
            }
        }
        return added;
    }

    // ============================================================ NEWS

    public List<NewsItem> getNews(long clubId) {
        List<NewsItem> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT n._id, n.title, n.body, n.date, COALESCE(u.full_name,'') "
                + "FROM " + DbHelper.T_NEWS + " n "
                + "LEFT JOIN " + DbHelper.T_USERS + " u ON u._id = n.created_by "
                + "WHERE n.club_id = ? ORDER BY n._id DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(clubId)})) {
            while (c.moveToNext()) {
                out.add(new NewsItem(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4)));
            }
        }
        return out;
    }

    public long addNews(long clubId, String title, String body, long by) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("title", InputValidator.sanitizeLine(title, 120));
        v.put("body", InputValidator.sanitizeText(body));
        v.put("date", java.text.DateFormat.getDateInstance().format(new java.util.Date()));
        v.put("created_by", by);
        return helper.getWritableDatabase().insert(DbHelper.T_NEWS, null, v);
    }

    // ============================================================ CONTACTS

    public User getPrimaryTutorForChild(long childId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT u._id, u.full_name, u.email, u.phone, u.role, u.status, u.club_id "
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

    /** An active admin of the given club (for parent contact). */
    public User getClubAdmin(long clubId) {
        List<User> admins = queryUsers("role = ? AND status = ? AND club_id = ?",
                new String[]{Role.ADMIN.name(), User.STATUS_ACTIVE, String.valueOf(clubId)},
                "_id ASC");
        return admins.isEmpty() ? null : admins.get(0);
    }

    // ============================================================ CLUBS

    public static boolean isSuperAdmin(User user) {
        return user != null && user.email != null
                && user.email.equalsIgnoreCase(DbHelper.SUPER_ADMIN_EMAIL);
    }

    public long createClub(String name, long ownerId, boolean verified) {
        ContentValues v = new ContentValues();
        v.put("name", InputValidator.sanitizeLine(name, 80));
        v.put("owner_id", ownerId);
        v.put("verified", verified ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        return helper.getWritableDatabase().insert(DbHelper.T_CLUBS, null, v);
    }

    public List<Club> getClubs() {
        return queryClubs(null, null);
    }

    public List<Club> getUnverifiedClubs() {
        return queryClubs("c.verified = 0", null);
    }

    public Club getClub(long clubId) {
        List<Club> clubs = queryClubs("c._id = ?", new String[]{String.valueOf(clubId)});
        return clubs.isEmpty() ? null : clubs.get(0);
    }

    public boolean isClubVerified(long clubId) {
        Club c = getClub(clubId);
        return c != null && c.verified;
    }

    public boolean setClubVerified(long clubId, boolean verified) {
        ContentValues v = new ContentValues();
        v.put("verified", verified ? 1 : 0);
        return helper.getWritableDatabase().update(DbHelper.T_CLUBS, v,
                "_id = ?", new String[]{String.valueOf(clubId)}) > 0;
    }

    private List<Club> queryClubs(String where, String[] args) {
        List<Club> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        StringBuilder sql = new StringBuilder(
                "SELECT c._id, c.name, c.owner_id, COALESCE(u.full_name,''), c.verified "
                        + "FROM " + DbHelper.T_CLUBS + " c "
                        + "LEFT JOIN " + DbHelper.T_USERS + " u ON u._id = c.owner_id ");
        if (where != null) sql.append("WHERE ").append(where).append(' ');
        sql.append("ORDER BY c.name");
        try (Cursor c = db.rawQuery(sql.toString(), args)) {
            while (c.moveToNext()) {
                out.add(new Club(c.getLong(0), c.getString(1), c.getLong(2),
                        c.getString(3), c.getInt(4) == 1));
            }
        }
        return out;
    }

    // ============================================================ GROUP MANAGEMENT

    public long addGroup(long clubId, String name, int dayIndex, String time, long tutorId) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("name", InputValidator.sanitizeLine(name, 80));
        v.put("day_index", dayIndex);
        v.put("time", InputValidator.sanitizeLine(time, 10));
        v.put("tutor_id", tutorId);
        return helper.getWritableDatabase().insert(DbHelper.T_GROUPS, null, v);
    }

    public void addGroupMember(long groupId, long childId) {
        ContentValues v = new ContentValues();
        v.put("group_id", groupId);
        v.put("child_id", childId);
        helper.getWritableDatabase().insertWithOnConflict(
                DbHelper.T_GROUP_MEMBERS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    // ============================================================ PLAYERS (roster)

    /** Adds a federation player to a club, deduped per club by external id. */
    public boolean addPlayer(long clubId, String externalId, String name, int rating, String fed) {
        ContentValues v = new ContentValues();
        v.put("club_id", clubId);
        v.put("external_id", externalId);
        v.put("full_name", InputValidator.sanitizeLine(name, InputValidator.MAX_NAME));
        v.put("rating", rating);
        v.put("federation", InputValidator.sanitizeLine(fed, 10));
        long id = helper.getWritableDatabase().insertWithOnConflict(
                DbHelper.T_PLAYERS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return id > 0; // -1 when the (club_id, external_id) pair already exists
    }

    public List<com.ptchess.club.data.model.Player> getPlayers(long clubId) {
        List<com.ptchess.club.data.model.Player> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_PLAYERS,
                new String[]{"_id", "external_id", "full_name", "rating", "federation"},
                "club_id = ?", new String[]{String.valueOf(clubId)},
                null, null, "rating DESC, full_name ASC")) {
            while (c.moveToNext()) {
                out.add(new com.ptchess.club.data.model.Player(
                        c.getLong(0), c.getString(1), c.getString(2),
                        c.getInt(3), c.getString(4)));
            }
        }
        return out;
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
