package com.kantoi.app;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Enjin Keselamatan Kod QR & Anti-Quishing KANTOI (Versi 1.17.0):
 * Mengesan ancaman kod QR secara masa-nyata dalam peranti (on-device):
 * 1. Pengecaman Kod QR DuitNow PayNet vs Pautan Web
 * 2. Pengesanan Jebakan Muat Turun Fail .apk Trojan Perbankan
 * 3. Pengesanan Domain Berisiko Tinggi (.top, .xyz, .vip, .buzz, dsb)
 * 4. Pengesanan Penyamaran Bantuan Kerajaan (.gov.my)
 * 5. Pengesanan Pancingan Data Touch 'n Go Money Packet & Perbankan
 */
public class KantoiQrSafetyEngine {

    public enum RiskLevel {
        SAFE,
        SUSPICIOUS,
        CRITICAL
    }

    public static class QrVerdict {
        public final RiskLevel riskLevel;
        public final String title;
        public final String domain;
        public final String description;
        public final String safetyNote;
        public final boolean isDuitNow;
        public final boolean isApkDownload;

        public QrVerdict(RiskLevel riskLevel, String title, String domain, String description, String safetyNote, boolean isDuitNow, boolean isApkDownload) {
            this.riskLevel = riskLevel;
            this.title = title;
            this.domain = domain;
            this.description = description;
            this.safetyNote = safetyNote;
            this.isDuitNow = isDuitNow;
            this.isApkDownload = isApkDownload;
        }
    }

    private static final Set<String> SUSPICIOUS_TLDS = new HashSet<>(Arrays.asList(
        ".top", ".xyz", ".vip", ".icu", ".tk", ".ml", ".ga", ".cf", ".gq",
        ".buzz", ".work", ".click", ".rest", ".fit", ".online", ".site",
        ".cc", ".space", ".pw", ".club", ".link", ".live", ".monster", ".cfd"
    ));

    private static final Set<String> GOV_KEYWORDS = new HashSet<>(Arrays.asList(
        "str", "bantuan", "sumbangan", "tunai", "ebpn", "mykasih", "ekasih",
        "madani", "kwsp", "saman", "jpj", "mysejahtera", "padu", "pakej-5g"
    ));

    private static final Set<String> TNG_PHISHING_KEYWORDS = new HashSet<>(Arrays.asList(
        "tng", "touchngo", "touch-n-go", "tngdigital", "tngewallet", "tngwallet",
        "moneypacket", "moneypocket", "angpow-tng", "angpau-tng", "duitraya-tng",
        "tng-claim", "tng-subsidi", "tng-bantuan", "tng-bonus", "tng-login"
    ));

    private static final Set<String> BANK_KEYWORDS = new HashSet<>(Arrays.asList(
        "maybank", "cimb", "publicbank", "rhb", "bankislam", "hongleong", "ambank", "bsn", "affin"
    ));

