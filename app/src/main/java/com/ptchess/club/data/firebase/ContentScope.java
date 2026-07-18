package com.ptchess.club.data.firebase;

import com.ptchess.club.data.model.Assignment;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.TournamentResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure role/membership scoping for cloud content. Firestore docs are denormalised
 * and keyed by <em>email</em> (a stable cross-device identifier), so who may see a
 * group / assignment / result is decided here with no database or network access.
 * Kept free of Android and Firebase types so it can be unit-tested standalone.
 *
 * <p>Roles are passed as their {@code Role.name()} string ("ADMIN"/"TUTOR"/
 * "CHILD"/"PARENT") to avoid a dependency on the {@code Role} enum (which pulls in
 * generated Android resources).
 */
public final class ContentScope {

    private ContentScope() { }

    /**
     * A stable {@code long} derived from a Firestore document id, used as the local
     * key for per-device completion tracking. Offset above the 32-bit range so it
     * never collides with small SQLite auto-increment ids.
     */
    public static long stableId(String cloudId) {
        if (cloudId == null) return 0L;
        return 0x1_0000_0000L | (cloudId.hashCode() & 0xffffffffL);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normSet(Iterable<String> in) {
        Set<String> out = new HashSet<>();
        if (in != null) for (String s : in) {
            String n = norm(s);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    /** Groups the viewer may see. {@code parentChildEmails} is used only for PARENT. */
    public static List<Group> visibleGroups(String role, String email,
                                            Iterable<String> parentChildEmails,
                                            List<Group> all) {
        List<Group> out = new ArrayList<>();
        if (all == null) return out;
        String r = norm(role);
        String me = norm(email);
        Set<String> kids = normSet(parentChildEmails);
        for (Group g : all) {
            if ("admin".equals(r)) {
                out.add(g);
            } else if ("tutor".equals(r)) {
                if (norm(g.tutorEmail).equals(me)) out.add(g);
            } else if ("child".equals(r)) {
                if (normSet(g.memberEmails).contains(me)) out.add(g);
            } else if ("parent".equals(r)) {
                if (!intersect(normSet(g.memberEmails), kids).isEmpty()) out.add(g);
            }
        }
        return out;
    }

    /** Assignments whose owning group is visible to the viewer. */
    public static List<Assignment> visibleAssignments(String role, List<Group> visibleGroups,
                                                      List<Assignment> all) {
        List<Assignment> out = new ArrayList<>();
        if (all == null) return out;
        if ("admin".equals(norm(role))) {
            out.addAll(all); // admin sees all club assignments
            return out;
        }
        Set<String> allowed = new HashSet<>();
        if (visibleGroups != null) for (Group g : visibleGroups) {
            if (g.cloudId != null) allowed.add(g.cloudId);
        }
        for (Assignment a : all) {
            if (a.groupCloudId != null && allowed.contains(a.groupCloudId)) out.add(a);
        }
        return out;
    }

    /**
     * Tournament results the viewer may see.
     * <ul>
     *   <li>ADMIN — all club results</li>
     *   <li>CHILD — their own (by email)</li>
     *   <li>PARENT — their children's (by email)</li>
     *   <li>TUTOR — the students in the tutor's groups (union of member emails)</li>
     * </ul>
     */
    public static List<TournamentResult> visibleResults(String role, String email,
                                                        Iterable<String> parentChildEmails,
                                                        List<Group> tutorGroups,
                                                        List<TournamentResult> all) {
        List<TournamentResult> out = new ArrayList<>();
        if (all == null) return out;
        String r = norm(role);
        String me = norm(email);
        if ("admin".equals(r)) {
            out.addAll(all);
            return out;
        }
        Set<String> allowed;
        if ("child".equals(r)) {
            allowed = new HashSet<>();
            allowed.add(me);
        } else if ("parent".equals(r)) {
            allowed = normSet(parentChildEmails);
        } else if ("tutor".equals(r)) {
            allowed = new HashSet<>();
            if (tutorGroups != null) for (Group g : tutorGroups) {
                allowed.addAll(normSet(g.memberEmails));
            }
        } else {
            return out;
        }
        for (TournamentResult res : all) {
            if (allowed.contains(norm(res.childEmail))) out.add(res);
        }
        return out;
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }
}
