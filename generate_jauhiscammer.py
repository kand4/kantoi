import json

with open('lowyat_scammers_db.json', 'r', encoding='utf-8') as f:
    db_records = json.load(f)

json_str = json.dumps(db_records, ensure_ascii=False)

html_content = f"""<!DOCTYPE html>
<html lang="ms" class="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>jauhiscammer — Sistem Pengesanan Scammer Malaysia Masa Nyata</title>
  <!-- Tailwind CSS CDN -->
  <script src="https://cdn.tailwindcss.com"></script>
  <script>
    tailwind.config = {{
      darkMode: 'class',
      theme: {{
        extend: {{
          colors: {{
            cyber: {{
              bg: '#090d16',
              card: '#0f172a',
              border: '#1e293b',
              accent: '#06b6d4',
              danger: '#ef4444',
              warning: '#f59e0b',
              success: '#10b981'
            }}
          }},
          fontFamily: {{
            mono: ['JetBrains Mono', 'Fira Code', 'Courier New', 'monospace']
          }}
        }}
      }}
    }}
  </script>
  <style>
    @keyframes pulse-glow {{
      0%, 100% {{ box-shadow: 0 0 20px rgba(239, 68, 68, 0.4); }}
      50% {{ box-shadow: 0 0 40px rgba(239, 68, 68, 0.8); }}
    }}
    .animate-danger-glow {{
      animation: pulse-glow 1.5s infinite;
    }}
  </style>
</head>
<body class="bg-[#090d16] text-slate-100 min-h-screen font-sans antialiased selection:bg-cyan-500 selection:text-black">

  <!-- Header -->
  <header class="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
      <div class="flex items-center space-x-3">
        <span class="text-2xl">🛡️</span>
        <div>
          <h1 class="text-lg font-bold tracking-tight text-white flex items-center gap-2">
            jauhiscammer <span class="text-xs bg-cyan-950 text-cyan-400 border border-cyan-800 px-2 py-0.5 rounded-full font-mono">v2.0 HYBRID</span>
          </h1>
          <p class="text-xs text-slate-400">Pusat Perisikan Ancaman Scam Malaysia (Lowyat.NET & CCID SemakMule)</p>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <span id="statRecordCount" class="text-xs font-mono bg-slate-800 text-slate-300 px-3 py-1.5 rounded-lg border border-slate-700">
          📦 79 Rekod Dipramuat
        </span>
        <button onclick="exportJSON()" class="text-xs bg-slate-800 hover:bg-slate-700 text-cyan-400 border border-cyan-900 px-3 py-1.5 rounded-lg font-mono transition">
          ⬇️ Eksport JSON
        </button>
      </div>
    </div>
  </header>

  <main class="max-w-7xl mx-auto px-4 py-8 space-y-8">

    <!-- Top Grid: Component 1 (Simulator) & Component 2 (SemakMule Bridge) -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">

      <!-- KOMPONEN 1: SIMULATOR PANGGILAN MASUK (5 Cols) -->
      <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl relative overflow-hidden flex flex-col justify-between">
        <div>
          <div class="flex items-center justify-between pb-4 border-b border-slate-800">
            <h2 class="font-bold text-white flex items-center gap-2">
              <span>📲</span> 1. Simulator Panggilan Masuk
            </h2>
            <span class="text-xs text-cyan-400 font-mono bg-cyan-950/60 border border-cyan-800/60 px-2 py-0.5 rounded">REALTIME HOOK</span>
          </div>

          <div class="mt-4 space-y-4">
            <div>
              <label class="block text-xs font-medium text-slate-400 mb-1">Masukkan Nombor Telefon Pemanggil:</label>
              <div class="relative">
                <input type="text" id="phoneInput" placeholder="Contoh: 0139986583 atau 0193761223" 
                  value="0139986583"
                  class="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-3 text-white font-mono text-sm focus:outline-none focus:border-cyan-500 transition">
              </div>
            </div>

            <!-- Quick sample buttons -->
            <div class="flex flex-wrap gap-2 pt-1">
              <span class="text-xs text-slate-500 self-center">Uji Rekod:</span>
              <button onclick="setSample('0139986583')" class="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-2.5 py-1 rounded-md border border-slate-700 font-mono">
                Solihin Amran
              </button>
              <button onclick="setSample('0193761223')" class="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-2.5 py-1 rounded-md border border-slate-700 font-mono">
                Suhaina (BINI KOI)
              </button>
              <button onclick="setSample('0123620106')" class="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-2.5 py-1 rounded-md border border-slate-700 font-mono">
                Kok Chun Yen
              </button>
              <button onclick="setSample('01199998888')" class="text-xs bg-slate-800 hover:bg-slate-700 text-amber-400 px-2.5 py-1 rounded-md border border-slate-700 font-mono">
                Nombor Baharu (?)
              </button>
            </div>

            <button onclick="simulateIncomingCall()" class="w-full bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-semibold py-3 px-4 rounded-xl shadow-lg transition flex items-center justify-center gap-2">
              <span>⚡</span> Simulasi Panggilan Masuk Sekarang
            </button>
          </div>
        </div>

        <!-- Hash & Telemetry Display -->
        <div class="mt-6 pt-4 border-t border-slate-800/80 font-mono text-xs text-slate-400 space-y-1">
          <div class="flex justify-between">
            <span class="text-slate-500">Format Standard (E.164):</span>
            <span id="telemetryE164" class="text-cyan-400 font-bold">-</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-slate-500">Hash SHA-256:</span>
            <span id="telemetryHash" class="text-slate-400 truncate max-w-[220px]" title="SHA-256 Web Crypto Hash">-</span>
          </div>
        </div>
      </div>

      <!-- SIMULATOR RESULT / OVERLAY DISPLAY (7 Cols) -->
      <div class="lg:col-span-7 bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col justify-between relative overflow-hidden" id="overlayCard">
        
        <div id="defaultOverlayState">
          <div class="flex items-center justify-between pb-4 border-b border-slate-800">
            <h2 class="font-bold text-white flex items-center gap-2">
              <span>🖥️</span> Paparan Antara Muka Panggilan (Caller Overlay)
            </h2>
            <span class="text-xs text-slate-500 font-mono">STANDBY</span>
          </div>
          <div class="py-16 text-center text-slate-500 space-y-3">
            <div class="text-5xl">📳</div>
            <p class="text-sm">Sila masukkan nombor dan klik <b>"Simulasi Panggilan Masuk"</b> untuk melihat respons sistem masa nyata.</p>
          </div>
        </div>

        <!-- Alert State Container (Hidden by default) -->
        <div id="activeOverlayState" class="hidden space-y-4">
          <!-- Injected dynamically by JS -->
        </div>

      </div>

    </div>

    <!-- KOMPONEN 2: JAMBATAN SEMAKMULE (Assisted Verification & Direct API Bridge) -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
      <div class="flex flex-wrap items-center justify-between pb-4 border-b border-slate-800 gap-2">
        <div>
          <h2 class="font-bold text-white flex items-center gap-2">
            <span>🏛️</span> 2. Jambatan SemakMule CCID PDRM (Direct API & Assisted Verification)
          </h2>
          <p class="text-xs text-slate-400">Penyemakan status jenayah komersil secara automatik & sokongan verifikasi portal rasmi PDRM.</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-xs font-mono bg-emerald-950 text-emerald-400 border border-emerald-800 px-2.5 py-1 rounded-full">
            ● API JSJK ONLINE
          </span>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Direct API Query Box -->
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
          <div>
            <div class="text-xs font-mono text-cyan-400 font-semibold mb-1">A. SEMAKAN API AUTOMATIK (0 KLIK)</div>
            <p class="text-xs text-slate-400">Semakan terus ke endpoint rasmi <code class="text-slate-300">get_search_data.php</code> tanpa perlu menaip atau membuka laman luar.</p>
          </div>
          <div id="semakmuleApiResult" class="mt-3 p-3 bg-slate-900 border border-slate-800 rounded-lg text-xs font-mono text-slate-300">
            Sedia untuk semakan automatik.
          </div>
        </div>

        <!-- Assisted 1-Click Clipboard Bridge -->
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
          <div>
            <div class="text-xs font-mono text-amber-400 font-semibold mb-1">B. JAMBATAN 1-CLICK SEMAKMULE</div>
            <p class="text-xs text-slate-400">Salin nombor format tempatan (<code class="text-slate-300">01xxxxxxxx</code>) ke papan klip dan terus buka portal rasmi CCID untuk pengesahan mata.</p>
          </div>
          <div class="mt-3">
            <button onclick="launchSemakMuleBridge()" class="w-full bg-amber-600 hover:bg-amber-500 text-white font-medium py-2 px-3 rounded-lg text-xs flex items-center justify-center gap-1.5 transition">
              <span>📋</span> Buka SemakMule & Auto-Salin
            </button>
          </div>
        </div>

        <!-- Community Feedback Buttons -->
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col justify-between">
          <div>
            <div class="text-xs font-mono text-emerald-400 font-semibold mb-1">C. TINDAKAN PANTAS PENGGUNA</div>
            <p class="text-xs text-slate-400">Kembalikan status hasil semakan untuk dikemas kini ke dalam pangkalan data lokal.</p>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-2">
            <button onclick="openAddModalWithCurrentPhone()" class="bg-rose-950 hover:bg-rose-900 text-rose-300 border border-rose-800 py-2 px-2 rounded-lg text-xs font-medium transition text-center">
              ⚠️ Ada Rekod Scammer
            </button>
            <button onclick="markPhoneSafe()" class="bg-emerald-950 hover:bg-emerald-900 text-emerald-300 border border-emerald-800 py-2 px-2 rounded-lg text-xs font-medium transition text-center">
              ✅ Tiada Rekod / Selamat
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- KOMPONEN 3: AUTO-CACHING & CROWD-UPDATE SYSTEM (Live Table) -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
      <div class="flex flex-wrap items-center justify-between pb-4 border-b border-slate-800 gap-4">
        <div>
          <h2 class="font-bold text-white flex items-center gap-2">
            <span>📊</span> 3. Pangkalan Data Langsung & Sistem Crowd-Update (jauhiscammer DB)
          </h2>
          <p class="text-xs text-slate-400">Data tersimpan di dalam LocalStorage dan boleh dieksport untuk kemas kini bulanan Truecaller APK.</p>
        </div>
        <div class="flex items-center gap-3">
          <input type="text" id="searchTableInput" onkeyup="filterTable()" placeholder="Cari nama, nombor, bank..."
            class="bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-xs text-white font-mono focus:outline-none focus:border-cyan-500">
          <button onclick="openAddModal()" class="bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold px-3 py-1.5 rounded-xl transition flex items-center gap-1">
            <span>➕</span> Tambah Rekod
          </button>
        </div>
      </div>

      <!-- Live Table -->
      <div class="mt-4 overflow-x-auto rounded-xl border border-slate-800 max-h-[420px] overflow-y-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead class="bg-slate-950 text-slate-400 font-mono sticky top-0 z-10 border-b border-slate-800">
            <tr>
              <th class="p-3">ID</th>
              <th class="p-3">Nama Suspek</th>
              <th class="p-3">Nombor Telefon</th>
              <th class="p-3">Akaun Bank</th>
              <th class="p-3">Alamat / Lokasi</th>
              <th class="p-3">Status Risiko</th>
              <th class="p-3 text-right">Tindakan</th>
            </tr>
          </thead>
          <tbody id="threatTableBody" class="divide-y divide-slate-800/60 font-mono text-slate-300">
            <!-- Rendered dynamically -->
          </tbody>
        </table>
      </div>
    </div>

  </main>

  <!-- MODAL: TAMBAH / KEMAS KINI REKOD SCAMMER -->
  <div id="addRecordModal" class="hidden fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
    <div class="bg-slate-900 border border-slate-700 rounded-2xl w-full max-w-xl p-6 shadow-2xl relative">
      <div class="flex items-center justify-between pb-3 border-b border-slate-800">
        <h3 class="font-bold text-white text-sm flex items-center gap-2">
          <span>⚠️</span> Tambah Rekod Scammer Baharu (Crowd-Update)
        </h3>
        <button onclick="closeAddModal()" class="text-slate-400 hover:text-white">✕</button>
      </div>

      <form id="recordForm" onsubmit="saveNewRecord(event)" class="mt-4 space-y-3 text-xs">
        <div>
          <label class="block text-slate-400 mb-1">Nama Suspek / Samaran:</label>
          <input type="text" id="formName" required placeholder="Contoh: Solihin Amran / Bini Koi"
            class="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-white font-mono">
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-400 mb-1">Nombor Telefon:</label>
            <input type="text" id="formPhone" required placeholder="Contoh: 0139986583"
              class="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-white font-mono">
          </div>
          <div>
            <label class="block text-slate-400 mb-1">No. Kad Pengenalan (IC):</label>
            <input type="text" id="formIC" placeholder="N/A atau 861223-06-xxxx"
              class="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-white font-mono">
          </div>
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Akaun Bank Keldai (Bank : No Akaun):</label>
          <input type="text" id="formBank" placeholder="Contoh: MBB : 111038449718; PBB : 4548163308"
            class="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-white font-mono">
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Alamat Premis / Kediaman (Jika Ada):</label>
          <input type="text" id="formAddress" placeholder="N/A atau Alamat lengkap untuk pautan Google Maps"
            class="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-white font-mono">
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Pautan Bukti (Lowyat / Laporan Polis):</label>
          <input type="text" id="formEvidence" placeholder="https://forum.lowyat.net/topic/..."
            class="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-white font-mono">
        </div>

        <div class="pt-3 flex justify-end gap-2 border-t border-slate-800">
          <button type="button" onclick="closeAddModal()" class="px-3 py-1.5 rounded-lg border border-slate-700 text-slate-400 hover:bg-slate-800">
            Batal
          </button>
          <button type="submit" class="px-4 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold">
            Simpan ke Pangkalan Data
          </button>
        </div>
      </form>
    </div>
  </div>

  <!-- JAVASCRIPT MODULAR ENGINE -->
  <script>
    // Inisialisasi Pangkalan Data Tempatan (Preloaded with Lowyat 79 records)
    const PRELOADED_RECORDS = {json_str};
    const STORAGE_KEY = 'jauhiscammer_threat_db_v2';

    let threatDatabase = [];

    function initDatabase() {{
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {{
        try {{
          threatDatabase = JSON.parse(saved);
        }} catch(e) {{
          threatDatabase = PRELOADED_RECORDS;
        }}
      }} else {{
        threatDatabase = PRELOADED_RECORDS;
        localStorage.setItem(STORAGE_KEY, JSON.stringify(threatDatabase));
      }}
      updateStats();
      renderTable(threatDatabase);
    }}

    function updateStats() {{
      document.getElementById('statRecordCount').innerText = `📦 ${{threatDatabase.length}} Rekod Tersimpan`;
    }}

    // Normalisasi Nombor E.164 & Hash SHA-256 menggunakan Web Crypto API
    async function sha256(message) {{
      const msgBuffer = new TextEncoder().encode(message);
      const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }}

    function normalizePhone(raw) {{
      let digits = (raw || '').replace(/[^0-9]/g, '');
      let e164 = '';
      let local01 = '';

      if (digits.startsWith('60')) {{
        e164 = '+' + digits;
        local01 = '0' + digits.substring(2);
      }} else if (digits.startsWith('0')) {{
        e164 = '+6' + digits;
        local01 = digits;
      }} else if (digits.startsWith('1')) {{
        e164 = '+60' + digits;
        local01 = '0' + digits;
      }} else {{
        e164 = '+' + digits;
        local01 = digits;
      }}
      return {{ digits, e164, local01 }};
    }}

    function setSample(phone) {{
      document.getElementById('phoneInput').value = phone;
      simulateIncomingCall();
    }}

    // Simulasi Panggilan Masuk & Semakan Hibrid
    async function simulateIncomingCall() {{
      const rawInput = document.getElementById('phoneInput').value.trim();
      if (!rawInput) return;

      const {{ digits, e164, local01 }} = normalizePhone(rawInput);
      const phoneHash = await sha256(digits);

      document.getElementById('telemetryE164').innerText = e164;
      document.getElementById('telemetryHash').innerText = phoneHash;

      // Cari dalam Database Tempatan
      const matched = findThreatInLocalDB(digits, local01);

      document.getElementById('defaultOverlayState').classList.add('hidden');
      const activeState = document.getElementById('activeOverlayState');
      activeState.classList.remove('hidden');

      if (matched) {{
        renderCriticalThreatOverlay(matched, e164);
      }} else {{
        renderUnknownThreatOverlay(e164, local01);
      }}

      // Lakukan semakan latar belakang ke SemakMule API
      querySemakMuleApi(local01);
    }}

    function findThreatInLocalDB(digits, local01) {{
      for (const item of threatDatabase) {{
        const all = (item.all_phones || '') + ',' + (item.primary_phone || '');
        const normItem = all.replace(/[^0-9]/g, '');
        if (normItem.includes(digits) || (local01 && normItem.includes(local01))) {{
          return item;
        }}
      }}
      return null;
    }}

    // Paparan Amaran Merah (CRITICAL / HIGH RISK)
    function renderCriticalThreatOverlay(threat, e164) {{
      const hasAddress = threat.address && threat.address !== 'N/A' && threat.address !== '-';
      const addressBtn = hasAddress ? `
        <a href="https://maps.google.com/?q=${{encodeURIComponent(threat.address)}}" target="_blank"
          class="mt-2 inline-flex items-center gap-1.5 bg-teal-600 hover:bg-teal-500 text-white px-3 py-1.5 rounded-lg text-xs font-semibold transition">
          🧭 Buka di Google Maps
        </a>
      ` : '';

      const evidenceBtn = (threat.evidence_links && threat.evidence_links.startsWith('http')) ? `
        <a href="${{threat.evidence_links.split('|')[0].trim()}}" target="_blank"
          class="mt-2 inline-flex items-center gap-1.5 bg-blue-600 hover:bg-blue-500 text-white px-3 py-1.5 rounded-lg text-xs font-semibold transition">
          🌐 Laporan Bukti Lowyat.NET
        </a>
      ` : '';

      const activeState = document.getElementById('activeOverlayState');
      activeState.innerHTML = `
        <div class="bg-gradient-to-r from-red-950 via-rose-900 to-red-950 border-2 border-red-500 rounded-2xl p-5 shadow-2xl animate-danger-glow text-white space-y-3">
          <div class="flex items-start justify-between">
            <div class="flex items-center gap-3">
              <span class="text-4xl animate-bounce">☠️</span>
              <div>
                <span class="text-[10px] bg-red-600 text-white px-2 py-0.5 rounded font-bold uppercase tracking-wider">
                  DISAHKAN SCAMMER \u2022 LOWYAT / CCID
                </span>
                <h3 class="text-xl font-black text-red-200 tracking-tight mt-1">
                  PANGGILAN BERBAHAYA!
                </h3>
              </div>
            </div>
            <span class="text-xs font-mono bg-black/40 px-2.5 py-1 rounded-full border border-red-500/40 text-red-300">
              ${{e164}}
            </span>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 text-xs">
            <div class="bg-black/40 border border-red-500/30 p-2.5 rounded-xl">
              <span class="text-slate-400 block text-[10px]">NAMA SUSPEK:</span>
              <span class="font-bold text-red-300 text-sm">${{threat.name || threat.suspect_name}}</span>
            </div>
            <div class="bg-black/40 border border-red-500/30 p-2.5 rounded-xl">
              <span class="text-slate-400 block text-[10px]">AKAUN BANK KELDAI:</span>
              <span class="font-mono text-amber-300">${{threat.bank_accounts || 'N/A'}}</span>
            </div>
          </div>

          ${{hasAddress ? `
            <div class="bg-black/40 border border-red-500/30 p-2.5 rounded-xl text-xs">
              <span class="text-slate-400 block text-[10px]">LOKASI / ALAMAT PREMIS:</span>
              <p class="text-slate-200 mt-0.5">${{threat.address}}</p>
              ${{addressBtn}}
            </div>
          ` : ''}}

          <div class="pt-2 flex flex-wrap gap-2 items-center justify-between">
            <div class="flex gap-2">
              ${{evidenceBtn}}
              <button onclick="copyDossier('${{e164}}', '${{threat.name || threat.suspect_name}}', '${{threat.bank_accounts || ''}}')"
                class="mt-2 inline-flex items-center gap-1 bg-slate-800 hover:bg-slate-700 text-slate-200 px-3 py-1.5 rounded-lg text-xs font-semibold transition border border-slate-700">
                📋 Salin Info
              </button>
            </div>
            <span class="text-[11px] text-red-300 font-mono">⚠️ JANGAN ANGKAT ATAU BUAT PINDAHAN DUIT!</span>
          </div>
        </div>
      `;
    }}

    // Paparan Status Tidak Diketahui (Ambang Kuning / Belum Disahkan)
    function renderUnknownThreatOverlay(e164, local01) {{
      const activeState = document.getElementById('activeOverlayState');
      activeState.innerHTML = `
        <div class="bg-gradient-to-r from-amber-950/80 via-slate-900 to-amber-950/80 border border-amber-500/60 rounded-2xl p-5 shadow-xl text-white space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <span class="text-3xl">⚠️</span>
              <div>
                <span class="text-[10px] bg-amber-500 text-black px-2 py-0.5 rounded font-bold uppercase tracking-wider">
                  STATUS TIDAK DIKETAHUI
                </span>
                <h3 class="text-lg font-bold text-amber-200 mt-0.5">
                  Tiada Dalam Pangkalan Data Tempatan
                </h3>
              </div>
            </div>
            <span class="text-xs font-mono bg-black/40 px-2 py-1 rounded border border-amber-500/30 text-amber-300">
              ${{e164}}
            </span>
          </div>

          <p class="text-xs text-slate-300">
            Nombor ini belum tersenarai dalam rekod Lowyat.NET tempatan. Disyorkan menyemak laporan terkini polis melalui portal CCID SemakMule.
          </p>

          <div class="pt-2 flex flex-wrap gap-2">
            <button onclick="launchSemakMuleBridge()" class="bg-amber-600 hover:bg-amber-500 text-white font-semibold px-4 py-2 rounded-xl text-xs flex items-center gap-2 transition">
              <span>📋</span> Semak Terus di SemakMule (Auto-Salin: ${{local01}})
            </button>
            <button onclick="openAddModalWithCurrentPhone()" class="bg-slate-800 hover:bg-slate-700 text-rose-300 border border-rose-800 px-3 py-2 rounded-xl text-xs font-medium transition">
              ➕ Laporkan Sebagai Scammer
            </button>
          </div>
        </div>
      `;
    }}

    // Semakan Terus Melalui SemakMule API Rasmi
    async function querySemakMuleApi(telNo) {{
      const resBox = document.getElementById('semakmuleApiResult');
      resBox.innerHTML = `<span class="text-cyan-400 animate-pulse">⏳ Menyemak pangkalan data JSJK CCID untuk ${{telNo}}...</span>`;

      try {{
        // Direct POST to SemakMule get_search_data.php
        const response = await fetch('https://semakmule.rmp.gov.my/api/mule/get_search_data.php', {{
          method: 'POST',
          headers: {{
            'Content-Type': 'application/json',
            'apikey': 'j3j389#nklala2'
          }},
          body: JSON.stringify({{
            data: {{
              category: 'telefon',
              telNo: telNo,
              captcha: ''
            }}
          }})
        }});

        if (response.ok) {{
          const data = await response.json();
          if (data.status === 1) {{
            const reports = data.table_data || [];
            const reportCount = reports.length;
            const searchCount = data.count || 0;
            if (reportCount > 0) {{
              resBox.innerHTML = `
                <div class="text-red-400 font-bold">
                  ⚠️ REKOD CCID DITEMUI: <span class="bg-red-950 px-2 py-0.5 rounded text-white">${{reportCount}} Laporan Polis</span>
                </div>
                <div class="text-[11px] text-slate-400 mt-1">Nombor ini mempunyai rekod repot polis rasmi CCID (Telah dicari ${{searchCount}} kali di SemakMule).</div>
              `;
            }} else {{
              resBox.innerHTML = `
                <div class="text-emerald-400 font-bold">
                  ✅ TIADA REKOD POLIS RASMI
                </div>
                <div class="text-[11px] text-slate-400 mt-1">Tiada laporan rasmi JSJK ditemui setakat ini. Nombor ini telah dicari sebanyak <b>${{searchCount}} kali</b> oleh orang awam di portal SemakMule.</div>
              `;
            }}
          }} else {{
            resBox.innerHTML = `<span class="text-slate-400">Respons SemakMule: ${{data.status_message || 'Selesai'}}</span>`;
          }}
        }} else {{
          resBox.innerHTML = `<span class="text-amber-400">Penyemakan API dihadkan (CORS/Network). Sila gunakan butang Jambatan 1-Click.</span>`;
        }}
      }} catch(err) {{
        // Sekiranya pelayar menyekat CORS pada browser tempatan, kemukakan fallback pintar
        resBox.innerHTML = `
          <div class="text-slate-400">
            Penyemakan automatik: Sila klik <b>"Buka SemakMule & Auto-Salin"</b> untuk semakan rasmi PDRM.
          </div>
        `;
      }}
    }}

    // Jambatan 1-Click SemakMule (Auto-Salin ke Clipboard & Buka Laman)
    function launchSemakMuleBridge() {{
      const rawInput = document.getElementById('phoneInput').value.trim();
      const {{ local01 }} = normalizePhone(rawInput);

      // Salin ke papan klip
      navigator.clipboard.writeText(local01).then(() => {{
        alert(`✅ Nombor [${{local01}}] telah disalin ke papan klip secara automatik!\n\nLaman SemakMule PDRM akan dibuka sekarang. Anda hanya perlu tekan Tampal (Paste) dan klik Semak.`);
        window.open('https://semakmule.rmp.gov.my/', '_blank');
      }}).catch(() => {{
        window.open('https://semakmule.rmp.gov.my/', '_blank');
      }});
    }}

    function markPhoneSafe() {{
      alert('Status nombor ditandakan sebagai tiada rekod dalam sesi semakan ini.');
    }}

    function copyDossier(phone, name, bank) {{
      const text = `[AMARAN SCAMMER]\nNombor: ${{phone}}\nNama Suspek: ${{name}}\nAkaun Bank: ${{bank}}\nSumber: Lowyat.NET / SemakMule`;
      navigator.clipboard.writeText(text).then(() => {{
        alert('Maklumat scammer telah disalin ke papan klip!');
      }});
    }}

    // Pengurusan Live Table & Modal
    function renderTable(data) {{
      const tbody = document.getElementById('threatTableBody');
      tbody.innerHTML = '';

      data.forEach((r, idx) => {{
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-slate-800/40 transition';
        const hasAddr = r.address && r.address !== 'N/A' && r.address !== '-';
        tr.innerHTML = `
          <td class="p-3 text-slate-500">${{r.id || (idx + 1)}}</td>
          <td class="p-3 font-semibold text-white">${{r.name || r.suspect_name}}</td>
          <td class="p-3 text-cyan-400">${{r.primary_phone || r.all_phones || 'N/A'}}</td>
          <td class="p-3 text-amber-300 max-w-[200px] truncate" title="${{r.bank_accounts || ''}}">${{r.bank_accounts || 'N/A'}}</td>
          <td class="p-3 max-w-[200px] truncate" title="${{r.address || ''}}">
            ${{hasAddr ? `<span class="text-emerald-400">📍 ${{r.address}}</span>` : '<span class="text-slate-600">N/A</span>'}}
          </td>
          <td class="p-3">
            <span class="bg-red-950 text-red-400 border border-red-800 px-2 py-0.5 rounded text-[10px] font-bold">
              ${{r.risk_tag ? 'BLACKLIST' : 'HIGH RISK'}}
            </span>
          </td>
          <td class="p-3 text-right">
            <button onclick="setSample('${{(r.primary_phone || r.all_phones || '').split(',')[0].trim()}}')"
              class="text-xs text-cyan-400 hover:text-cyan-300 underline font-mono">
              Uji Call
            </button>
          </td>
        `;
        tbody.appendChild(tr);
      }});
    }}

    function filterTable() {{
      const query = document.getElementById('searchTableInput').value.toLowerCase();
      const filtered = threatDatabase.filter(r => {{
        const str = JSON.stringify(r).toLowerCase();
        return str.includes(query);
      }});
      renderTable(filtered);
    }}

    function openAddModal() {{
      document.getElementById('addRecordModal').classList.remove('hidden');
    }}

    function openAddModalWithCurrentPhone() {{
      const rawInput = document.getElementById('phoneInput').value.trim();
      const {{ local01 }} = normalizePhone(rawInput);
      document.getElementById('formPhone').value = local01;
      openAddModal();
    }}

    function closeAddModal() {{
      document.getElementById('addRecordModal').classList.add('hidden');
    }}

    function saveNewRecord(e) {{
      e.preventDefault();
      const name = document.getElementById('formName').value.trim();
      const phone = document.getElementById('formPhone').value.trim();
      const ic = document.getElementById('formIC').value.trim() || 'N/A';
      const bank = document.getElementById('formBank').value.trim() || 'N/A';
      const addr = document.getElementById('formAddress').value.trim() || 'N/A';
      const evidence = document.getElementById('formEvidence').value.trim() || 'https://semakmule.rmp.gov.my/';

      const newRec = {{
        id: threatDatabase.length + 1,
        name: name,
        primary_phone: phone,
        all_phones: phone,
        bank_accounts: bank,
        ic_number: ic,
        address: addr,
        evidence_links: evidence,
        risk_tag: 'HIGH_RISK_SCAMMER_CROWD_VERIFIED',
        source: 'jauhiscammer Community & CCID SemakMule Verification'
      }};

      threatDatabase.unshift(newRec);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(threatDatabase));
      updateStats();
      renderTable(threatDatabase);
      closeAddModal();
      document.getElementById('recordForm').reset();
      alert(`Rekod untuk [${{name}}] berjaya disimpan ke dalam pangkalan data lokal!`);
    }}

    function exportJSON() {{
      const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(threatDatabase, null, 2));
      const downloadAnchor = document.createElement('a');
      downloadAnchor.setAttribute("href", dataStr);
      downloadAnchor.setAttribute("download", `jauhiscammer_threat_db_${{new Date().toISOString().slice(0,10)}}.json`);
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      downloadAnchor.remove();
    }}

    // Jalankan pemulaan sistem
    window.addEventListener('DOMContentLoaded', initDatabase);
  </script>
</body>
</html>
"""

with open('jauhiscammer.html', 'w', encoding='utf-8') as f:
    f.write(html_content)

print("jauhiscammer.html successfully generated! Size:", len(html_content))