    /**
     * Menganalisis sebarang data hasil imbasan kod QR
     */
    public static QrVerdict evaluate(String rawData) {
        if (rawData == null || rawData.trim().isEmpty()) {
            return new QrVerdict(RiskLevel.SAFE, "Data Kosong", "", "Tiada maklumat kod QR dikesan.", "", false, false);
        }

        String trimmed = rawData.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // 1. Pengecaman Kod QR DuitNow (EMVCo PayNet) & Pembongkar TLV
        if (trimmed.startsWith("000201") || (trimmed.startsWith("0002") && (lower.contains("duitnow") || lower.contains("paynet")))) {
            Map<String, String> tlv = parseEmvcoTlv(trimmed);
            String merchantName = tlv.containsKey("59") ? tlv.get("59").trim() : "";
            String merchantCity = tlv.containsKey("60") ? tlv.get("60").trim() : "";
            String amount = tlv.containsKey("54") ? tlv.get("54").trim() : "";
            
            String accOrProxy = "";
            String bankOrAcquirer = "PayNet DuitNow";
            
            // Semak Tag 26 hingga 51 untuk Maklumat Akaun Pedagang / Penerima
            for (int t = 26; t <= 51; t++) {
                String tagKey = String.format(Locale.ROOT, "%02d", t);
                if (tlv.containsKey(tagKey)) {
                    Map<String, String> subTlv = parseEmvcoTlv(tlv.get(tagKey));
                    if (subTlv.containsKey("02")) {
                        accOrProxy = subTlv.get("02").trim();
                    }
                    if (subTlv.containsKey("01")) {
                        String sub01 = subTlv.get("01").trim();
                        if ("01".equals(sub01)) {
                            bankOrAcquirer = "Nombor Telefon Proksi";
                        } else if (!sub01.isEmpty()) {
                            bankOrAcquirer = sub01;
                        }
                    }
                    if (!accOrProxy.isEmpty()) break;
                }
            }

            StringBuilder desc = new StringBuilder();
            if (!merchantName.isEmpty()) {
                desc.append("👤 Pemilik Berdaftar: ").append(merchantName).append("\n");
            }
            if (!accOrProxy.isEmpty()) {
                desc.append("🏦 Akaun/Proksi: ").append(accOrProxy);
                if (!bankOrAcquirer.isEmpty()) {
                    desc.append(" (").append(bankOrAcquirer).append(")");
                }
                desc.append("\n");
            }
            if (!merchantCity.isEmpty()) {
                desc.append("📍 Bandar/Lokasi: ").append(merchantCity).append("\n");
            }
            if (!amount.isEmpty()) {
                desc.append("💰 Jumlah Bayaran: RM ").append(amount).append("\n");
            }
            if (desc.length() == 0) {
                desc.append("Kod QR pembayaran standard rasmi PayNet DuitNow.");
            }

            String title = merchantName.isEmpty() ? "✅ KOD QR DUITNOW PAYNET SAH" : "✅ DUITNOW: " + merchantName;
            String note = "💡 Pastikan nama pemilik ini sepadan dengan penerima transaksi sebelum memindahkan wang.";

            return new QrVerdict(
                RiskLevel.SAFE,
                title,
                "DuitNow EMVCo",
                desc.toString().trim(),
                note,
                true,
                false
            );
        }

        // 2. Semak jika Skim Pautan Aplikasi Terus Touch 'n Go (Deep Link tngd://)
        if (lower.startsWith("tngd://")) {
            return new QrVerdict(
                RiskLevel.SAFE,
                "✅ PAUTAN SAH APLIKASI TNG EWALLET",
                "tngd:// (Aplikasi Rasmi)",
                "Pautan membuka terus aplikasi rasmi Touch 'n Go eWallet tanpa melalui pelayar web.",
                "Wang Money Packet rasmi akan terus masuk ke baki eWallet anda.",
                false,
                false
            );
        }

        // 3. Semak jika ini adalah Pautan URL
        Uri uri = null;
        try {
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                uri = Uri.parse("https://" + trimmed);
            } else {
                uri = Uri.parse(trimmed);
            }
        } catch (Exception ignored) {}

        String host = (uri != null && uri.getHost() != null) ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        String path = (uri != null && uri.getPath() != null) ? uri.getPath().toLowerCase(Locale.ROOT) : "";

        // 4. Perangkap Muat Turun Fail .APK Terus (Malware / Banking Trojan)
        if (path.endsWith(".apk") || lower.contains(".apk?") || lower.endsWith(".apk")) {
            return new QrVerdict(
                RiskLevel.CRITICAL,
                "🚨 BAHAYA: PERANGKAP TROJAN APK DIKESAN!",
                host,
                "Pautan ini cuba memuat turun fail aplikasi Android (.apk) secara automatik ke peranti anda. Ini adalah taktik sindiket pencuri SMS OTP/TAC perbankan!",
                "🚫 AMARAN: Jangan sesekali pasang fail ini pada telefon pintar anda!",
                false,
                true
            );
        }

        // 5. Semak Pancingan Data Bantuan Kerajaan
        boolean hasGovKw = false;
        for (String kw : GOV_KEYWORDS) {
            if (lower.contains(kw)) {
                hasGovKw = true;
                break;
            }
        }
        if (hasGovKw && !host.endsWith(".gov.my") && !host.endsWith(".my")) {
            return new QrVerdict(
                RiskLevel.CRITICAL,
                "🚨 PHISHING: PENYAMARAN BANTUAN KERAJAAN!",
                host,
                "Laman ini mendakwa mengenai bantuan rasmi atau agensi kerajaan tetapi TIDAK menggunakan domain rasmi kerajaan (.gov.my).",
                "🚫 AMARAN: Sindiket cuba memancing nombor kad pengenalan, nombor telefon, dan kata laluan perbankan anda!",
                false,
                false
            );
        }

