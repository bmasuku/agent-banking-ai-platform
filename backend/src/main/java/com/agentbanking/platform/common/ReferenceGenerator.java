package com.agentbanking.platform.common;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ReferenceGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferenceGenerator() {
    }

    /** e.g. TXN-20261125-K7Q2M9XA */
    public static String next(String prefix) {
        String date = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.ofInstant(AppClock.now(), AppClock.zone()));
        StringBuilder sb = new StringBuilder(prefix).append('-').append(date).append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
