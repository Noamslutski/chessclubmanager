package com.ptchess.club.data.firebase;

import android.content.Context;

import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.util.Async;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ptchess.club.data.model.Assignment;
import com.ptchess.club.data.model.Book;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.NewsItem;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.TournamentResult;
import com.ptchess.club.data.model.User;
import com.ptchess.club.security.InputValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud (Firestore) store for per-club content, organised as subcollections under
 * {@code clubs/{clubId}/…} (news, books, groups, assignments, results). This is the
 * source of truth whenever Firebase is configured; the Firestore SDK's built-in
 * offline persistence caches reads/writes and syncs them, so the app keeps working
 * without a connection. When Firebase is absent, callers use the local SQLite store.
 *
 * <p>Docs are denormalised and keyed by email so {@link ContentScope} can decide
 * visibility per role without any local-id remapping across devices.
 */
public final class FirebaseContent {

    public interface NewsCb { void ok(List<NewsItem> items); void fail(); }
    public interface BooksCb { void ok(List<Book> items); void fail(); }
    public interface GroupsCb { void ok(List<Group> items); void fail(); }
    public interface AssignmentsCb { void ok(List<Assignment> items); void fail(); }
    public interface ResultsCb { void ok(List<TournamentResult> items); void fail(); }
    public interface UsersCb { void ok(List<User> items); void fail(); }
    public interface CountCb { void done(int count); }
    public interface ParentKidsCb { void ready(List<String> kidEmails); }

    private FirebaseContent() { }

    public static boolean enabled(Context context) {
        return FirebaseServices.isAvailable(context);
    }

    /**
     * Resolves the child emails used to scope a PARENT's cloud reads, then invokes
     * {@code cb} on the main thread. Unions the local parent–child links (same device)
     * with the {@code childEmails} recorded on the parent's cloud profile (which follows
     * them to a fresh device). For non-parents it returns {@code null} (no scoping).
     */
    public static void resolveParentKids(Context ctx, User user, ParentKidsCb cb) {
        if (user.role != Role.PARENT) {
            cb.ready(null);
            return;
        }
        ClubRepository repo = ClubRepository.getInstance(ctx);
        Async.io(() -> {
            List<String> local = new ArrayList<>();
            for (User c : repo.getChildrenForParent(user.id)) {
                if (c.email != null && !c.email.isEmpty()) local.add(c.email.trim().toLowerCase());
            }
            Async.main(() -> {
                String uid = FirebaseAuthService.currentUid();
                if (uid == null) {
                    cb.ready(local);
                    return;
                }
                FirebaseProfile.fetchChildEmails(uid, cloud -> {
                    java.util.LinkedHashSet<String> union = new java.util.LinkedHashSet<>(local);
                    if (cloud != null) {
                        for (String e : cloud) if (e != null && !e.isEmpty()) union.add(e.trim().toLowerCase());
                    }
                    cb.ready(new ArrayList<>(union));
                });
            });
        });
    }

    private static CollectionReference col(long clubId, String name) {
        return FirebaseFirestore.getInstance().collection("clubs")
                .document(String.valueOf(clubId)).collection(name);
    }

    /**
     * Runs {@code onComplete} twice: immediately (the local cache already reflects the
     * write via latency compensation, so a re-read shows it even while offline) and again
     * when the server confirms (which resolves the online race where the first re-read
     * beat the server round-trip). {@code onComplete} is an idempotent reload, so running
     * it twice is harmless — offline writes never leave the UI stale.
     */
    private static void commit(com.google.android.gms.tasks.Task<?> task, Runnable onComplete) {
        if (onComplete == null) return;
        onComplete.run();
        task.addOnCompleteListener(t -> onComplete.run());
    }

    private static void run(Runnable r) {
        if (r != null) r.run();
    }

    // ============================================================ NEWS

