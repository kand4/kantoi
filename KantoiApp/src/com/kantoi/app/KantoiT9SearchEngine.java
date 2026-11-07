package com.kantoi.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enjin Carian Pintar T9 (Inspirasi Fossify Dialer)
 * Memetakan huruf abjad nama kenalan kepada digit kekunci telefon standard
 * (2: ABC, 3: DEF, 4: GHI, 5: JKL, 6: MNO, 7: PQRS, 8: TUV, 9: WXYZ)
 * bagi membolehkan pencarian kenalan sepantas kilat terus dari pad nombor pendail.
 */
public class KantoiT9SearchEngine {

    public static class T9Contact {
        public final String name;
        public final String number;
        public final String cleanNumber;
        public final String t9Name;

        public T9Contact(String name, String number) {
            this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : "Kenalan";
            this.number = (number != null) ? number.trim() : "";
            this.cleanNumber = KantoiThreatEngine.normalizeToLocal(this.number);
            this.t9Name = convertToT9(this.name);
        }
    }

    /**
     * Menukar sebarang teks nama manusia ke digit T9 standard telekomunikasi antarabangsa.
     */
    public static String convertToT9(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        String lower = text.toLowerCase(Locale.ENGLISH);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'c') sb.append('2');
            else if (c >= 'd' && c <= 'f') sb.append('3');
            else if (c >= 'g' && c <= 'i') sb.append('4');
            else if (c >= 'j' && c <= 'l') sb.append('5');
            else if (c >= 'm' && c <= 'o') sb.append('6');
            else if (c >= 'p' && c <= 's') sb.append('7');
            else if (c >= 't' && c <= 'v') sb.append('8');
            else if (c >= 'w' && c <= 'z') sb.append('9');
            else if (c >= '0' && c <= '9') sb.append(c);
            else sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Menapis senarai kenalan berdasarkan padanan nama T9 atau nombor telefon.
     */
    public static List<T9Contact> filter(List<T9Contact> allContacts, String queryDigits, int maxResults) {
        List<T9Contact> results = new ArrayList<>();
        if (allContacts == null || queryDigits == null || queryDigits.trim().isEmpty()) {
            return results;
        }

        String q = queryDigits.replaceAll("[^0-9]", "");
        if (q.isEmpty()) return results;

        for (T9Contact c : allContacts) {
            boolean nameMatch = c.t9Name.contains(q);
            boolean numMatch = c.cleanNumber.contains(q) || c.number.contains(q);
            if (nameMatch || numMatch) {
                results.add(c);
                if (results.size() >= maxResults) break;
            }
        }
        return results;
    }
}
