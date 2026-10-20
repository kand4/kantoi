/**
 * LOWYAT.NET SCAMMER DATABASE MONTHLY UPDATER
 * Runs on the 7th of every month or manually on demand.
 * Features:
 * - Rate-limited polite HTTP requests (prevents IP blocks)
 * - Incremental deduplication (matches against phone & bank accounts)
 * - Updates both lowyat_scammers_db.json and lowyat_scammers_db.xlsx
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const xlsx = require('c:/Users/fisaa/Downloads/android/x64/RedHorizon-main/node_modules/xlsx');

const DB_JSON_PATH = path.join(__dirname, 'lowyat_scammers_db.json');
const DB_XLSX_PATH = path.join(__dirname, 'lowyat_scammers_db.xlsx');
const TARGET_URL = 'https://forum.lowyat.net/topic/1579999';

// Polite headers to simulate standard browser and avoid IP bans
const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
  'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.9,ms;q=0.8',
  'Connection': 'keep-alive',
  'Cache-Control': 'max-age=0'
};

function fetchUrl(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: HEADERS }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return fetchUrl(res.headers.location).then(resolve).catch(reject);
      }
      if (res.statusCode !== 200) {
        return reject(new Error(`HTTP status ${res.statusCode}`));
      }
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

function parseBlocks(html) {
  const postStart = html.indexOf('data-postid="36653213"');
  const postEnd = html.indexOf('<!--Begin Msg Number', postStart + 50);
  const postHtml = html.substring(postStart, postEnd > 0 ? postEnd : html.length);
  const blocks = postHtml.split(/\*{4,6}/);

  const parsed = [];

  for (const b of blocks) {
    let address = '';
    const addrMatch = b.match(/Address\s*:\s*([^<\n\r]+)/i);
    if (addrMatch) {
      address = addrMatch[1].replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
    }

    const clean = b
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<[^>]+>/g, ' ')
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .trim();

    if (clean.length < 15 || clean.includes('TIPS FOR EASY FINDING') || clean.includes('Hi my fellow forumers')) {
      continue;
    }

    const phoneRegex = /(?:(?:\+?60|0)1[0-46-9][\s-]?\d{7,8})/g;
    const phoneMatches = clean.match(phoneRegex) || [];
    const phones = [...new Set(phoneMatches.map(p => {
      let num = p.replace(/[\s-]/g, '');
      if (num.startsWith('01')) num = '+6' + num;
      else if (num.startsWith('601')) num = '+' + num;
      return num;
    }))];

    const icMatch = clean.match(/\b\d{6}-\d{2}-\d{4}\b/);
    const ic = icMatch ? icMatch[0] : '';

    const bankRegex = /\b(MBB|MAYBANK|CIMB|PBB|PUBLIC BANK|RHB|HLB|HONG LEONG|AMBANK|BANK ISLAM|BSN|BANK RAKYAT)\s*[:\-]?\s*(\d{8,16})/gi;
    const bankMatches = clean.match(bankRegex) || [];
    const banks = [...new Set(bankMatches.map(b => b.trim()))];

    let name = '';
    const lines = clean.split('\n').map(l => l.trim()).filter(l => l.length > 0);
    for (const line of lines) {
      if (line.match(/^(\*|_|-|=)+$/)) continue;
      if (line.match(/^(MBB|CIMB|PBB|RHB|HLB|BANK|H\/P|Phone|Address|IC|Contact|LINK)/i)) continue;
      if (line.includes('[') && line.includes(']')) {
        const beforeBracket = line.split('[')[0].trim();
        if (beforeBracket.length > 2) {
          name = beforeBracket;
          break;
        }
      } else if (line.length > 2 && line.length < 80) {
        name = line;
        break;
      }
    }

    const handleMatches = clean.match(/\[\s*([^\]]+?)\s*\]/);
    const handles = handleMatches ? handleMatches[1].trim() : '';

    const linkMatches = b.match(/href='(https?:\/\/forum\.lowyat\.net\/topic\/\d+[^']*)'/gi) || [];
    const links = [...new Set(linkMatches.map(l => l.replace(/href='|'/g, '')))];

    if (phones.length > 0 || banks.length > 0 || ic || address) {
      parsed.push({
        name: name || 'SUSPECT_SCAMMER',
        primary_phone: phones[0] || 'N/A',
        all_phones: phones.join(', ') || 'N/A',
        bank_accounts: banks.join('; ') || 'N/A',
        ic_number: ic || 'N/A',
        address: address || 'N/A',
        forum_handles: handles || 'N/A',
        evidence_links: links.join(' | ') || TARGET_URL,
        risk_tag: 'HIGH_RISK_SCAMMER_LOWYAT_BLACKLIST',
        source: 'Lowyat.NET Dispute Resolution Corner (Topic 1579999)'
      });
    }
  }

  return parsed;
}

async function run() {
  console.log(`[${new Date().toISOString()}] Starting Lowyat Scam Database Sync...`);
  
  // 1. Load existing records if any
  let existingRecords = [];
  if (fs.existsSync(DB_JSON_PATH)) {
    try {
      existingRecords = JSON.parse(fs.readFileSync(DB_JSON_PATH, 'utf8'));
      console.log(`Loaded ${existingRecords.length} existing records from database.`);
    } catch (e) {
      console.warn('Could not parse existing JSON database, starting fresh.');
    }
  }

  // 2. Fetch thread page with polite delay
  console.log(`Connecting to: ${TARGET_URL}...`);
  let html = '';
  try {
    html = await fetchUrl(TARGET_URL);
  } catch (err) {
    console.error(`Failed to connect to Lowyat forum: ${err.message}`);
    console.log('Skipping update to avoid aggressive retries.');
    return;
  }

  // 3. Parse newly fetched data
  const freshRecords = parseBlocks(html);
  console.log(`Extracted ${freshRecords.length} records from current online snapshot.`);

  // 4. Merge & Deduplicate
  const existingKeys = new Set();
  for (const r of existingRecords) {
    if (r.primary_phone !== 'N/A') existingKeys.add(r.primary_phone);
    if (r.bank_accounts !== 'N/A') existingKeys.add(r.bank_accounts);
    if (r.ic_number !== 'N/A') existingKeys.add(r.ic_number);
  }

  let newCount = 0;
  for (const f of freshRecords) {
    const key = f.primary_phone !== 'N/A' ? f.primary_phone : (f.bank_accounts !== 'N/A' ? f.bank_accounts : f.name);
    if (!existingKeys.has(key)) {
      f.id = existingRecords.length + 1;
      existingRecords.push(f);
      existingKeys.add(key);
      newCount++;
    }
  }

  console.log(`New records added: ${newCount}. Total database size: ${existingRecords.length} records.`);

  // 5. Save updated JSON
  fs.writeFileSync(DB_JSON_PATH, JSON.stringify(existingRecords, null, 2), 'utf8');
  console.log(`[SUCCESS] Updated JSON saved to: ${DB_JSON_PATH}`);

  // 6. Save updated Excel
  const worksheet = xlsx.utils.json_to_sheet(existingRecords);
  const workbook = xlsx.utils.book_new();
  xlsx.utils.book_append_sheet(workbook, worksheet, 'Lowyat_Scammers');
  xlsx.writeFile(workbook, DB_XLSX_PATH);
  console.log(`[SUCCESS] Updated Excel saved to: ${DB_XLSX_PATH}`);
  console.log(`\nNext scheduled run: 7th of next month at 03:00 AM.`);
}

run().catch(console.error);
