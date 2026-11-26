package com.agentbanking.platform.exchange;

import java.security.SecureRandom;
import java.util.Locale;

final class ExchangePrivacy {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private ExchangePrivacy() {
    }

    static String anonymityCode() {
        return "NX-" + random(4);
    }

    static String verifyToken() {
        return random(6);
    }

    private static String random(int len) {
        char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = CODE[RANDOM.nextInt(CODE.length)];
        }
        return new String(out).toUpperCase(Locale.ROOT);
    }
}
