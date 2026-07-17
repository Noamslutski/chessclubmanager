package com.ptchess.club.security;

import android.util.Base64;

import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing. Passwords are never stored in plaintext.
 *
 * <p>Stored format: {@code iterations:base64(salt):base64(hash)}. Verification
 * uses a constant-time comparison to avoid timing side-channels.</p>
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGO = "PBKDF2WithHmacSHA256";

    private PasswordHasher() { }

    public static String hash(char[] password) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] hash = pbkdf2(password, salt, ITERATIONS);
            return ITERATIONS + ":" + b64(salt) + ":" + b64(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static String hash(String password) {
        return hash(password.toCharArray());
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = unb64(parts[1]);
            byte[] expected = unb64(parts[2]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGO);
        try {
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    private static byte[] unb64(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }
}