    public static void addNews(long clubId, String title, String body, String date,
                               String authorName, Runnable onComplete) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("title", InputValidator.sanitizeLine(title, 120));
            m.put("body", InputValidator.sanitizeText(body));
            m.put("date", date);
            m.put("authorName", authorName != null ? authorName : "");
            m.put("createdAtMs", System.currentTimeMillis());
            commit(col(clubId, "news").add(m), onComplete);
        } catch (Throwable t) {
            run(onComplete);
        }
    }

    public static void fetchNews(long clubId, NewsCb cb) {
        try {
            col(clubId, "news").get()
                    .addOnSuccessListener(qs -> {
                        List<NewsItem> out = new ArrayList<>();
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            out.add(new NewsItem(ContentScope.stableId(d.getId()),
                                    str(d, "title"), str(d, "body"), str(d, "date"),
                                    str(d, "authorName")));
                        }
                        // newest first
                        sortByMsDesc(qs.getDocuments(), out);
                        cb.ok(out);
                    })
                    .addOnFailureListener(e -> cb.fail());
        } catch (Throwable t) {
            cb.fail();
        }
    }

    // ============================================================ BOOKS

    public static void addBook(long clubId, String title, String author, String language,
                               String category, String side, int rMin, int rMax,
                               String fileUrl, Runnable onComplete) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("title", InputValidator.sanitizeLine(title, 120));
            m.put("author", InputValidator.sanitizeLine(author, 80));
            m.put("language", InputValidator.sanitizeLine(language, 40));
            m.put("category", category);
            m.put("side", side);
            m.put("ratingMin", rMin);
            m.put("ratingMax", rMax);
            m.put("fileUrl", fileUrl != null ? fileUrl : "");
            commit(col(clubId, "books").add(m), onComplete);
        } catch (Throwable t) {
            run(onComplete);
        }
    }

    public static void fetchBooks(long clubId, BooksCb cb) {
        try {
            col(clubId, "books").get()
                    .addOnSuccessListener(qs -> {
                        List<Book> out = new ArrayList<>();
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            out.add(new Book(ContentScope.stableId(d.getId()),
                                    str(d, "title"), str(d, "author"), str(d, "language"),
                                    str(d, "category"), str(d, "side"),
                                    (int) lng(d, "ratingMin", 0), (int) lng(d, "ratingMax", 3000),
                                    str(d, "fileUrl")));
                        }
                        out.sort(Comparator.comparing((Book b) -> b.title == null ? "" : b.title));
                        cb.ok(out);
                    })
                    .addOnFailureListener(e -> cb.fail());
        } catch (Throwable t) {
            cb.fail();
        }
    }

    // ============================================================ GROUPS

    public static void addGroup(long clubId, String name, int dayIndex, String time,
                                String tutorEmail, String tutorName, Runnable onComplete) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("name", InputValidator.sanitizeLine(name, 120));
            m.put("dayIndex", dayIndex);
            m.put("time", InputValidator.sanitizeLine(time, 20));
            m.put("tutorEmail", tutorEmail != null ? tutorEmail.trim().toLowerCase() : "");
            m.put("tutorName", tutorName != null ? tutorName : "");
            m.put("memberEmails", new ArrayList<String>());
            m.put("memberNames", new ArrayList<String>());
            commit(col(clubId, "groups").add(m), onComplete);
        } catch (Throwable t) {
            run(onComplete);
        }
    }

    /** Appends members (parallel email/name arrays) to a cloud group document. */
    public static void addGroupMembers(long clubId, String groupCloudId,
                                       List<String> emails, List<String> names, Runnable onComplete) {
        if (groupCloudId == null || emails == null || emails.isEmpty()) {
            run(onComplete);
            return;
        }
        try {
            List<String> lower = new ArrayList<>();
            for (String e : emails) lower.add(e == null ? "" : e.trim().toLowerCase());
            commit(col(clubId, "groups").document(groupCloudId).update(
                    "memberEmails", FieldValue.arrayUnion(lower.toArray()),
                    "memberNames", FieldValue.arrayUnion(names.toArray())), onComplete);
        } catch (Throwable t) {
            run(onComplete);
        }
    }

    /** Fetches all club groups, then returns only those visible to {@code viewer}. */
    public static void fetchGroups(User viewer, List<String> parentChildEmails, GroupsCb cb) {
        fetchAllGroups(viewer.clubId, new GroupsCb() {
            @Override public void ok(List<Group> all) {
                cb.ok(ContentScope.visibleGroups(viewer.role.name(), viewer.email,
                        parentChildEmails, all));
            }
            @Override public void fail() { cb.fail(); }
        });
    }

    /** All of a club's groups (unscoped) — used for the public weekly schedule. */
    public static void fetchClubGroups(long clubId, GroupsCb cb) {
        fetchAllGroups(clubId, cb);
    }

    private static void fetchAllGroups(long clubId, GroupsCb cb) {
        try {
            col(clubId, "groups").get()
                    .addOnSuccessListener(qs -> {
                        List<Group> out = new ArrayList<>();
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            List<String> emails = strList(d, "memberEmails");
                            List<String> names = strList(d, "memberNames");
                            Group g = new Group(ContentScope.stableId(d.getId()), str(d, "name"),
                                    (int) lng(d, "dayIndex", 0), str(d, "time"), 0,
                                    str(d, "tutorName"), emails.size());
                            g.cloudId = d.getId();
                            g.tutorEmail = str(d, "tutorEmail");
                            g.memberEmails = emails;
                            g.memberNames = names;
                            out.add(g);
                        }
                        out.sort(Comparator.comparingInt((Group g) -> g.dayIndex)
                                .thenComparing(g -> g.time == null ? "" : g.time));
                        cb.ok(out);
                    })
                    .addOnFailureListener(e -> cb.fail());
        } catch (Throwable t) {
            cb.fail();
        }
    }

    // ============================================================ ASSIGNMENTS

    public static void addAssignment(long clubId, String groupCloudId, String groupName,
                                     String title, String description, String fileUrl,
                                     String dueDate, String createdByName, Runnable onComplete) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("groupCloudId", groupCloudId != null ? groupCloudId : "");
            m.put("groupName", groupName != null ? groupName : "");
            m.put("title", InputValidator.sanitizeLine(title, 120));
            m.put("description", InputValidator.sanitizeText(description));
            m.put("fileUrl", fileUrl != null ? fileUrl : "");
            m.put("dueDate", dueDate);
            m.put("createdByName", createdByName != null ? createdByName : "");
            m.put("createdAtMs", System.currentTimeMillis());
            commit(col(clubId, "assignments").add(m), onComplete);
        } catch (Throwable t) {
            run(onComplete);
        }
    }

    /** Fetches the club's groups and assignments, returning the ones visible to {@code viewer}. */
    public static void fetchAssignments(User viewer, List<String> parentChildEmails, AssignmentsCb cb) {
        fetchAllGroups(viewer.clubId, new GroupsCb() {
            @Override public void ok(List<Group> allGroups) {
                List<Group> visible = ContentScope.visibleGroups(viewer.role.name(),
                        viewer.email, parentChildEmails, allGroups);
                try {
                    col(viewer.clubId, "assignments").get()
                            .addOnSuccessListener(qs -> {
                                List<Assignment> all = new ArrayList<>();
                                for (DocumentSnapshot d : qs.getDocuments()) {
                                    Assignment a = new Assignment(ContentScope.stableId(d.getId()),
                                            0, str(d, "groupName"), str(d, "title"),
                                            str(d, "description"), emptyToNull(str(d, "fileUrl")),
                                            str(d, "dueDate"), str(d, "createdByName"), false);
                                    a.cloudId = d.getId();
                                    a.groupCloudId = str(d, "groupCloudId");
                                    all.add(a);
                                }
                                List<Assignment> vis = ContentScope.visibleAssignments(
                                        viewer.role.name(), visible, all);
                                sortAssignmentsByMsDesc(qs.getDocuments(), vis);
                                cb.ok(vis);
                            })
                            .addOnFailureListener(e -> cb.fail());
                } catch (Throwable t) {
                    cb.fail();
                }
            }
            @Override public void fail() { cb.fail(); }
        });
    }

    /**
     * Convenience cloud loader used by the home screen and the assignments screen:
     * resolves parent scoping, fetches visible assignments, and (for a student) fills
     * the per-device completion flag before delivering on the main thread.
     */
    public static void loadAssignments(Context ctx, User user, AssignmentsCb cb) {
        resolveParentKids(ctx, user, kids -> fetchAssignments(user, kids, new AssignmentsCb() {
            @Override public void ok(List<Assignment> items) {
                if (user.role != Role.CHILD) {
                    cb.ok(items);
                    return;
                }
                ClubRepository repo = ClubRepository.getInstance(ctx);
                Async.io(() -> {
                    for (Assignment a : items) {
                        if (a.cloudId != null) {
                            a.completed = repo.isAssignmentDone(
                                    ContentScope.stableId(a.cloudId), user.id);
                        }
                    }
                    Async.main(() -> cb.ok(items));
                });
            }
            @Override public void fail() { cb.fail(); }
        }));
    }

    // ============================================================ RESULTS

    public static void addResult(long clubId, String tournamentName, String date,
                                 String childEmail, String childName,
                                 double points, int games, int standing) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("tournamentName", InputValidator.sanitizeLine(tournamentName, 120));
            m.put("date", InputValidator.sanitizeLine(date, 20));
            m.put("childEmail", childEmail != null ? childEmail.trim().toLowerCase() : "");
            m.put("childName", childName != null ? childName : "");
            m.put("points", points);
            m.put("games", games);
            m.put("standing", standing);
            col(clubId, "results").add(m);
        } catch (Throwable ignored) { }
    }

    /** One parsed CSV row destined for the cloud results collection. */
    public static final class ResultRow {
        public final String tournamentName, date, childEmail, childName;
        public final double points;
        public final int games, standing;
        public ResultRow(String tournamentName, String date, String childEmail, String childName,
                         double points, int games, int standing) {
            this.tournamentName = tournamentName;
            this.date = date;
            this.childEmail = childEmail;
            this.childName = childName;
            this.points = points;
            this.games = games;
            this.standing = standing;
        }
    }

    /** Writes all imported results in one batch, reporting how many committed. */
    public static void importResults(long clubId, List<ResultRow> rows, CountCb cb) {
        if (rows == null || rows.isEmpty()) {
            cb.done(0);
            return;
        }
        try {
            com.google.firebase.firestore.WriteBatch batch = FirebaseFirestore.getInstance().batch();
            CollectionReference c = col(clubId, "results");
            for (ResultRow r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("tournamentName", InputValidator.sanitizeLine(r.tournamentName, 120));
                m.put("date", InputValidator.sanitizeLine(r.date, 20));
                m.put("childEmail", r.childEmail != null ? r.childEmail.trim().toLowerCase() : "");
                m.put("childName", r.childName != null ? r.childName : "");
                m.put("points", r.points);
                m.put("games", r.games);
                m.put("standing", r.standing);
                batch.set(c.document(), m);
            }
            int n = rows.size();
            batch.commit().addOnCompleteListener(t -> cb.done(t.isSuccessful() ? n : 0));
        } catch (Throwable t) {
            cb.done(0);
        }
    }

    public static void fetchResults(User viewer, List<String> parentChildEmails, ResultsCb cb) {
        // Tutor scoping needs the tutor's groups; fetch them first (cheap, cached offline).
        fetchAllGroups(viewer.clubId, new GroupsCb() {
            @Override public void ok(List<Group> allGroups) {
                List<Group> tutorGroups = ContentScope.visibleGroups("TUTOR", viewer.email,
                        null, allGroups);
                try {
                    col(viewer.clubId, "results").get()
                            .addOnSuccessListener(qs -> {
                                List<TournamentResult> all = new ArrayList<>();
                                for (DocumentSnapshot d : qs.getDocuments()) {
                                    TournamentResult r = new TournamentResult(
                                            ContentScope.stableId(d.getId()),
                                            str(d, "tournamentName"), str(d, "date"), 0,
                                            str(d, "childName"), dbl(d, "points"),
                                            (int) lng(d, "games", 0), (int) lng(d, "standing", 0));
                                    r.childEmail = str(d, "childEmail");
                                    all.add(r);
                                }
                                List<TournamentResult> vis = ContentScope.visibleResults(
                                        viewer.role.name(), viewer.email, parentChildEmails,
                                        tutorGroups, all);
                                vis.sort(Comparator.comparing((TournamentResult r) ->
                                        r.date == null ? "" : r.date).reversed());
                                cb.ok(vis);
                            })
                            .addOnFailureListener(e -> cb.fail());
                } catch (Throwable t) {
                    cb.fail();
                }
            }
            @Override public void fail() { cb.fail(); }
        });
    }

    // ============================================================ DIRECTORY

    /** Club members of a given role, read from the cloud user directory (admin-only by rules). */
    public static void clubUsersByRole(long clubId, Role role, UsersCb cb) {
        try {
            FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("clubId", clubId).get()
                    .addOnSuccessListener(qs -> {
                        List<User> out = new ArrayList<>();
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            Role r = Role.fromName(str(d, "role"));
                            if (r != role) continue;
                            User u = new User();
                            u.fullName = str(d, "fullName");
                            u.email = str(d, "email");
                            u.phone = str(d, "phone");
                            u.role = r;
                            u.status = str(d, "status");
                            u.clubId = lng(d, "clubId", 0);
                            u.rating = (int) lng(d, "rating", 0);
                            out.add(u);
                        }
                        out.sort(Comparator.comparing((User u) -> u.fullName == null ? "" : u.fullName));
                        cb.ok(out);
                    })
                    .addOnFailureListener(e -> cb.fail());
        } catch (Throwable t) {
            cb.fail();
        }
    }

    // ============================================================ helpers

    private static String str(DocumentSnapshot d, String key) {
        Object o = d.get(key);
        return o == null ? "" : o.toString();
    }

    private static long lng(DocumentSnapshot d, String key, long def) {
        Long v = d.getLong(key);
        return v != null ? v : def;
    }

    private static double dbl(DocumentSnapshot d, String key) {
        Double v = d.getDouble(key);
        return v != null ? v : 0d;
    }

    private static List<String> strList(DocumentSnapshot d, String key) {
        List<String> out = new ArrayList<>();
        Object o = d.get(key);
        if (o instanceof List) {
            for (Object e : (List<?>) o) if (e != null) out.add(e.toString());
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** Reorders {@code items} to match the createdAtMs-descending order of {@code docs}. */
    private static void sortByMsDesc(List<DocumentSnapshot> docs, List<NewsItem> items) {
        List<Integer> idx = orderIndices(docs);
        List<NewsItem> copy = new ArrayList<>(items);
        items.clear();
        for (int i : idx) items.add(copy.get(i));
    }

    private static void sortAssignmentsByMsDesc(List<DocumentSnapshot> docs, List<Assignment> vis) {
        // vis is a filtered subset; sort it directly by each doc's createdAtMs via a lookup.
        Map<String, Long> ms = new HashMap<>();
        for (DocumentSnapshot d : docs) ms.put(d.getId(), lng(d, "createdAtMs", 0));
        vis.sort(Comparator.comparingLong((Assignment a) ->
                a.cloudId != null && ms.containsKey(a.cloudId) ? ms.get(a.cloudId) : 0L).reversed());
    }

    /** Indices of {@code docs} sorted by createdAtMs descending. */
    private static List<Integer> orderIndices(List<DocumentSnapshot> docs) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) idx.add(i);
        idx.sort((a, b) -> Long.compare(lng(docs.get(b), "createdAtMs", 0),
                lng(docs.get(a), "createdAtMs", 0)));
        return idx;
    }
}
