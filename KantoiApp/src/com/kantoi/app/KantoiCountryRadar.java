package com.kantoi.app;

/**
 * Enjin Radar Kod Negara Berisiko Tinggi (Inspirasi PhoneInfoga)
 * Mengesan panggilan antarabangsa dari hab dan zon sindiket penipuan telekomunikasi siber
 * (Kemboja, Myanmar, Laos, Nigeria, Pakistan, India, Bangladesh, Indonesia, Filipina).
 */
public class KantoiCountryRadar {

    public static class CountryRisk {
        public final boolean isHighRisk;
        public final String countryCode;
        public final String countryName;
        public final String riskCategory;
        public final String flagEmoji;
        public final String advice;

        public CountryRisk(boolean isHighRisk, String countryCode, String countryName, String riskCategory, String flagEmoji, String advice) {
            this.isHighRisk = isHighRisk;
            this.countryCode = countryCode;
            this.countryName = countryName;
            this.riskCategory = riskCategory;
            this.flagEmoji = flagEmoji;
            this.advice = advice;
        }
    }

    // Pangkalan data zon berisiko tinggi antarabangsa (Sindiket Scam & Jenayah Siber)
    private static final CountryRisk[] HIGH_RISK_COUNTRIES = new CountryRisk[] {
        new CountryRisk(true, "+855", "Kemboja (Cambodia)", "Hab Sindiket Pemerdagangan & Job Scam Siber", "\uD83C\uDDF0\uD83C\uDDED", "Zon aktif sindiket penipuan tawaran kerja palsu & pelaburan kripto."),
        new CountryRisk(true, "+95", "Myanmar (Burma)", "Kompleks Scam Siber KK Park & Shwe Kokko", "\uD83C\uDDF2\uD83C\uDDF2", "Pusat sindiket penipuan telekomunikasi antarabangsa berpagar & kerja paksa."),
        new CountryRisk(true, "+856", "Laos", "Zon Khas Golden Triangle Scam & Judi Siber", "\uD83C\uDDF1\uD83C\uDDE6", "Sindiket panggilan pelaburan tipu dan penipuan telekomunikasi."),
        new CountryRisk(true, "+234", "Nigeria", "Sindiket Love Scam & Bungkusan Parcel Kastam", "\uD83C\uDDF3\uD83C\uDDEC", "Penipuan bungkusan hadiah tersangkut di kastam dan penipuan cinta maya."),
        new CountryRisk(true, "+92", "Pakistan", "Sindiket Panggilan Cabutan Bertuah & Samaran Pegawai", "\uD83C\uDDF5\uD83C\uDDF0", "Panggilan tipu cabutan bertuah dan penyamaran pegawai."),
        new CountryRisk(true, "+91", "India", "Sindiket Tech Support Palsu & Penyamaran IT", "\uD83C\uDDEE\uD83C\uDDF3", "Penipuan sokongan komputer palsu dan pemerasan pancingan data."),
        new CountryRisk(true, "+880", "Bangladesh", "Sindiket Pendail Pukal & Robocall Spam", "\uD83C\uDDE7\uD83C\uDDE9", "Pendail automatik (robocall) dan spam kewangan."),
        new CountryRisk(true, "+62", "Indonesia", "Sindiket APK Undangan Kahwin & Kurier Palsu", "\uD83C\uDDEE\uD83C\uDDE9", "Penyebaran fail APK pancingan SMS melalui WhatsApp/SMS bertopengkan kurier atau jemputan."),
        new CountryRisk(true, "+63", "Filipina", "Zon Sindiket Penipuan Luar Pesisir POGO", "\uD83C\uDDF5\uD83C\uDDED", "Pusat operasi penipuan telekomunikasi dan kad kredit.")
    };

    /**
     * Memeriksa sama ada nombor pemanggil berasal dari negara antarabangsa berisiko tinggi.
     * Mengelakkan sebarang salah padanan (false positives) terhadap nombor domestik Malaysia (01x, 03, +60).
     */
    public static CountryRisk checkCountryRisk(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) {
            return null;
        }

        String trimmed = rawNumber.trim();

        // 1. Nombor domestik Malaysia bermula dengan '0' tunggal (cth: 012, 03, 088)
        if (trimmed.startsWith("0") && !trimmed.startsWith("00")) {
            return null;
        }

        // 2. Bersihkan aksara selain nombor dan '+'
        String clean = trimmed.replaceAll("[^0-9+]", "");
        if (clean.startsWith("00")) {
            clean = "+" + clean.substring(2);
        } else if (!clean.startsWith("+")) {
            clean = "+" + clean;
        }

        // 3. Pastikan nombor Malaysia (+60) dikecualikan
        if (clean.startsWith("+60")) {
            return null;
        }

        // 4. Padankan dengan zon kod negara berisiko tinggi
        for (CountryRisk country : HIGH_RISK_COUNTRIES) {
            if (clean.startsWith(country.countryCode)) {
                return country;
            }
        }

        return null;
    }

    /**
     * Memeriksa sama ada nombor adalah panggilan antarabangsa secara umum.
     */
    public static boolean isInternational(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return false;
        String trimmed = rawNumber.trim();
        if (trimmed.startsWith("0") && !trimmed.startsWith("00")) return false;

        String clean = trimmed.replaceAll("[^0-9+]", "");
        if (clean.startsWith("00")) clean = "+" + clean.substring(2);
        else if (!clean.startsWith("+")) clean = "+" + clean;

        return !clean.startsWith("+60") && clean.length() > 4;
    }
}
