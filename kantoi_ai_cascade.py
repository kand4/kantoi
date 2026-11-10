# -*- coding: utf-8 -*-
"""
KANTOI MULTI-PROVIDER AI CASCADE ENGINE (Fasa 3 / v3.1)
======================================================
Sistem Pintar Penukaran AI Berbilang Penyedia (Failover Cascade):
1. Keutamaan 1: Groq Cloud (Sepantas Kilat ~0.5s, LPU Inference)
   - Model: openai/gpt-oss-120b, qwen/qwen3.8-27b, allam-2-7b
2. Keutamaan 2: Google Gemini (Multimodal Vision & Konteks Mendalam)
   - Model: gemini-3.8-flash, gemini-flash-latest, gemini-3.8-flash-lite
3. Keutamaan 3 (Sandaran Kecemasan): Enjin Heuristik Tempatan KANTOI (100% Offline)
"""

import os
import sys
import time
import json
import re
import requests

# Muat turun kunci rahsia secara selamat tanpa mencetuskan ralat jika salah satu pembolehubah tiada
try:
    import local_secrets
    GROQ_API_KEY = getattr(local_secrets, "GROQ_API_KEY", os.environ.get("GROQ_API_KEY", ""))
    GEMINI_API_KEY = getattr(local_secrets, "GEMINI_API_KEY", os.environ.get("GEMINI_API_KEY", ""))
except ImportError:
    GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")
    GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
except Exception:
    GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")
    GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

# Senarai Model SOTA Terkini (Mengelakkan Model Lapuk)
AVAILABLE_GROQ_MODELS = [
    "openai/gpt-oss-120b",
    "qwen/qwen3.8-27b",
    "allam-2-7b"
]

AVAILABLE_GEMINI_MODELS = [
    "gemini-3.8-flash",
    "gemini-flash-latest",
    "gemini-3.8-flash-lite",
    "gemini-2.5-flash-lite"
]


def get_active_groq_key() -> str:
    """Mendapatkan kunci Groq daripada local_secrets, global atau os.environ"""
    k = GROQ_API_KEY or getattr(sys.modules.get('local_secrets'), 'GROQ_API_KEY', '') or os.environ.get("GROQ_API_KEY", "")
    return str(k).strip(" \t\r\n\'\"")


def get_active_gemini_key() -> str:
    """Mendapatkan kunci Gemini daripada local_secrets, global atau os.environ"""
    k = GEMINI_API_KEY or getattr(sys.modules.get('local_secrets'), 'GEMINI_API_KEY', '') or os.environ.get("GEMINI_API_KEY", "")
    return str(k).strip(" \t\r\n\'\"")


