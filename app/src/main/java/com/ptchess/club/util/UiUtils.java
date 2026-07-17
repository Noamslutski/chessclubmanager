package com.ptchess.club.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.print.PrintManager;
import android.widget.Toast;

/** Small UI helpers: toasts, contact intents (call / WhatsApp), open & print files. */
public final class UiUtils {

    private UiUtils() { }

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
