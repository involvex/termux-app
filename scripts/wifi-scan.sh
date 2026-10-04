#!/usr/bin/env bash
# invapp-widget: wifi-scan
# Passive nearby Wi-Fi scanner (no root, no monitor mode, no capture/crack).
# Uses termux-wifi-scaninfo + termux-wifi-connectioninfo. Audit own/authorized nets only.
# Needs: Termux:API APK + pkg install termux-api + Location ON + location permission.
# Android throttles scans (4 per 2 min); results may be cached - this is normal.
#
# Install on device (no APK rebuild needed):
#   cp scripts/wifi-scan.sh ~/.shortcuts/wifi-scan && chmod +x ~/.shortcuts/wifi-scan
#   bash ~/.shortcuts/wifi-scan
# Workaround if the widget entry misbehaves: keep the script under ~/repos and
# point the shortcut at it, plus an optional shell alias:
#   cp scripts/wifi-scan.sh ~/repos/wifi-scan.sh
#   printf 'sh ~/repos/wifi-scan.sh\n' > ~/.shortcuts/wifi-scan && chmod +x ~/.shortcuts/wifi-scan
#   alias wifiscan="sh ~/repos/wifi-scan.sh"
set -e
export PATH="$PREFIX/bin:$PATH"
toast() { command -v termux-toast >/dev/null 2>&1 && termux-toast "$1" || true; }
need_api() {
  if ! command -v "$1" >/dev/null 2>&1; then
    msg='Need Termux:API APK + pkg install termux-api'
    toast "$msg"; echo "$msg" >&2
    exit 1
  fi
}
need_api termux-wifi-scaninfo
need_api termux-wifi-connectioninfo
OUTDIR="$HOME/repos/wifi-scan"
CACHE="$HOME/.cache/invx-wifi-scan"
mkdir -p "$OUTDIR" "$CACHE"
TS="$(date +%Y%m%d-%H%M%S)"
SCAN_F="$CACHE/scan-last.json"
CONN_F="$CACHE/conn-last.json"
SUM_F="$CACHE/last-summary.txt"
toast 'Scanning Wi-Fi…'
termux-wifi-scaninfo > "$SCAN_F" 2>/dev/null || true
termux-wifi-connectioninfo > "$CONN_F" 2>/dev/null || true
if [ ! -s "$SCAN_F" ]; then toast 'No scan data (enable Location)'; echo 'No scan data - enable Location + grant permission' >&2; exit 1; fi
if grep -q 'API_ERROR' "$SCAN_F"; then toast 'Enable Location + grant permission'; cat "$SCAN_F" >&2; exit 1; fi
if ! command -v python3 >/dev/null 2>&1; then cp "$SCAN_F" "$OUTDIR/scan-$TS.json"; toast 'Saved raw JSON (pkg install python for table)'; cat "$SCAN_F"; exit 0; fi
python3 - "$SCAN_F" "$CONN_F" "$OUTDIR" "$CACHE" "$TS" <<'PYEOF'
import sys, json, os, html, csv, collections
scan_f, conn_f, outdir, cache, ts = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
def load_json(p, default):
    try:
        with open(p, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return default
scans = load_json(scan_f, [])
conn = load_json(conn_f, {})
if isinstance(scans, dict):
    scans = []
connected = ''
try:
    connected = str((conn or {}).get('bssid') or '').upper()
except Exception:
    connected = ''
OUI = {'00:1A:11': 'Google', '3C:22:FB': 'Apple', 'F0:18:98': 'Apple', '8C:85:90': 'Apple', 'D8:A0:1D': 'Apple', 'AC:DE:48': 'Apple', 'DC:A6:32': 'Raspberry Pi', 'B8:27:EB': 'Raspberry Pi', '50:C7:BF': 'TP-Link', '14:CC:20': 'TP-Link', '30:B5:C2': 'TP-Link', '9C:C7:A6': 'TP-Link', 'C0:4A:00': 'Netgear', '28:C6:8E': 'Netgear', 'A0:04:60': 'Netgear', '00:18:39': 'Cisco', '00:1B:2A': 'Cisco', '58:8D:09': 'Cisco', '78:8A:20': 'Ubiquiti', '74:83:C2': 'Ubiquiti', '24:A4:3C': 'Ubiquiti', 'E0:63:E5': 'Samsung', '78:D6:F0': 'Samsung', 'C8:14:79': 'Xiaomi', '64:CC:2E': 'Xiaomi', '34:CE:00': 'Xiaomi', '28:6C:07': 'Huawei', '48:49:5C': 'Huawei', '3C:7C:3F': 'Huawei', '70:A8:E3': 'Amazon', '44:65:0D': 'Amazon', '18:B4:30': 'Google/Nest', 'F4:F5:D8': 'Google/Nest', '00:0F:B5': 'Netgear', '10:0D:7F': 'Netgear', '2C:30:33': 'Ruckus', '38:FF:36': 'Ubiquiti', '04:18:D6': 'TP-Link', '60:E7:01': 'TP-Link'}
def vendor(bssid):
    try:
        return OUI.get(str(bssid).upper()[0:8], '?')
    except Exception:
        return '?'
def classify(cap):
    c = str(cap or '').upper()
    if 'SAE' in c or 'WPA3' in c:
        return ('WPA3', 'OK')
    if 'OWE' in c and 'WPA' not in c:
        return ('OWE', 'MEDIUM')
    if 'WEP' in c:
        return ('WEP', 'HIGH')
    if 'WPA2' in c:
        if 'WPS' in c:
            return ('WPA2+WPS', 'MEDIUM')
        return ('WPA2', 'OK')
    if 'WPA' in c:
        return ('WPA', 'MEDIUM')
    if c.strip() == '[ESS]' or c.strip() == 'ESS':
        return ('OPEN', 'HIGH')
    if 'ESS' in c and 'PRIVACY' not in c:
        return ('OPEN', 'HIGH')
    if not c.strip():
        return ('OPEN?', 'HIGH')
    return ('UNKNOWN', 'MEDIUM')
def freq_to_ch(f):
    try:
        f = int(f)
    except Exception:
        return ('?', '?')
    if f == 2484:
        return (14, '2.4')
    if 2412 <= f <= 2472:
        return ((f - 2412) // 5 + 1, '2.4')
    if 5170 <= f <= 5825:
        return ((f - 5000) // 5, '5')
    if 5955 <= f <= 7115:
        return ((f - 5950) // 5, '6')
    if 2400 <= f < 2500:
        return ('?', '2.4')
    if 5000 <= f < 6000:
        return ('?', '5')
    return ('?', '?')
def pct_of(r):
    try:
        r = int(r)
    except Exception:
        return 0
    if r >= -50:
        return 100
    if r <= -100:
        return 0
    return 2 * (r + 100)
def bars(p):
    if p >= 75:
        return '####'
    if p >= 50:
        return '###'
    if p >= 25:
        return '##'
    return '#'
rows = []
for s in scans:
    try:
        ssid = str(s.get('ssid') or '<hidden>')
        bssid = str(s.get('bssid') or '?')
        rssi = int(s.get('rssi', -100))
    except Exception:
        continue
    freq = s.get('frequency_mhz', 0)
    ch, band = freq_to_ch(freq)
    sec, risk = classify(s.get('capabilities', ''))
    p = pct_of(rssi)
    is_conn = (bssid.upper() == connected and connected != '')
    rows.append({'ssid': ssid, 'bssid': bssid, 'rssi': rssi, 'pct': p, 'bars': bars(p), 'freq': freq, 'ch': ch, 'band': band, 'sec': sec, 'risk': risk, 'vendor': vendor(bssid), 'conn': is_conn, 'caps': str(s.get('capabilities') or '')})
rows.sort(key=lambda r: r['rssi'], reverse=True)
print('Wi-Fi scan: %d networks (sorted by signal)' % len(rows))
print('------------------------------------------------------------')
print('%-24s %-17s %7s %-7s %-9s %-12s %s' % ('SSID', 'BSSID', 'dBm/%', 'Ch', 'SEC', 'VENDOR', 'FLAGS'))
for r in rows[:40]:
    ss = r['ssid'][:24]
    flag = ('*CONNECTED ' if r['conn'] else '') + r['risk']
    print('%-24s %-17s %4d/%3d %-7s %-9s %-12s %s %s' % (ss, r['bssid'], r['rssi'], r['pct'], ('%s/%s' % (r['ch'], r['band'])), r['sec'], r['vendor'][:12], r['bars'], flag))
if len(rows) > 40:
    print('... +%d more (see JSON/CSV report)' % (len(rows) - 40))
ch_count = collections.Counter(str(r['ch']) + '/' + str(r['band']) for r in rows)
open_n = sum(1 for r in rows if r['risk'] == 'HIGH')
med_n = sum(1 for r in rows if r['risk'] == 'MEDIUM')
busiest = ch_count.most_common(1)[0] if ch_count else ('-', 0)
ssid_groups = collections.defaultdict(set)
for r in rows:
    ssid_groups[r['ssid']].add(r['bssid'].upper())
dupes = sorted([k for k, v in ssid_groups.items() if len(v) > 1 and k != '<hidden>'])
print('------------------------------------------------------------')
top = rows[0] if rows else None
tops = ('%s %ddBm' % (top['ssid'][:20], top['rssi'])) if top else 'none'
print('Summary: %d nets, strongest: %s, HIGH-risk: %d, MEDIUM: %d, busiest: Ch %s (%d nets)' % (len(rows), tops, open_n, med_n, busiest[0], busiest[1]))
if dupes:
    print('Note: duplicate SSID on multiple BSSIDs (mesh or check): ' + ', '.join(dupes[:5]))
print('Advice: 2.4GHz use Ch 1/6/11; prefer WPA2/WPA3; avoid OPEN/WEP for anything sensitive.')
hist = os.path.join(cache, 'history.jsonl')
prev = None
try:
    with open(hist, 'r', encoding='utf-8') as f:
        lines = [l for l in f if l.strip()]
        if lines:
            prev = json.loads(lines[-1])
except Exception:
    prev = None
rank = {'OK': 0, 'MEDIUM': 1, 'HIGH': 2}
cur_map = {r['bssid'].upper(): r for r in rows}
if isinstance(prev, dict) and isinstance(prev.get('networks'), list):
    prev_map = {}
    for n in prev['networks']:
        try:
            prev_map[str(n.get('bssid', '')).upper()] = n
        except Exception:
            pass
    new = [b for b in cur_map if b not in prev_map]
    gone = [b for b in prev_map if b not in cur_map]
    weaker, stronger = [], []
    for b, r in cur_map.items():
        if b in prev_map:
            try:
                pr = rank.get(str(prev_map[b].get('risk', 'OK')), 0)
                cr = rank.get(r['risk'], 0)
                if cr > pr:
                    weaker.append(b)
                elif cr < pr:
                    stronger.append(b)
            except Exception:
                pass
    if new or gone or weaker or stronger:
        print('--- vs previous scan (%s) ---' % str(prev.get('ts', '?')))
        for b in new[:10]:
            print('[NEW] %s (%s)' % (cur_map[b]['ssid'][:24], b))
        for b in gone[:10]:
            print('[GONE] %s (%s)' % (str(prev_map[b].get('ssid', '?'))[:24], b))
        for b in weaker[:10]:
            print('[WEAKER] %s (%s)' % (cur_map[b]['ssid'][:24], b))
        for b in stronger[:10]:
            print('[STRONGER] %s (%s)' % (cur_map[b]['ssid'][:24], b))
base = os.path.join(outdir, 'scan-' + ts)
enriched = [{'ssid': r['ssid'], 'bssid': r['bssid'], 'rssi_dbm': r['rssi'], 'signal_pct': r['pct'], 'frequency_mhz': r['freq'], 'channel': r['ch'], 'band_ghz': r['band'], 'security': r['sec'], 'risk': r['risk'], 'vendor': r['vendor'], 'connected': r['conn'], 'capabilities': r['caps']} for r in rows]
with open(base + '.json', 'w', encoding='utf-8') as f:
    json.dump({'ts': ts, 'count': len(enriched), 'networks': enriched}, f, indent=2)
with open(base + '.csv', 'w', encoding='utf-8', newline='') as f:
    w = csv.writer(f)
    w.writerow(['ssid', 'bssid', 'rssi_dbm', 'signal_pct', 'frequency_mhz', 'channel', 'band_ghz', 'security', 'risk', 'vendor', 'connected'])
    for r in enriched:
        w.writerow([r['ssid'], r['bssid'], r['rssi_dbm'], r['signal_pct'], r['frequency_mhz'], r['channel'], r['band_ghz'], r['security'], r['risk'], r['vendor'], r['connected']])
with open(base + '.html', 'w', encoding='utf-8') as f:
    f.write('<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">')
    f.write('<title>Wi-Fi scan ' + html.escape(ts) + '</title>')
    f.write('<style>body{font-family:sans-serif;margin:16px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:6px;font-size:14px}th{background:#eee}.high{color:#c00;font-weight:bold}.med{color:#a60}</style></head><body>')
    f.write('<h2>Wi-Fi scan ' + html.escape(ts) + ' (' + str(len(enriched)) + ' nets)</h2>')
    f.write('<p>Passive scan via termux-wifi-scaninfo. Open with Preview. Avoid OPEN/WEP for sensitive use.</p><table><tr><th>SSID</th><th>BSSID</th><th>dBm/%</th><th>Ch</th><th>SEC</th><th>Vendor</th><th>Flags</th></tr>')
    for r in enriched:
        cls = 'high' if r['risk'] == 'HIGH' else ('med' if r['risk'] == 'MEDIUM' else '')
        f.write('<tr><td>' + html.escape(str(r['ssid'])) + '</td><td>' + html.escape(str(r['bssid'])) + '</td><td>' + str(r['rssi_dbm']) + '/' + str(r['signal_pct']) + '</td><td>' + html.escape(str(r['channel']) + '/' + str(r['band_ghz'])) + '</td><td class=' + chr(34) + cls + chr(34) + '>' + html.escape(str(r['security'])) + '</td><td>' + html.escape(str(r['vendor'])) + '</td><td>' + ('CONNECTED ' if r['connected'] else '') + html.escape(str(r['risk'])) + '</td></tr>')
    f.write('</table></body></html>')
try:
    with open(hist, 'a', encoding='utf-8') as f:
        f.write(json.dumps({'ts': ts, 'networks': [{'bssid': r['bssid'], 'ssid': r['ssid'], 'sec': r['sec'], 'risk': r['risk'], 'rssi': r['rssi']} for r in rows]}) + chr(10))
except Exception:
    pass
summ = '%d nets, %s, %d HIGH, busiest Ch %s' % (len(rows), tops, open_n, busiest[0])
with open(os.path.join(cache, 'last-summary.txt'), 'w', encoding='utf-8') as f:
    f.write(summ)
PYEOF
STATUS=$?
if [ -f "$SUM_F" ]; then toast "$(cat "$SUM_F")"; else toast 'Wi-Fi scan done'; fi
echo "Reports -> $OUTDIR"
exit $STATUS
