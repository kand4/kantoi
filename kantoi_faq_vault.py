# -*- coding: utf-8 -*-
"""
KANTOI Anti-Scam Knowledge Vault: 30 Soalan Lazim & Panduan Hak Mangsa Scam
Format: Modul Bebas Kuota AI (0ms Latency, 0 Token Burn, 100% Sitasi Sah di Sisi Undang-Undang Malaysia).
Statut & Akta: Kanun Keseksaan (Seksyen 420, 424A/B/C), CPC (Seksyen 107/435), CMA 1998, AMLA 2001, Garis Panduan BNM & NSRC.
"""

import html

FAQ_CATEGORIES = {
    "cat_emergency": {
        "title": "🚨 1. Tindakan Cemas 24 Jam",
        "short_title": "🚨 Tindakan Cemas 24 Jam",
        "desc": "Langkah serta-merta menyelamatkan baki wang dalam tempoh 'Golden Hours'",
        "q_ids": ["q1", "q2", "q3", "q4", "q5", "q6"]
    },
    "cat_mule": {
        "title": "🏦 2. Akaun Keldai & Tuntutan Duit",
        "short_title": "🏦 Akaun Keldai & Saman",
        "desc": "Pindaan Kanun Keseksaan 2024, saman sivil & tuntutan gantirugi",
        "q_ids": ["q7", "q8", "q9", "q10", "q11", "q12"]
    },
    "cat_police": {
        "title": "👮 3. Balai Polis, Repot & IO",
        "short_title": "👮 Balai Polis & IO PDRM",
        "desc": "Prosedur laporan Seksyen 420 KK, dokumen bukti & semakan kes",
        "q_ids": ["q13", "q14", "q15", "q16", "q17", "q18"]
    },
    "cat_bank": {
        "title": "🏛️ 4. Liabiliti Bank, BNM & Mahkamah",
        "short_title": "🏛️ Hak Bank, BNM & OFS",
        "desc": "Aduan BNM TELELINK, Ombudsman Kewangan (OFS) & Perintah Mahkamah",
        "q_ids": ["q19", "q20", "q21", "q22", "q23", "q24"]
    },
    "cat_safety": {
        "title": "🛡️ 5. Ugutan & Penipuan Susulan",
        "short_title": "🛡️ Ugutan & Tipu Susulan",
        "desc": "Ancaman menyamar polis, pembersihan APK malware & sokongan emosi",
        "q_ids": ["q25", "q26", "q27", "q28", "q29", "q30"]
    }
}