def query_ai_cascade(prompt: str, system_prompt: str = "", max_tokens: int = 800) -> dict:
    """
    Menghantar prompt merentasi rantaian AI SOTA mengikut keutamaan:
    Groq Cloud -> Google Gemini -> Enjin Tempatan KANTOI.
    """
    sys_instruction = system_prompt or (
        "Anda adalah KANTOI Cyber Intel AI, pakar forensik dan pencegahan penipuan kewangan/siber di Malaysia. "
        "Beri jawapan yang padat, berwibawa, profesional dalam Bahasa Melayu, dan berikan nasihat perlindungan praktikal."
    )

    groq_k = get_active_groq_key()
    gemini_k = get_active_gemini_key()

    # -------------------------------------------------------------------------
    # 1. TIER 1: GROQ CLOUD (Paling Pantas, LPU Architecture)
    # -------------------------------------------------------------------------
    if groq_k and groq_k.startswith("gsk_"):
        headers = {
            "Authorization": f"Bearer {groq_k}",
            "Content-Type": "application/json"
        }
        for g_model in AVAILABLE_GROQ_MODELS:
            start_t = time.time()
            try:
                payload = {
                    "model": g_model,
                    "messages": [
                        {"role": "system", "content": sys_instruction},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.2,
                    "max_tokens": max_tokens
                }
                r = requests.post(
                    "https://api.groq.com/openai/v1/chat/completions",
                    headers=headers,
                    json=payload,
                    timeout=9
                )
                if r.status_code == 200:
                    data = r.json()
                    ans = data["choices"][0]["message"]["content"].strip()
                    elapsed_ms = int((time.time() - start_t) * 1000)
                    return {
                        "status": "success",
                        "provider": "Groq Cloud (Ultra-Fast)",
                        "model": g_model,
                        "text": ans,
                        "latency_ms": elapsed_ms
                    }
                elif r.status_code in [404, 400]:
                    # Model mungkin ditukar nama, cuba model seterusnya
                    continue
                elif r.status_code == 429:
                    # Rate limit, failover ke Gemini segera
                    break
            except Exception as e:
                print(f"[GROQ CASCADE WARN on {g_model}] {e}")
                continue

    # -------------------------------------------------------------------------
    # 2. TIER 2: GOOGLE GEMINI (Multimodal & Deep Context)
    # -------------------------------------------------------------------------
    if gemini_k and len(gemini_k) >= 15:
        headers = {"Content-Type": "application/json"}
        for gem_model in AVAILABLE_GEMINI_MODELS:
            start_t = time.time()
            try:
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{gem_model}:generateContent?key={gemini_k}"
                payload = {
                    "contents": [{
                        "parts": [{"text": f"{sys_instruction}\n\n[ARAHAN PENGGUNA]:\n{prompt}"}]
                    }],
                    "generationConfig": {
                        "temperature": 0.2,
                        "maxOutputTokens": max_tokens
                    }
                }
                r = requests.post(url, headers=headers, json=payload, timeout=12)
                if r.status_code == 200:
                    data = r.json()
                    candidates = data.get("candidates", [])
                    if candidates:
                        ans = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "").strip()
                        elapsed_ms = int((time.time() - start_t) * 1000)
                        return {
                            "status": "success",
                            "provider": "Google Gemini",
                            "model": gem_model,
                            "text": ans,
                            "latency_ms": elapsed_ms
                        }
                elif r.status_code in [404, 400]:
                    continue
                elif r.status_code in [429, 503]:
                    # Beban tinggi / rate limit, cuba model lite
                    continue
            except Exception as e:
                print(f"[GEMINI CASCADE WARN on {gem_model}] {e}")
                continue

    # -------------------------------------------------------------------------
    # 3. TIER 3: ENJIN HEURISTIK TEMPATAN (100% Offline Fallback)
    # -------------------------------------------------------------------------
    return {
        "status": "fallback_offline",
        "provider": "KANTOI Local Heuristics",
        "model": "Offline Anti-Scam Ruleset",
        "text": (
            "⚠️ <b>Analisis Keselamatan Siber Tempatan (Mod Luar Talian):</b>\n\n"
            "1. <b>Prinsip Asas:</b> Pihak berkuasa rasmi (PDRM, LHDN, Mahkamah, Bank Negara Malaysia) "
            "TIDAK SEKALI-KALI akan meminta pemindahan wang ke akaun peribadi/individu melalui panggilan telefon.\n"
            "2. <b>Semakan Pantas:</b> Sentiasa semak nombor telefon dan nombor akaun bank di portal rasmi PDRM SemakMule atau gunakan carian bot ini.\n"
            "3. <b>Tindakan Segera:</b> Jika anda telah memindahkan wang, hubungi <b>Pusat Respons Scam Kebangsaan (NSRC) di talian 997</b> dalam tempoh 24 jam untuk pembekuan akaun suspek."
        ),
        "latency_ms": 0
    }


def analyze_scam_inquiry(user_inquiry: str) -> dict:
    """
    Menganalisis pertanyaan atau aduan situasi scammer daripada pengguna.
    """
    prompt = (
        f"Pengguna sedang menghadapi situasi ini:\n\"{user_inquiry}\"\n\n"
        "Sila buat penilaian keselamatan siber pantas:\n"
        "1. Kategori Modus Operandi (cth: Macau Scam / Phone Impersonation, Pelaburan Palsu, Tipu Beli Barang, dsb.)\n"
        "2. Tahap Risiko (KRITIKAL / TINGGI / SEDERHANA / RENDAH)\n"
        "3. Tanda-Tanda Amaran Merah (Red Flags) yang dikesan\n"
        "4. Nasihat Tindakan Pantas (Langkah yang perlu diambil sekarang juga)\n\n"
        "Formatkan jawapan dalam susunan yang kemas dan mudah dibaca melalui telefon pintar."
    )
    return query_ai_cascade(prompt)
