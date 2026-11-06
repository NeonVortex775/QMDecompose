package com.qingming.decompose.util;

import java.util.ArrayList;
import java.util.List;

/** Colour codes. DragonCore only reads the section sign, so & is converted. */
public final class Text {

    private Text() {
    }

    public static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    public static List<String> color(List<String> in) {
        List<String> out = new ArrayList<String>();
        if (in == null) {
            return out;
        }
        for (String s : in) {
            out.add(color(s));
        }
        return out;
    }

    public static String plain(String s) {
        if (s == null) {
            return "";
        }
        String c = color(s);
        StringBuilder sb = new StringBuilder(c.length());
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch == '§' || ch == '\u00a7') {
                i++;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    public static List<String> plain(List<String> in) {
        List<String> out = new ArrayList<String>();
        if (in == null) {
            return out;
        }
        for (String s : in) {
            out.add(plain(s));
        }
        return out;
    }

    public static boolean wildcard(String pattern, String text) {
        if (pattern == null) {
            return false;
        }
        if (text == null) {
            text = "";
        }
        if (!pattern.contains("*")) {
            return pattern.equals(text);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (sb.length() > 0) {
                sb.append(".*");
            }
            sb.append(java.util.regex.Pattern.quote(part));
        }
        return text.matches(sb.toString());
    }

    public static String replace(String s, String from, String to) {
        return s == null ? "" : s.replace(from, to);
    }
}
