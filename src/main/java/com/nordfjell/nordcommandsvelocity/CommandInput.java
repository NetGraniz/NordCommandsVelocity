package com.nordfjell.nordcommandsvelocity;

import java.util.Locale;

/** Inspect only; never rebuild the original argument string or forwarding result. */
final class CommandInput {
    static final int MAX_LENGTH = 32767;
    static final int MAX_LABEL_LENGTH = 256;
    private CommandInput() {}

    // Velocity passes commands without the first slash and trims ASCII spaces.
    static String label(String input) {
        if (input == null || input.isEmpty() || input.length() > MAX_LENGTH) return null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') return null;
        }
        int start = 0;
        while (start < input.length() && input.charAt(start) == ' ') start++;
        if (start == input.length() || input.charAt(start) == '/') return null;
        int end = input.indexOf(' ', start);
        if (end < 0) end = input.length();
        if (end - start > MAX_LABEL_LENGTH) return null;
        return input.substring(start, end).toLowerCase(Locale.ROOT);
    }
}