SCAM_VICTIM_FAQS = {
    # ==========================================
    # TIANG 1: TINDAKAN CEMAS 24 JAM
    # ==========================================
    "q1": {
        "cat": "cat_emergency",
        "title": "Apakah perkara NO. 1 yang wajib saya buat sebaik sedar ditipu?",
        "answer": (
            "🚨 <b>TINDAKAN NO. 1 SEGERA (KURANG 24 JAM):</b>\n\n"
            "Sebaik sahaja menyedari anda menjadi mangsa, <b>JANGAN TUNGGU KE BALAI POLIS DAHULU</b>. Anda wajib:\n\n"
            "1. 📞 <b>Dail Segera Talian NSRC: 997</b> <i>(Waktu operasi: 8.00 pagi – 8.00 malam)</i>.\n"
            "2. 🛑 <b>Hubungi Talian Kecemasan Bank Anda (24 Jam)</b> untuk aktifkan <i>Kill Switch</i>.\n\n"
            "💡 <b>Kenapa 24 jam pertama dipanggil 'Golden Hours'?</b>\n"
            "Sindiket penipuan biasanya memindahkan wang mangsa melalui 3 hingga 5 lapisan akaun keldai sebelum dikeluarkan di mesin ATM. "
            "Panggilan pantas ke 997 membolehkan pegawai NSRC bersama pihak bank memintas dan membekukan aliran wang sebelum sempat dikeluarkan!"
        ),
        "links": [
            {"text": "📞 Talian NSRC (Dail 997)", "url": "https://nfcc.my/index.php/nsrc"},
            {"text": "🌐 Panduan NSRC NFCC", "url": "https://nfcc.my"}
        ]
    },
    "q2": {
        "cat": "cat_emergency",
        "title": "Apa maklumat yang perlu saya berikan bila hubungi NSRC 997?",
        "answer": (
            "📋 <b>5 MAKLUMAT WAJIB BILA HUBUNGI 997:</b>\n\n"
            "Pastikan anda telah menyediakan fakta ringkas berikut sebelum pegawai mengangkat panggilan:\n\n"
            "1. 👤 <b>Maklumat Anda:</b> Nama penuh, No Kad Pengenalan (MyKad) & No Telefon.\n"
            "2. 🏦 <b>Bank Pengirim (Anda):</b> Nama bank anda & No Akaun bank yang digunakan.\n"
            "3. 💳 <b>Bank Penerima (Scammer):</b> Nama bank scammer & No Akaun sasaran.\n"
            "4. ⏱️ <b>Masa & Jumlah:</b> Waktu transaksi dibuat dan jumlah wang yang dipindahkan (RM).\n"
            "5. 📱 <b>Saluran Komunikasi:</b> No telefon/akaun WhatsApp atau Telegram suspek.\n\n"
            "ℹ️ <i>Nota: Anda akan diberikan No Rujukan Kes NSRC. Simpan nombor rujukan ini untuk diserahkan semasa membuat repot polis fizikal nanti.</i>"
        ),
        "links": [
            {"text": "🌐 Portal Rasmi NSRC", "url": "https://nfcc.my/index.php/nsrc"}
        ]
    },
    "q3": {
        "cat": "cat_emergency",
        "title": "Bagaimana cara mengaktifkan fungsi 'Kill Switch' pada akaun bank saya?",
        "answer": (
            "🛑 <b>PENGAKTIFAN 'KILL SWITCH' PERBANKAN:</b>\n\n"
            "Semua institusi perbankan di Malaysia kini diwajibkan oleh Bank Negara Malaysia (BNM) menyediakan ciri <i>Kill Switch</i>.\n\n"
            "• <b>Melalui Aplikasi Bank:</b> Buka aplikasi bank anda (Maybank MAE, CIMB OCTO, RHB Mobile, PB engage dll), pergi ke <i>Settings / Security</i> dan tekan butang <b>'Block Account / Deactivate Access'</b>.\n"
            "• <b>Melalui Panggilan Hotline 24 Jam:</b> Hubungi talian kecemasan kad/akaun bank anda dan pilih menu kecemasan kehilangan kad atau penipuan.\n\n"
            "🔒 <b>Kesan Kill Switch:</b> Akses perbankan atas talian anda akan dibekukan serta-merta, menghalang scammer daripada menyedut baki wang yang masih ada."
        ),
        "links": [
            {"text": "🏦 Dasar Keselamatan BNM", "url": "https://www.bnm.gov.my"}
        ]
    },
    "q4": {
        "cat": "cat_emergency",
        "title": "Bolehkah wang saya diselamatkan dan dipulangkan jika saya hubungi 997?",
        "answer": (
            "💰 <b>PELUANG PEMULANGAN WANG MANGSA:</b>\n\n"
            "Peluang pemulangan bergantung kepada <b>kepantasan tindakan anda</b>:\n\n"
            "✅ <b>Jika wang masih dalam akaun keldai:</b> Bank penerima akan membekukan dana tersebut. Wang ini akan diserahkan kepada mahkamah sebagai barang kes.\n"
            "⚖️ <b>Prosedur Pemulangan Undang-Undang:</b> Selepas kertas siasatan polis selesai, pihak pendakwaan akan memohon Perintah Mahkamah di bawah <b>Seksyen 435 Kanun Tatacara Jenayah (CPC)</b> untuk memulangkan dana yang disita kepada pemilik asal yang sah.\n"
            "❌ <b>Jika wang telah dikeluarkan di ATM:</b> Peluang mendapatkan semula wang secara tunai adalah rendah melainkan aset suspek berjaya disita melalui tindakan mahkamah."
        ),
        "links": [
            {"text": "🌐 Semak Portal SemakMule PDRM", "url": "https://semakmule.rmp.gov.my"}
        ]
    },
    "q5": {
        "cat": "cat_emergency",
        "title": "Scammer ugut nak viralkan gambar/video/data peribadi saya, apa langkah keselamatan?",
        "answer": (
            "🚫 <b>JANGAN SESEKALI MEMBAYAR WANG UGUTAN (BLACKMAIL):</b>\n\n"
            "Scammer tidak akan berhenti meminta wang sekiranya anda membayar kali pertama. Lakukan langkah berikut:\n\n"
            "1. 📸 <b>Simpan Bukti:</b> Tangkap layar <i>(screenshot)</i> mesej ugutan, profil suspek, dan nombor telefon mereka.\n"
            "2. 🔕 <b>Putuskan Sambungan:</b> Sekat <i>(block)</i> nombor suspek dan privasikan semua akaun media sosial anda (Facebook, Instagram, TikTok).\n"
            "3. 👮 <b>Laporan Polis:</b> Buat repot polis di bawah <b>Seksyen 384 / 385 Kanun Keseksaan</b> (Pemerasan / Pemerasan Jenayah).\n"
            "4. 🏛️ <b>Laporan SKMM:</b> Laporkan pautan atau akaun tersebut ke portal e-Aduan SKMM di bawah <b>Seksyen 233 Akta Komunikasi dan Multimedia 1998</b>."
        ),
        "links": [
            {"text": "🌐 Portal e-Aduan SKMM", "url": "https://aduan.mcmc.gov.my"}
        ]
    },
    "q6": {
        "cat": "cat_emergency",
        "title": "Bolehkah saya padam perbualan WhatsApp dengan scammer?",
        "answer": (
            "⚠️ <b>JANGAN PADAM CHAT! INI ADALAH BAHAN BUKTI MAHKAMAH:</b>\n\n"
            "Mesej perbualan WhatsApp, SMS, dan Telegram adalah eksibit undang-undang utama <i>(Chain of Custody Evidence)</i>:\n\n"
            "• <b>Langkah Wajib:</b> Buka perbualan WhatsApp dengan suspek ➔ Tekan menu 3 titik di atas kanan ➔ <i>More</i> ➔ <b>Export Chat</b> ➔ Simpan fail teks dan media ke Google Drive atau emel anda.\n"
            "• <b>Cetak Tangkap Layar:</b> Cetak perbualan penting yang memaparkan nombor telefon suspek, arahan pembayaran, dan resit transaksi untuk diserahkan kepada Pegawai Penyiasat Polis (IO)."
        ),
        "links": [
            {"text": "🌐 Semak Profil Suspek di SemakMule", "url": "https://semakmule.rmp.gov.my"}
        ]
    },

    # ==========================================
    # TIANG 2: AKAUN KELDAI & TUNTUTAN DUIT
    # ==========================================
    "q7": {
        "cat": "cat_mule",
        "title": "Siapakah pemilik akaun bank yang saya pindahkan duit itu?",
        "answer": (
            "👤 <b>ANATOMI AKAUN KELDAI (MULE ACCOUNT):</b>\n\n"
            "Nama individu yang terpapar pada resit bank anda biasanya **BUKAN dalang utama scammer**:\n\n"
            "• Mereka adalah <b>Keldai Akaun</b> — individu yang menjual, menyewakan, atau menyerahkan kawalan akaun bank dan kad ATM mereka kepada sindiket jenayah untuk mendapatkan upah komisen RM200–RM500.\n"
            "• Dalang utama scammer lazimnya beroperasi dari luar negara (seperti Kemboja, Myanmar, Laos) dan mengawal akaun tersebut secara akses perbankan dalam talian."
        ),
        "links": [
            {"text": "🌐 Info SemakMule PDRM", "url": "https://semakmule.rmp.gov.my"}
        ]
    },
    "q8": {
        "cat": "cat_mule",
        "title": "Apakah hukuman ke atas pemilik Keldai Akaun di bawah Pindaan Kanun Keseksaan 2024?",
        "answer": (
            "⚖️ <b>PINDAAN KANUN KESEKSAAN TERKINI (2024):</b>\n\n"
            "Parlimen Malaysia telah meluluskan undang-undang ketat khas untuk membanteras pemilik keldai akaun:\n\n"
            "• <b>Seksyen 424A:</b> Memiliki atau mengawal instrumen pembayaran orang lain tanpa kuasa sah ➔ <b>Penjara sehingga 5 tahun</b> dan denda RM10,000 – RM100,000.\n"
            "• <b>Seksyen 424B:</b> Menyerahkan instrumen pembayaran sendiri kepada orang lain ➔ <b>Penjara sehingga 7 tahun</b> dan denda RM10,000 – RM150,000.\n"
            "• <b>Seksyen 424C:</b> Menggunakan akaun sendiri atau akaun pihak lain untuk transaksi penipuan ➔ <b>Penjara 3 hingga 10 tahun</b> dan denda RM10,000 – RM150,000."
        ),
        "links": [
            {"text": "🏛️ Portal Parlimen Malaysia", "url": "https://www.parlimen.gov.my"}
        ]
    },
    "q9": {
        "cat": "cat_mule",
        "title": "Bolehkah saya saman pemilik akaun keldai di Mahkamah Sivil untuk tuntut duit balik?",
        "answer": (
            "👨‍⚖️ <b>BOLEH! SAMAN SIVIL TERHADAP PEMILIK AKAUN KELDAI:</b>\n\n"
            "Di sisi undang-undang sivil, pemilik akaun bank bertanggungjawab ke atas setiap sen yang memasuki akaun mereka berasaskan doktrin <i>Unjust Enrichment</i> (Penerimaan Manfaat Tanpa Hak):\n\n"
            "• <b>Tuntutan Bawah RM5,000:</b> Anda boleh memfailkan <b>Tuntutan Kecil (Small Claims Procedure)</b> di Mahkamah Majistret berdekatan. Kos pendaftaran hanyalah sekitar RM20–RM30 dan kedua-dua pihak <b>DILARANG</b> menggunakan peguam.\n"
            "• <b>Tuntutan Melebihi RM5,000:</b> Anda boleh melantik peguam sivil untuk mengeluarkan Notis Tuntutan <i>(Letter of Demand)</i> dan memfailkan saman sivil di Mahkamah Majistret / Sesyen."
        ),
        "links": [
            {"text": "🏛️ Portal Kehakiman Malaysia", "url": "https://www.kehakiman.gov.my"}
        ]
    },
    "q10": {
        "cat": "cat_mule",
        "title": "Saya pernah bagi kad ATM/akaun bank saya pada pihak ketiga, adakah saya boleh didakwa?",
        "answer": (
            "🚨 <b>YA, ANDA BOLEH DIDAKWA & DISENARAIHITAM!</b>\n\n"
            "Alasan 'saya tidak tahu akaun digunakan untuk menipu' bukan lagi pembelaan mutlak di mahkamah:\n\n"
            "1. <b>Pendakwaan Jenayah:</b> Anda boleh dituduh di bawah <b>Seksyen 424B Kanun Keseksaan</b>.\n"
            "2. <b>Senarai Hitam Bank Negara (BNM):</b> Nama anda akan dimasukkan ke dalam pangkalan data keldai akaun institusi kewangan. Semua akaun bank anda akan ditutup dan anda dilarang membuka sebarang akaun bank baharu di Malaysia."
        ),
        "links": [
            {"text": "🏦 Portal Rasmi BNM", "url": "https://www.bnm.gov.my"}
        ]
    },
    "q11": {
        "cat": "cat_mule",
        "title": "Bagaimana menyemak sama ada nombor telefon atau akaun bank pernah terlibat scam?",
        "answer": (
            "🔍 <b>SEMAKAN SIAP-SEDIA PDRM SEMAKMULE:</b>\n\n"
            "PDRM JSJK menyediakan portal carian rekod ancaman jenayah komersial rasmi:\n\n"
            "• <b>Laman Web:</b> Buka <a href=\"https://semakmule.rmp.gov.my\">semakmule.rmp.gov.my</a>\n"
            "• <b>Cara Carian:</b> Pilih kategori sama ada 'Nombor Telefon' atau 'Nombor Akaun Bank', masukkan nombor suspek dan kod keselamatan captcha.\n"
            "• <b>Keputusan:</b> Portal akan memaparkan bilangan laporan polis yang pernah dibuat terhadap akaun tersebut dan jumlah carian oleh orang ramai."
        ),
        "links": [
            {"text": "🌐 Buka Portal PDRM SemakMule", "url": "https://semakmule.rmp.gov.my"}
        ]
    },
    "q12": {
        "cat": "cat_mule",
        "title": "Kenapa bank tidak boleh terus pulangkan duit walaupun akaun keldai telah dibekukan?",
        "answer": (
            "⚖️ <b>HALANGAN UNDANG-UNDANG PEMULANGAN WANG:</b>\n\n"
            "Pihak bank tidak mempunyai kuasa mutlak untuk memindahkan wang keluar dari akaun seseorang secara sesuka hati:\n\n"
            "• <b>Hak Milik Berkanun:</b> Wang yang telah dimasukkan ke dalam sesuatu akaun dianggap sebagai tanggungan sah akaun tersebut sehinggalah diputuskan sebaliknya oleh mahkamah.\n"
            "• <b>Perintah Mahkamah Seksyen 435 CPC:</b> Pihak Polis dan Pendakwa Raya perlu melengkapkan kertas siasatan untuk membuktikan wang tersebut adalah harta hasil jenayah <i>(proceeds of crime)</i>, barulah Hakim Mahkamah mengeluarkan Perintah Pemulangan Harta."
        ),
        "links": [
            {"text": "🌐 Semak Status di SemakMule", "url": "https://semakmule.rmp.gov.my"}
        ]
    },

    # ==========================================
    # TIANG 3: BALAI POLIS, REPOT & IO
    # ==========================================
    "q13": {
        "cat": "cat_police",
        "title": "Wajibkah saya hadir ke Balai Polis membuat laporan jika sudah hubungi NSRC 997?",
        "answer": (
            "👮 <b>WAJIB HADIR KE BALAI POLIS DALAM 24 JAM:</b>\n\n"
            "Panggilan ke talian 997 adalah **tindakan kecemasan awal**, BUKAN pengganti laporan polis rasmi:\n\n"
            "• <b>Sebab Wajib Buat Repot Fizikal:</b> Pembukaan Kertas Siasatan Jenayah <i>(Investigation Paper)</i> hanya boleh dimulakan selepas Pegawai Penyiasat menerima salinan laporan polis bertandatangan pengadu di bawah <b>Seksyen 107 Kanun Tatacara Jenayah (CPC)</b>.\n"
            "• Tanpa repot polis fizikal, akaun yang dibekukan sementara oleh bank boleh dinyahbekukan semula selepas beberapa hari!"
        ),
        "links": [
            {"text": "🌐 Lokasi Balai Polis PDRM Terdekat", "url": "https://www.rmp.gov.my"}
        ]
    },
    "q14": {
        "cat": "cat_police",
        "title": "Di bawah seksyen undang-undang apa kes penipuan disiasat oleh Polis?",
        "answer": (
            "⚖️ <b>SEKSYEN 420 KANUN KESEKSAAN (MENIPU):</b>\n\n"
            "Kes scam kewangan disiasat di bawah <b>Seksyen 420 Kanun Keseksaan</b>:\n\n"
            "• <b>Takrifan:</b> Menipu seseorang dan dengan curangnya mendorong orang itu menyerahkan apa-apa harta wang ringgit kepada mana-mana orang.\n"
            "• <b>Hukuman Jika Sabit Kesalahan:</b> Hukuman <b>penjara tidak kurang 1 tahun dan tidak lebih 10 tahun</b>, dan dengan <b>sebat</b>, dan boleh juga dikenakan <b>denda</b>."
        ),
        "links": [
            {"text": "📢 Facebook Cyber Crime Alert PDRM", "url": "https://www.facebook.com/CyberCrimeAlertRMP"}
        ]
    },
    "q15": {
        "cat": "cat_police",
        "title": "Apakah dokumen dan bukti fizikal yang WAJIB saya bawa ke Balai Polis?",
        "answer": (
            "📁 <b>SENARAI SEMAK DOKUMEN WAJIB KE BALAI POLIS:</b>\n\n"
            "Bawa bersama salinan cetak <i>(hardcopy/printout)</i> berikut:\n\n"
            "1. 🪪 <b>Salinan Kad Pengenalan (MyKad)</b> anda.\n"
            "2. 🧾 <b>Penyata Bank / Resit Transaksi Rasmi</b> (memaparkan rujukan transaksi, tarikh, masa, no akaun pengirim & penerima).\n"
            "3. 💬 <b>Salinan Cetak Chat WhatsApp / Media Sosial</b> bersama suspek.\n"
            "4. 🔢 <b>No Rujukan Aduan NSRC 997</b> (jika anda telah hubungi 997).\n"
            "5. 🔗 <b>Pautan / Tangkap Layar Iklan</b> tawaran palsu scammer."
        ),
        "links": [
            {"text": "📋 Jana Draf Repot Polis (Menu Bot)", "callback_data": "help_report"}
        ]
    },
    "q16": {
        "cat": "cat_police",
        "title": "Siapakah Pegawai Penyiasat (IO) dan bagaimana cara berhubung?",
        "answer": (
            "👮 <b>PERANAN PEGAWAI PENYIASAT (IO):</b>\n\n"
            "Pegawai Penyiasat <i>(Investigating Officer / IO)</i> adalah inspektor/pegawai polis dari Bahagian Siasatan Jenayah Komersil (BSJKD) Ibu Pejabat Polis Daerah (IPD) yang menguruskan kes anda:\n\n"
            "• <b>Langkah Mendapatkan Kontak IO:</b> Selepas membuat laporan di kaunter balai, minta nombor telefon bilik siasatan BSJKD IPD berkenaan.\n"
            "• Dalam tempoh 1 hingga 3 hari bekerja, hubungi IPD tersebut dengan memberikan No Repot Polis anda untuk mendapatkan nama dan nombor telefon bimbit IO kes anda."
        ),
        "links": [
            {"text": "📞 Talian Terus CCID Bukit Aman", "url": "https://semakmule.rmp.gov.my"}
        ]
    },
    "q17": {
        "cat": "cat_police",
        "title": "Bolehkah saya buat laporan polis secara online melalui portal e-Reporting?",
        "answer": (
            "⚠️ <b>TIDAK BOLEH UNTUK KES PENIPUAN / JENAYAH SIBER:</b>\n\n"
            "Sistem e-Reporting PDRM <i>(ereporting.rmp.gov.my)</i> hanya dibenarkan untuk kes kehilangan dokumen peribadi bukan jenayah (seperti hilang MyKad, lesen memandu, atau kad bank tertelan ATM).\n\n"
            "• Kes penipuan kewangan melibatkan eksibit bukti transaksi bank dan pernyataan saksi yang <b>MEWAJIBKAN kehadiran fizikal</b> pengadu di balai polis untuk menurunkan tandatangan rasmi."
        ),
        "links": [
            {"text": "🌐 Portal Rasmi PDRM", "url": "https://www.rmp.gov.my"}
        ]
    },
    "q18": {
        "cat": "cat_police",
        "title": "Apa beza 'Cover Report' dengan 'Laporan Pembukaan Kertas Siasatan Jenayah'?",
        "answer": (
            "📑 <b>PERBEZAAN DUA JENIS REPOT POLIS:</b>\n\n"
            "• <b>1. Cover Report (Laporan Makluman):</b> Sekadar merekodkan fakta bahawa insiden telah berlaku (contohnya untuk memohon pembatalan kad bank atau tuntutan insurans syarikat). Polis tidak membuka kertas siasatan jenayah secara mendalam.\n"
            "• <b>2. Laporan Maklumat Pertama (Seksyen 107 CPC):</b> Pengadu menyatakan secara spesifik bahawa beliau telah ditipu dan menuntut tindakan undang-undang di bawah Kanun Keseksaan. Kertas Siasatan dibuka, suspek dijejaki, dan perintah sitaan akaun dikeluarkan."
        ),
        "links": [
            {"text": "📢 Facebook JSJK PDRM", "url": "https://www.facebook.com/CyberCrimeAlertRMP"}
        ]
    },

    # ==========================================
    # TIANG 4: LIABILITI BANK, BNM & MAHKAMAH
    # ==========================================
    "q19": {
        "cat": "cat_bank",
        "title": "Adakah pihak bank wajib bertanggungjawab jika transaksi berlaku tanpa TAC/OTP?",
        "answer": (
            "🏛️ <b>LIABILITI PIHAK BANK & DASAR BNM:</b>\n\n"
            "Bank Negara Malaysia (BNM) telah mengeluarkan dasar tegas bahawa bank bertanggungjawab terhadap keselamatan perkhidmatan perbankan mereka:\n\n"
            "• Jika pemindahan wang berlaku akibat kelemahan sistem sekuriti bank (bukan kerana kecuaian mangsa menyerahkan OTP/kata laluan kepada pihak ketiga), bank <b>wajib menyiasat secara adil</b> dan boleh dipertanggungjawabkan membayar ganti rugi.\n"
            "• Semua bank kini telah beralih daripada SMS OTP kepada pengesahan dua-faktor selamat (cth: Secure2u / SecurePRN)."
        ),
        "links": [
            {"text": "🏦 Portal Rasmi BNM", "url": "https://www.bnm.gov.my"}
        ]
    },
    "q20": {
        "cat": "cat_bank",
        "title": "Bank enggan bantu dan menyalahkan saya 100%, bagaimana cara mengadu ke BNM?",
        "answer": (
            "📮 <b>CARA MENGADU KE BANK NEGARA MALAYSIA (BNM TELELINK):</b>\n\n"
            "Jika unit aduan bank anda mengeluarkan surat penolakan rasmi <i>(Final Decision Letter)</i>:\n\n"
            "1. 🌐 Buka portal e-LINK di: <a href=\"https://telelink.bnm.gov.my\">telelink.bnm.gov.my</a>\n"
            "2. 📞 Atau hubungi talian BNM TELELINK di <b>1-300-88-5465</b> <i>(Waktu bekerja: 9.00 pagi – 5.00 petang)</i>.\n"
            "3. Lampirkan surat penolakan bank, repot polis, dan kronologi kes untuk disemak oleh pegawai penyelia BNM."
        ),
        "links": [
            {"text": "🌐 Portal Aduan BNM TELELINK", "url": "https://telelink.bnm.gov.my"}
        ]
    },
    "q21": {
        "cat": "cat_bank",
        "title": "Apa itu Ombudsman Perkhidmatan Kewangan (OFS) dan bagaimana menuntut pampasan?",
        "answer": (
            "⚖️ <b>OMBUDSMAN PERKHIDMATAN KEWANGAN (OFS):</b>\n\n"
            "OFS adalah badan penyelesaian pertikaian kewangan bebas yang diluluskan oleh Bank Negara Malaysia:\n\n"
            "• <b>Perkhidmatan Percuma:</b> Mangsa boleh membawa pertikaian dengan pihak bank ke OFS secara 100% percuma tanpa perlu melantik peguam.\n"
            "• <b>Bidang Kuasa Tuntutan:</b> OFS boleh mengadili pertikaian transaksi kewangan tanpa izin sehingga had <b>RM25,000</b>.\n"
            "• <b>Syarat Utama:</b> Aduan mesti difailkan dalam tempoh <b>6 bulan</b> selepas menerima surat keputusan muktamad daripada bank anda."
        ),
        "links": [
            {"text": "🌐 Portal Rasmi OFS", "url": "https://www.ofs.org.my"}
        ]
    },
    "q22": {
        "cat": "cat_bank",
        "title": "Bolehkah kes penipuan scam dibawa ke Tribunal Tuntutan Pengguna Malaysia (TTPM)?",
        "answer": (
            "🛍️ <b>BIDANG KUASA TRIBUNAL PENGGUNA (TTPM):</b>\n\n"
            "• <b>Boleh Untuk Kes E-Dagang Pengguna Sahaja:</b> Jika anda membeli barang daripada peniaga berdaftar SSM tetapi barang tidak dihantar atau tidak mengikut spesifikasi (tuntutan bawah RM50,000).\n"
            "• <b>TIDAK BOLEH Untuk Jenayah Scammer Tulen:</b> Kes penipuan jenayah (seperti Macau Scam, skim pelaburan haram, atau sindiket phishing) berada di luar bidang kuasa TTPM dan tertakluk di bawah bidang kuasa jenayah Mahkamah Majistret & PDRM."
        ),
        "links": [
            {"text": "🌐 Portal TTPM KPDN", "url": "https://ttpm.kpdn.gov.my"}
        ]
    },
    "q23": {
        "cat": "cat_bank",
        "title": "Berapa lama tempoh proses perundangan di mahkamah sebelum mangsa menerima semula wang?",
        "answer": (
            "⏳ <b>GARIS MASA PROSES PERUNDANGAN MAHKAMAH:</b>\n\n"
            "Bagi wang yang berjaya dibekukan oleh pihak berkuasa:\n\n"
            "• <b>Tempoh Siasatan:</b> Siasatan polis dan pemintasan akaun mengambil masa purata <b>1 hingga 3 bulan</b>.\n"
            "• <b>Proses Pendakwaan:</b> Pendakwaan suspek di mahkamah mengambil masa antara <b>3 hingga 12 bulan</b>.\n"
            "• Selepas kes selesai, Timbalan Pendakwa Raya (DPP) akan memfailkan permohonan pelupusan harta barang kes di bawah Seksyen 435 CPC untuk mengarahkan pihak bank mengeluarkan cek pemulangan kepada mangsa."
        ),
        "links": [
            {"text": "🏛️ Portal Rasmi Kehakiman", "url": "https://www.kehakiman.gov.my"}
        ]
    },
    "q24": {
        "cat": "cat_bank",
        "title": "Bolehkah akaun bank saya dibekukan jika saya tidak sengaja menerima wang scammer?",
        "answer": (
            "⚠️ <b>RISIKO KELDAI AKAUN TIDAK SENGAJA (TRANSIT MULE):</b>\n\n"
            "Ya, pihak bank dan polis boleh membekukan akaun anda di bawah Akta Pencegahan Pengubahan Wang Haram (AMLA 2001) jika terdapat aliran dana haram melaluinya:\n\n"
            "• <b>Tindakan Anda:</b> Jangan sesekali memindahkan wang tersebut kepada pihak ketiga mengikut arahan sesiapa! Segera hubungi pihak bank anda dan hadir ke balai polis untuk membuat laporan bahawa anda menerima wang yang mencurigakan tanpa sebarang niat jahat <i>(Mens Rea)</i>."
        ),
        "links": [
            {"text": "🏦 Dasar AMLA Bank Negara", "url": "https://www.bnm.gov.my"}
        ]
    },

    # ==========================================
    # TIANG 5: UGUTAN & PENIPUAN SUSULAN
    # ==========================================
    "q25": {
        "cat": "cat_safety",
        "title": "Scammer menyamar polis / mahkamah ugut nak tangkap saya di rumah, adakah benar?",
        "answer": (
            "🚫 <b>100% PALSU! JANGAN PANIK DENGAN UGUTAN TANGKAPAN:</b>\n\n"
            "Ini adalah taktik psikologi klasik sindiket penipuan (Macau Scam):\n\n"
            "• PDRM, Mahkamah, SPRM, LHDN, mahupun Kastam Diraja Malaysia <b>TIDAK PERNAH</b> menghubungi orang awam melalui panggilan WhatsApp atau telefon untuk meminta penyelesaian wang atau memindahkan wang ke akaun peribadi.\n"
            "• Polis juga <b>TIDAK PERNAH</b> menghantar gambar waran tangkap digital melalui WhatsApp! Sebarang waran tangkap rasmi wajib disampaikan secara fizikal oleh pegawai polis beruniform di premis anda."
        ),
        "links": [
            {"text": "📢 Kenyataan Rasmi PDRM CCID", "url": "https://www.facebook.com/CyberCrimeAlertRMP"}
        ]
    },
    "q26": {
        "cat": "cat_safety",
        "title": "Ada pihak hubungi saya mengaku 'Pakar Pemulihan Wang / Hacker' boleh dapatkan duit balik, adakah sah?",
        "answer": (
            "🚨 <b>AMARAN KERAS: INI ADALAH 'RECOVERY SCAM' (PENIPUAN LAPIS KEDUA):</b>\n\n"
            "Sindiket kerap mensasarkan mangsa yang baru sahaja ditipu kerana mereka tahu mangsa sedang terdesak untuk mendapatkan wang kembali:\n\n"
            "• Mereka mendakwa mempunyai peguam khas, 'orang dalam bank', atau 'hacker' yang kononnya boleh menggodam akaun scammer.\n"
            "• Mereka akan meminta mangsa membayar fi deposit atau yuran peguam terlebih dahulu, kemudian terus menghilangkan diri.\n"
            "• <b>Hanya saluran PDRM, NSRC 997, dan Bank Negara Malaysia</b> yang mempunyai kuasa undang-undang sah untuk memulihkan dana anda!"
        ),
        "links": [
            {"text": "🌐 Semak Portal SemakMule", "url": "https://semakmule.rmp.gov.my"}
        ]
    },
    "q27": {
        "cat": "cat_safety",
        "title": "Saya ada tekan link dan pasang fail APK daripada scammer, apa risiko kepada telefon saya?",
        "answer": (
            "📱 <b>BAHAYA FAIL APK PALSU (MALWARE PENCURI PERBANKAN):</b>\n\n"
            "Fail APK yang dimuat turun di luar Google Play Store mengandungi trojan pencuri SMS <i>(SMS Spyware)</i>:\n\n"
            "• Aplikasi palsu tersebut akan meminta kebenaran membaca SMS pada telefon anda.\n"
            "• Sebaik sahaja dipasang, ia akan meniru halaman log masuk perbankan dan memintas kod SMS OTP/TAC bank anda tanpa sebarang notifikasi berbunyi di skrin, membolehkan scammer memindahkan wang anda secara senyap."
        ),
        "links": [
            {"text": "🛡️ Portal Keselamatan Siber Cyber999", "url": "https://www.cyber999.my"}
        ]
    },
    "q28": {
        "cat": "cat_safety",
        "title": "Bagaimana cara membersihkan telefon pintar selepas memasang fail APK scammer?",
        "answer": (
            "🧹 <b>3 LANGKAH PANTAS BERSIHKAN TELEFON:</b>\n\n"
            "1. ✈️ <b>Mod Penerbangan Serta-Merta:</b> Hidupkan <i>Airplane Mode</i> atau cabut kad SIM dan matikan WiFi serta-merta untuk mematikan sambungan kawalan jauh scammer.\n"
            "2. 🔑 <b>Tukar Kata Laluan Bank dari Telefon Lain:</b> Gunakan komputer atau telefon ahli keluarga yang bersih untuk menukar semua kata laluan dan PIN perbankan anda.\n"
            "3. 🔄 <b>Factory Reset:</b> Lakukan penetapan semula kilang <i>(Factory Reset)</i> pada telefon anda untuk memadamkan fail malware sehingga ke akar umbi."
        ),
        "links": [
            {"text": "🌐 Pusat Respons Cyber999", "url": "https://www.cyber999.my"}
        ]
    },
    "q29": {
        "cat": "cat_safety",
        "title": "Bagaimana cara membuat aduan rasmi ke SKMM (MCMC)?",
        "answer": (
            "🌐 <b>SALURAN ADUAN RASMI SKMM (MCMC):</b>\n\n"
            "Aduan ke Suruhanjaya Komunikasi dan Multimedia Malaysia (SKMM) bertujuan untuk menyekat pautan laman web palsu dan talian telefon suspek:\n\n"
            "• <b>Portal Rasmi:</b> <a href=\"https://aduan.mcmc.gov.my\">aduan.mcmc.gov.my</a>\n"
            "• <b>Emel Aduan:</b> <code>aduanskmm@mcmc.gov.my</code>\n"
            "• <b>WhatsApp SKMM:</b> <code>016-220 6262</code>\n"
            "• Lampirkan tangkap layar pautan phishing dan nombor WhatsApp scammer untuk tindakan sekatan di bawah Akta Komunikasi dan Multimedia 1998."
        ),
        "links": [
            {"text": "🌐 Buka Portal e-Aduan SKMM", "url": "https://aduan.mcmc.gov.my"}
        ]
    },
    "q30": {
        "cat": "cat_safety",
        "title": "Di mana saya boleh dapatkan sokongan emosi jika mengalami tekanan mental akibat kerugian wang?",
        "answer": (
            "💚 <b>ANDA TIDAK BERSEORANGAN. BANTUAN SOKONGAN EMOSI PERCUMA:</b>\n\n"
            "Kehilangan wang simpanan boleh mengakibatkan kejutan, kemurungan, dan tekanan jiwa yang amat berat. Dapatkan sokongan kaunselor bertauliah tanpa sebarang bayaran:\n\n"
            "• 🩺 <b>Talian HEAL 15555:</b> Talian Bantuan Krisis Kesihatan Mental KKM (Beroperasi setiap hari 8.00 AM – 12.00 AM). Hubungi <b>15555</b>.\n"
            "• 🤝 <b>Befrienders KL (24 Jam):</b> Khidmat sokongan emosi percuma di talian <b>03-7627 2929</b>.\n"
            "• 💜 <b>Talian Kasih KPWKM:</b> Dail <b>15999</b> atau WhatsApp <code>019-261 5999</code>."
        ),
        "links": [
            {"text": "🌐 Laman Talian HEAL KKM", "url": "https://www.moh.gov.my"},
            {"text": "🤝 Laman Befrienders KL", "url": "https://www.befrienders.org.my"}
        ]
    }
}

