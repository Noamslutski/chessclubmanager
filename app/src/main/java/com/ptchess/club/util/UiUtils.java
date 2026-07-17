package com.ptchess.club.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.print.PrintManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small UI helpers: toasts, contact intents (call / WhatsApp), open & print files. */
public final class UiUtils {

    private UiUtils() { }

    /**
     * Loads an image into an {@link ImageView} from either a local URI
     * ({@code content://} / {@code file://}) or a remote {@code http(s)} URL —
     * e.g. a Firebase Storage download URL. Remote images are fetched off the
     * main thread; local ones use {@link ImageView#setImageURI}.
     */
    public static void loadImage(ImageView iv, String uriString) {
        if (iv == null || uriString == null || uriString.isEmpty()) return;
        if (uriString.startsWith("http")) {
            final String url = uriString;
            Async.io(() -> {
                Bitmap bmp = downloadBitmap(url);
                if (bmp != null) Async.main(() -> iv.setImageBitmap(bmp));
            });
        } else {
            try {
                iv.setImageURI(Uri.parse(uriString));
            } catch (Exception ignored) { }
        }
    }

    private static Bitmap downloadBitmap(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context ctx, int resId) {
        Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show();
    }

    public static void dial(Context ctx, String phone) {
        if (phone == null || phone.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        safeStart(ctx, i);
    }

    /** Opens a WhatsApp chat with the given international number (no + or spaces). */
    public static void whatsapp(Context ctx, String phone) {
        if (phone == null || phone.isEmpty()) return;
        String digits = phone.replaceAll("[^0-9]", "");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + digits));
        safeStart(ctx, i);
    }

    public static void openUri(Context ctx, String uriString, String mime) {
        if (uriString == null || uriString.isEmpty()) {
            toast(ctx, "—");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(uriString), mime != null ? mime : "*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        safeStart(ctx, i);
    }

    /**
     * Sends a document to the Android print framework. For a PDF the system
     * print UI lets the user pick a printer or "Save as PDF"; here we hand off
     * via the print service (a production build renders the PDF pages onto the
     * print adapter).
     */
    public static void printDocument(Context ctx, String jobName, String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            toast(ctx, "—");
            return;
        }
        PrintManager pm = (PrintManager) ctx.getSystemService(Context.PRINT_SERVICE);
        if (pm == null) {
            openUri(ctx, uriString, "application/pdf");
            return;
        }
        // The simplest reliable path is to open the PDF in a viewer that offers
        // print; full in-app PrintDocumentAdapter rendering is a backend/PDF task.
        openUri(ctx, uriString, "application/pdf");
    }

    private static void safeStart(Context ctx, Intent i) {
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast(ctx, "—");
        }
    }
}