        // 6. Semak Penyamaran Touch 'n Go Money Packet Palsu
        boolean hasTngKw = false;
        for (String kw : TNG_PHISHING_KEYWORDS) {
            if (lower.contains(kw)) {
                hasTngKw = true;
                break;
            }
        }
        if (hasTngKw) {
            boolean isOfficialTng = host.equals("touchngo.com.my") || host.endsWith(".touchngo.com.my")
                                 || host.equals("tngdigital.com.my") || host.endsWith(".tngdigital.com.my");
            if (!isOfficialTng) {
                return new QrVerdict(
                    RiskLevel.CRITICAL,
                    "🚨 PERANGKAP: TNG MONEY PACKET PALSU!",
                    host,
                    "Pautan ini menyamar sebagai pautan 'Money Packet' atau 'Duit Raya' Touch 'n Go tetapi menggunakan tapak pihak ketiga yang tidak sah.",
                    "💡 PANDUAN: Money Packet TNG yang sebenar TIDAK PERNAH meminta nombor telefon, PIN 6-digit atau SMS OTP di pelayar web. Wang terus masuk ke aplikasi eWallet anda!",
                    false,
                    false
                );
            }
        }

        // 7. Semak Penyamaran Perbankan Internet (Maybank, CIMB, RHB, BSN dsb)
        for (String bank : BANK_KEYWORDS) {
            if (lower.contains(bank)) {
                boolean isGovOrEdu = host.endsWith(".gov.my") || host.endsWith(".edu.my");
                boolean isOfficialBank = host.endsWith(".com.my") || host.endsWith(".com");
                // Jika domain mencurigakan menggunakan nama bank
                for (String tld : SUSPICIOUS_TLDS) {
                    if (host.endsWith(tld)) {
                        return new QrVerdict(
                            RiskLevel.CRITICAL,
                            "🚨 PHISHING: PENYAMARAN LAMAN BANK!",
                            host,
                            "Laman ini mengandungi nama institusi perbankan (" + bank.toUpperCase(Locale.ROOT) + ") pada domain berisiko tinggi (" + tld + ").",
                            "🚫 JANGAN masukkan nama pengguna atau kata laluan perbankan anda!",
                            false,
                            false
                        );
                    }
                }
            }
        }

        // 8. Semak Domain Berisiko Tinggi (Suspicious TLDs)
        for (String tld : SUSPICIOUS_TLDS) {
            if (host.endsWith(tld)) {
                return new QrVerdict(
                    RiskLevel.SUSPICIOUS,
                    "⚠️ AMARAN: DOMAIN BERISIKO TINGGI (" + tld + ")",
                    host,
                    "Laman web ini menggunakan sambungan domain (" + tld + ") yang lazimnya digunakan untuk aktiviti penipuan dan pancingan data berkos rendah.",
                    "Berhati-hati dan elakkan daripada mengisi sebarang maklumat peribadi atau perbankan.",
                    false,
                    false
                );
            }
        }

        return new QrVerdict(
            RiskLevel.SAFE,
            "✅ PAUTAN BERSIH / TIADA ANCAMAN DIKESAN",
            host,
            "Pautan tidak menunjukkan sebarang ciri penyamaran jenama, malware APK, atau domain berisiko tinggi yang diketahui.",
            "Sentiasa pastikan alamat URL di palang alamat pelayar web anda adalah tepat.",
            false,
            false
        );
    }

    /**
     * Menghuraikan format rentetan Tag-Length-Value (TLV) EMVCo piawaian PayNet DuitNow
     */
    public static Map<String, String> parseEmvcoTlv(String raw) {
        Map<String, String> tlv = new HashMap<>();
        if (raw == null || raw.length() < 4) return tlv;
        int i = 0;
        int len = raw.length();
        while (i + 4 <= len) {
            String tag = raw.substring(i, i + 2);
            try {
                int length = Integer.parseInt(raw.substring(i + 2, i + 4));
                i += 4;
                if (i + length <= len) {
                    String value = raw.substring(i, i + length);
                    tlv.put(tag, value);
                    i += length;
                } else {
                    break;
                }
            } catch (Exception e) {
                break;
            }
        }
        return tlv;
    }
}