# ==============================================================================
# FUNGSI PEMBINA ANTARAMUKA FAQ TELEGRAM
# ==============================================================================

def build_faq_hub_view():
    """Membina paparan Hab Utama 30 FAQ"""
    text = (
        "📚 <b>HAB PANDUAN PANTAS MANGSA SCAM (30 FAQ WAJIB TAHU)</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "Panduan rasmi hak perundangan mangsa, tindakan jam pertama, dan prosedur tuntutan wang berlandaskan undang-undang Malaysia terkini (Kanun Keseksaan & Akta Bank Negara).\n\n"
        "💡 <i>Pilih kategori panduan yang anda perlukan di bawah:</i>"
    )

    buttons = []
    # Kategori 1 hingga 5
    for cat_id, cat_info in FAQ_CATEGORIES.items():
        buttons.append([{"text": cat_info["title"], "callback_data": f"faq_cat_{cat_id}"}])

    # 3 Soalan Emas Paling Kritikal (Pantas 1-Ketik)
    buttons.append([
        {"text": "⭐ 1. Tindakan Jam Pertama?", "callback_data": "faq_q_q1"},
        {"text": "⭐ 2. Duit Boleh Dapat Balik?", "callback_data": "faq_q_q4"}
    ])
    buttons.append([
        {"text": "⭐ 3. Saman Akaun Keldai?", "callback_data": "faq_q_q9"},
        {"text": "⭐ 4. Perlu ke Balai Polis?", "callback_data": "faq_q_q13"}
    ])
    buttons.append([
        {"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}
    ])

    markup = {"inline_keyboard": buttons}
    return text, markup

def build_faq_category_view(cat_id):
    """Membina paparan senarai soalan mengikut kategori"""
    cat_info = FAQ_CATEGORIES.get(cat_id)
    if not cat_info:
        return build_faq_hub_view()

    text = (
        f"<b>{cat_info['title'].upper()}</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"ℹ️ <i>{cat_info['desc']}</i>\n\n"
        "Sila pilih soalan di bawah untuk membaca jawapan padu beserta sitasi rasmi pihak berkuasa:"
    )

    buttons = []
    for q_id in cat_info["q_ids"]:
        q_data = SCAM_VICTIM_FAQS.get(q_id)
        if q_data:
            buttons.append([{"text": f"❓ {q_data['title']}", "callback_data": f"faq_q_{q_id}"}])

    buttons.append([
        {"text": "« Kembali ke Hab FAQ", "callback_data": "faq_hub"},
        {"text": "« Menu Utama", "callback_data": "help_menu"}
    ])

    markup = {"inline_keyboard": buttons}
    return text, markup

def build_faq_answer_view(q_id):
    """Membina paparan jawapan terperinci bersitasi untuk soalan tertentu"""
    q_data = SCAM_VICTIM_FAQS.get(q_id)
    if not q_data:
        return build_faq_hub_view()

    cat_id = q_data.get("cat", "cat_emergency")
    cat_info = FAQ_CATEGORIES.get(cat_id, {})

    text = (
        f"❓ <b>SOALAN LAZIM:</b>\n"
        f"<b>{q_data['title']}</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
        f"{q_data['answer']}\n\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "⚖️ <i>Maklumat ini disediakan untuk panduan kesedaran dan tindakan kecemasan mangsa scam mengikut statut undang-undang Malaysia.</i>"
    )

    buttons = []
    # Butang pautan agensi rasmi
    for lk in q_data.get("links", []):
        buttons.append([{"text": lk["text"], "url": lk["url"]}])

    # Butang navigasi kembali
    buttons.append([
        {"text": f"« Senarai {cat_info.get('short_title', 'Kategori')}", "callback_data": f"faq_cat_{cat_id}"},
        {"text": "« Hab 30 FAQ", "callback_data": "faq_hub"}
    ])
    buttons.append([
        {"text": "« Menu Utama", "callback_data": "help_menu"}
    ])

    markup = {"inline_keyboard": buttons}
    return text, markup
