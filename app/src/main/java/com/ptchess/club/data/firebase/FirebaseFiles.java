package com.ptchess.club.data.firebase;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * Uploads a locally-picked file ({@code content://} URI) to Firebase Storage and
 * hands back a public download URL. Everything here is optional: when Firebase
 * isn't configured the callers keep the local URI and the app works offline.
 *
 * <p>Files are organised by club to match {@code storage.rules}:
 * {@code logos/{clubId}/…}, {@code library/{clubId}/…}, {@code assignments/{clubId}/…}.
 */
public final class FirebaseFiles {

    /** Storage folders (top-level path segments), aligned with storage.rules. */
    public static final String LOGOS = "logos";
    public static final String LIBRARY = "library";
    public static final String ASSIGNMENTS = "assignments";

    public interface UploadCb {
        void onSuccess(String downloadUrl);
        void onError(String message);
    }

    private FirebaseFiles() { }

    public static boolean enabled(Context context) {
        return FirebaseServices.isAvailable(context);
    }

    /**
     * Uploads {@code localUri} under {@code folder/clubId/} and returns the
     * resulting download URL on the main thread via {@code cb}. Callbacks from
     * the Storage SDK already arrive on the main thread.
     */
    public static void upload(Context context, String folder, long clubId, Uri localUri, UploadCb cb) {
        if (localUri == null) {
            cb.onError("no file");
            return;
        }
        try {
            String name = System.currentTimeMillis() + "_" + sanitize(displayName(context, localUri));
            StorageReference ref = FirebaseStorage.getInstance().getReference()
                    .child(folder + "/" + clubId + "/" + name);
            ref.putFile(localUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            Exception e = task.getException();
                            throw e != null ? e : new RuntimeException("upload failed");
                        }
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(uri -> cb.onSuccess(uri.toString()))
                    .addOnFailureListener(e -> cb.onError(e.getMessage()));
        } catch (Throwable t) {
            cb.onError(t.getMessage());
        }
    }

    private static String displayName(Context context, Uri uri) {
        String fallback = "file";
        if (uri == null) return fallback;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String n = c.getString(idx);
                        if (n != null && !n.isEmpty()) return n;
                    }
                }
            } catch (Exception ignored) { }
        }
        String last = uri.getLastPathSegment();
        return last != null && !last.isEmpty() ? last : fallback;
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.length() > 60 ? s.substring(s.length() - 60) : s;
    }
}
