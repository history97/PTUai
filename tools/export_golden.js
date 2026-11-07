// 원본 index.html 의 계산 코드를 가짜 DOM 위에서 "그대로" 실행해서
// Kotlin 이식 결과와 비교할 기준값(golden.tsv)과 입력 데이터(bids.tsv)를 만든다.
//   node tools/export_golden.js tools/index.original.html engine/src/test/resources
const fs = require('fs'), vm = require('vm'), path = require('path');
const HTML = process.argv[2] || 'tools/index.original.html';
const OUT = process.argv[3] || 'engine/src/test/resources';
const src = fs.readFileSync(HTML, 'utf8');
const lines = src.split('\n');

function seg(startRe, endRe) {
  const s = lines.findIndex(l => startRe.test(l));
  if (s < 0) throw new Error('시작 마커 없음: ' + startRe);
  let e = lines.length;
  if (endRe) { e = lines.findIndex((l, i) => i > s && endRe.test(l)); if (e < 0) throw new Error('끝 마커 없음: ' + endRe); }
  return lines.slice(s, e).join('\n');
}
const m = src.match(/const BASE_RECS = (\[.*?\]);\n/s);
const BASE = JSON.parse(m[1]);

const code = [
  seg(/^const KNOWN_RATES/, /^const \$=/),                       // KNOWN_RATES, GU, K_SHRINK
  seg(/^const won=/, /^\/\* -+ 터치해서 고르기/),                  // 수식, enrich, rebuild, dist, gapStats, backtest, calc ...
  seg(/^let PK=null,PROB_W/, /^const MK_COL/),                   // PROB_W
  seg(/^let COMPETITOR_DATA/, /^function drawPre/),              // competitorRate, simS
  seg(/^function simMC/, /^function renderStats/),               // ranks, gammaP, lgamma, kruskal
].join('\n');

// ---- 가짜 DOM ----
const els = {};
const mkEl = id => ({ id, value: '', textContent: '', innerHTML: '', disabled: false, className: '', style: {},
  classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
  rows: [], clientWidth: 800, clientHeight: 330, setAttribute() {}, addEventListener() {}, querySelector() { return null; }, querySelectorAll() { return []; } });
const $ = id => els[id] || (els[id] = mkEl(id));
const ctx = vm.createContext({ console, $, document: { getElementById: $, querySelectorAll() { return []; } },
  localStorage: { getItem() { return null; }, setItem() {}, removeItem() {} } });
vm.runInContext('const BASE_RECS=' + m[1] + ';', ctx);
vm.runInContext(code, ctx, { filename: 'original-calc.js' });
vm.runInContext("let CUR_NO=''; function drawPick(){} function drawPre(){}", ctx);   // CUR_NO 는 공고 선택 UI 상태(계산과 무관)
//   // 그래프 그리는 함수는 계산과 무관
vm.runInContext('rebuild();', ctx);

const L = [];
const emit = (sec, id, key, val) => L.push([sec, id, key, Array.isArray(val) ? val.join(';') : String(val)].join('\t'));
const run = s => vm.runInContext(s, ctx);

// ---- seeded RNG (생성기 전용) ----
let seed = 20261103;
const rnd = () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
const rint = (a, b) => Math.floor(a + rnd() * (b - a + 1));
const r4 = v => Math.round(v * 10000) / 10000;

// ---- bids.tsv / extra.tsv ----
const clean = s => String(s ?? '').replace(/[\t\r\n]+/g, ' ');
const HEAD = 'no\tn\to\tco\td\tb\ta\te\tp\tf\tx\tc';
const row = r => [r.no, r.n, r.o, r.co, r.d, r.b, r.a, r.e, r.p, r.f, r.x, r.c].map(clean).join('\t');
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'bids.tsv'), [HEAD].concat(BASE.map(row)).join('\n') + '\n');

// ---- A. 수식 ----
for (let i = 0; i < 400; i++) {
  const e = Math.exp(Math.log(1e7) + rnd() * (Math.log(9.9e9) - Math.log(1e7)));
  const b = rint(1e7, 9e9), a = rnd() < 0.1 ? 0 : rint(0, Math.floor(b * 0.12));
  const s = r4((rnd() * 2 - 1) * 2.5), rate = [89.745, 88.745, 87.495, 87.745, 86.745, 85.495][rint(0, 5)];
  const id = 'r' + i;
  emit('rates', id, 'in_e', e); emit('rates', id, 'in_b', b); emit('rates', id, 'in_a', a); emit('rates', id, 'in_s', s); emit('rates', id, 'in_rate', rate);
  const B = run(`floorAt(${b},${a},${rate},${s})`);
  emit('rates', id, 'out_rateByEst', run(`rateByEst(${e})`));
  emit('rates', id, 'out_floorAt', B);
  emit('rates', id, 'out_bidFor', run(`bidFor(${b},${a},${s},${rate})`));
  emit('rates', id, 'out_sOfBid', run(`sOfBid(${B},${b},${a},${rate})`));
}
for (let i = 0; i < 120; i++) {   // snapRate: 알려진 값 근처와 임의 값
  const v = i < 60 ? [89.745, 88.745, 87.495, 87.745, 86.745, 85.495, 81.995, 79.995][i % 8] + (rnd() * 2 - 1) * 0.008 : 70 + rnd() * 25;
  emit('snap', 's' + i, 'in_v', v); emit('snap', 's' + i, 'out', run(`snapRate(${v})`));
}
// ---- B. 그룹/규모 ----
const orgs = [...new Set(BASE.map(r => r.o))].concat(['', '서울특별시 강남구', '서울특별시  종로구', '서울특별시\u00a0강남구', '서울특별시\u3000송파구', '서울특별시\t중구', '서울특별시 가나구', '서울시 어딘가', '서울특별시 도시공사', '어느 초등학교', '국토교통부', '서울주택도시공사']);
orgs.forEach((o, i) => { emit('group', 'g' + i, 'in_o', o); emit('group', 'g' + i, 'out', run(`groupOf(${JSON.stringify(o)})`)); });
[0, 5e7, 9.9999e7, 1e8, 2.99e8, 3e8, 9.99e8, 1e9, 4.99e9, 5e9, 9.9e9, 1e10, 3e10].forEach((e, i) => { emit('size', 'z' + i, 'in_e', e); emit('size', 'z' + i, 'out', run(`sizeOf(${e})`)); });

// ---- C. 데이터셋(enrich/dedupe/정렬) ----
const DATA = run('DATA');
emit('dataset', 'all', 'count', DATA.length);
DATA.forEach((r, i) => {
  emit('order', i, 'no', r.no);
  for (const k of ['e', 'rate', 's', 'sw', 'rA', 'gap', 'diff', 'grp', 'size', 'mon']) emit('enrich', r.no, k, r[k]);
});
emit('dataset', 'all', 'defaultS', run('fmtS(median(DATA.map(r=>r.s)))'));
// 중복/결측 처리 확인: 원본 rebuild() 를 EXTRA 에 일부러 이상한 값을 넣고 실행
const extra = [
  Object.assign({}, BASE[0], { x: BASE[0].x + 1000 }),                         // 같은 no → 덮어쓰기
  Object.assign({}, BASE[1], { no: '', n: '새 공고 A', d: '2026-10-01' }),        // no 없음 → n+d 키
  Object.assign({}, BASE[2], { no: 'bad-1', x: 0 }),                           // x<=0 → 제외
  Object.assign({}, BASE[3], { no: 'bad-2', p: BASE[3].a }),                   // p<=a → 제외
  Object.assign({}, BASE[4], { no: 'bad-3', e: 0 }),                           // e 결측 → b/1.1
  Object.assign({}, BASE[5], { no: 'bad-4', f: 0 }),                           // f 결측 → rateByEst
];
fs.writeFileSync(path.join(OUT, 'extra.tsv'), [HEAD].concat(extra.map(row)).join('\n') + '\n');
run(`EXTRA=${JSON.stringify(extra)}; rebuild();`);
const D2 = run('DATA');
emit('dataset2', 'all', 'count', D2.length);
D2.forEach((r, i) => { emit('order2', i, 'no', r.no || ('(' + r.n + r.d + ')')); emit('order2', i, 'rate', r.rate); emit('order2', i, 'e', r.e); emit('order2', i, 'x', r.x); });
run('EXTRA=[]; rebuild();');

// ---- D. 분포 ----
const groups = ['전체', '', '강남구', '송파구', '종로구', '공사·공단', '교육청·학교', '서울시 본청·사업소', '국가·기타', '없는그룹'];
groups.forEach((g, gi) => {
  const id = 'd' + gi; emit('dist', id, 'in_g', g);
  const D = run(`dist(${JSON.stringify(g)})`);
  emit('dist', id, 'n', D.n); emit('dist', id, 'wS', D.wS); emit('dist', id, 'hasSub', D.sub ? 1 : 0);
  for (let x = -3; x <= 3.0001; x += 0.173) { const xs = String(r4(x)); emit('dist', id, 'cdf@' + xs, D.cdf(+xs)); }
  [-0.0001, 0.0000001, 2.5, 4, -4].forEach(x => emit('dist', id, 'cdf@' + x, D.cdf(x)));
  [0.001, 0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99, 0.999].forEach(q => emit('dist', id, 'inv@' + q, D.inv(q)));
});
// ---- E. gapStats ----
const GSx = run('gapStats()');
emit('gap', 'all', 'cnt', GSx.cnt); emit('gap', 'all', 'modeIdx', GSx.modeIdx); emit('gap', 'all', 'mode', GSx.mode); emit('gap', 'all', 'n', GSx.G.length);
[0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99].forEach(p => emit('gap', 'all', 'q@' + p, GSx.q(p)));
// ---- F. 통계 함수 ----
emit('stats', 'all', 'mean', run('mean(SALL)')); emit('stats', 'all', 'sd', run('sd(SALL)')); emit('stats', 'all', 'median', run('median(SALL)'));
[0, 0.01, 0.1, 0.333, 0.5, 0.9, 0.999, 1].forEach(q => emit('stats', 'all', 'quant@' + q, run(`quant(SALL,${q})`)));
for (let i = 0; i < 40; i++) { const a = 0.5 + rnd() * 20, x = rnd() * 40; emit('gamma', 'g' + i, 'in_a', a); emit('gamma', 'g' + i, 'in_x', x); emit('gamma', 'g' + i, 'out', run(`gammaP(${a},${x})`)); }
for (let i = 0; i < 30; i++) { const z = 0.1 + rnd() * 30; emit('lgamma', 'l' + i, 'in_z', z); emit('lgamma', 'l' + i, 'out', run(`lgamma(${z})`)); }
const byKey = keyFn => { const mp = {}; DATA.forEach(r => (mp[keyFn(r)] = mp[keyFn(r)] || []).push(r.s)); return mp; };
[['grp', r => r.grp], ['size', r => r.size], ['year', r => r.d.slice(0, 4)]].forEach(([name, fn]) => {
  const mp = byKey(fn), names = Object.keys(mp).sort();
  const res = run(`kruskal(${JSON.stringify(names.map(n => mp[n]))})`);
  emit('kruskal', name, 'names', names.join('|')); emit('kruskal', name, 'H', res.H); emit('kruskal', name, 'df', res.df); emit('kruskal', name, 'p', res.p);
});
// ---- G. 백테스트 ----
const btCases = [];
for (let i = 0; i < 30; i++) btCases.push([r4((rnd() * 2 - 1) * 1.5), [0, 0, 0, 0.5, -0.5, 1][rint(0, 5)], groups[rint(0, groups.length - 1)]]);
btCases.forEach(([s, off, g], i) => {
  const id = 'b' + i; emit('backtest', id, 'in_s', s); emit('backtest', id, 'in_off', off); emit('backtest', id, 'in_g', g);
  const all = run(`backtestTS(${s},${off},DATA)`); ['n', 'q', 'win', 'low', 'high'].forEach(k => emit('backtest', id, 'all_' + k, all[k]));
  const sub = run(`backtestTS(${s},${off},DATA.filter(r=>r.grp===${JSON.stringify(g)}))`); ['n', 'q', 'win', 'low', 'high'].forEach(k => emit('backtest', id, 'grp_' + k, sub[k]));
  const b1 = run(`backtest(${s},DATA)`); ['n', 'q', 'win'].forEach(k => emit('backtest', id, 'old_' + k, b1[k]));
});
// ---- H. 시뮬레이션 (시드 고정 PRNG 그대로) ----
[['A', 2], ['A', 3], ['B', 2], ['B', 3], ['C', 2], ['C', 3]].forEach(([model, R]) => {
  const out = run(`simS('${model}',${R})`), id = model + R;
  emit('sim', id, 'len', out.length);
  [0, 1, 2, 100, 999, 5000, 50000, 77777, 99998, 99999].forEach(i => emit('sim', id, 'at@' + i, out[i]));
  let sum = 0; for (const v of out) sum += v; emit('sim', id, 'sum', sum);
});
// ---- I. num / 포맷 ----
['1,234,567', '12.5%', '-3.2', 'abc', '', '1.2.3', '--5', '١٢٣', ' 45 원', '.5', '-.5', '5.', '1e5', '0', '-0', '1,000.50'].forEach((s, i) => { emit('num', 'n' + i, 'in', JSON.stringify(s)); emit('num', 'n' + i, 'out', run(`num(${JSON.stringify(s)})`)); });
const fmtVals = [0, 0.5, 1.5, 2.5, -2.5, -0.5, 1.005, 1.45, 8.345, -0.00001, 0.00004999, 0.00005, 123456.789, -987654.321, 2193681000, -1.9999999, 3.14159265, 99.99995, 1e-7, 0.1 + 0.2];
fmtVals.forEach((v, i) => {
  const id = 'f' + i; emit('fmt', id, 'in', v);
  [0, 1, 3, 4].forEach(d => emit('fmt', id, 'toFixed' + d, run(`(${v}).toFixed(${d})`)));
  emit('fmt', id, 'won', run(`won(${v})`)); emit('fmt', id, 'f3', run(`f3(${v})`)); emit('fmt', id, 'trunc4', run(`trunc4(${v})`)); emit('fmt', id, 'trunc3', run(`trunc3(${v})`));
  emit('fmt', id, 'round', run(`Math.round(${v})`));
});

// ---- J. calc() 전체 (가짜 DOM 위에서 원본 calc 실행) ----
const set = (id, v) => { $(id).value = v === undefined || v === null ? '' : String(v); };
const clearAll = () => ['inB', 'inA', 'inE', 'inC', 'inG', 'inR', 'inS', 'inP', 'inX', 'inN'].forEach(id => set(id, ''));
clearAll(); set('inB', 148870940); set('inA', 12056532); set('inG', '전체'); run('calc()');   // 워밍업: 기본 사정율(initDone) 처리
const calcCases = [];
for (let i = 0; i < 90; i++) {
  const rec = BASE[rint(0, BASE.length - 1)];
  const useRec = rnd() < 0.6;
  const b = useRec ? rec.b : rint(2e7, 4.5e9), a = rnd() < 0.15 ? 0 : (useRec ? rec.a : rint(0, Math.floor(b * 0.1)));
  const mode = rnd();
  const c = {
    b, a, e: rnd() < 0.5 ? (useRec ? rec.e : Math.round(b / 1.1)) : '', c: rnd() < 0.5 ? Math.round(b * (0.7 + rnd() * 0.2)) : '',
    g: groups[rint(0, groups.length - 1)], r: rnd() < 0.15 ? [87.745, 86.745, 89.745][rint(0, 2)] : '',
    s: mode < 0.15 ? 'default' : r4((rnd() * 2 - 1) * 1.6), p: mode > 0.85 ? Math.round(b * (1 + (rnd() * 2 - 1) * 0.02)) : '',
    x: rnd() < 0.5 ? Math.round(b * (0.82 + rnd() * 0.12)) : '',
  };
  calcCases.push(c);
}
// 계산 불가/경계 케이스를 앞에 고정으로 추가
calcCases.unshift({ b: 0, a: 0, e: '', c: '', g: '전체', r: '', s: 0, p: '', x: '' },
  { b: 1.2e10, a: 1e8, e: 1.2e10, c: '', g: '전체', r: '', s: 0, p: '', x: '' },
  { b: 148870940, a: 12056532, e: '', c: 135000000, g: '전체', r: '', s: -0.00001, p: '', x: 125000000 });
calcCases.forEach((c, i) => {
  const id = 'c' + i;
  clearAll();
  set('inB', c.b); set('inA', c.a); set('inE', c.e); set('inC', c.c); set('inG', c.g); set('inR', c.r); set('inP', c.p); set('inX', c.x);
  set('inS', c.s === 'default' ? run('fmtS(median(DATA.map(r=>r.s)))') : c.s);
  ['in_b', 'in_a', 'in_e', 'in_c', 'in_g', 'in_r', 'in_p', 'in_x'].forEach((k, j) => emit('calc', id, k, [c.b, c.a, c.e, c.c, c.g, c.r, c.p, c.x][j]));
  emit('calc', id, 'in_s', c.s);
  ['bidNum', 'bidMeta', 'probBig', 'compBig', 'sProb', 'stB', 'stP'].forEach(k => { $(k).innerHTML = ''; $(k).textContent = ''; });
  $('alerts').innerHTML = ''; $('scenTbl').innerHTML = ''; $('kvX').innerHTML = ''; $('kv').innerHTML = '';
  run('LAST=null; calc();');
  const LAST = run('LAST');
  if (!LAST) { emit('calc', id, 'out_ok', 0); emit('calc', id, 'out_msg', $('bidMeta').textContent || $('bidMeta').innerHTML); return; }
  emit('calc', id, 'out_ok', 1);
  for (const k of ['rate', 'e', 'sExp', 'sBid', 'B', 'pQ', 'nearP', 'pExp', 'floorExp', 'off']) emit('calc', id, 'out_' + k, LAST[k]);
  for (const k of ['n', 'q', 'win', 'low', 'high']) emit('calc', id, 'out_bt_' + k, LAST.bt[k]);
  if (LAST.btG) for (const k of ['n', 'q', 'win', 'low', 'high']) emit('calc', id, 'out_btG_' + k, LAST.btG[k]);
  emit('calc', id, 'out_hasBtG', LAST.btG ? 1 : 0);
  emit('calc', id, 'out_bidNum', $('bidNum').innerHTML); emit('calc', id, 'out_bidMeta', $('bidMeta').innerHTML);
  emit('calc', id, 'out_probBig', $('probBig').textContent); emit('calc', id, 'out_compBig', $('compBig').textContent);
  emit('calc', id, 'out_sProb', $('sProb').textContent);
  const al = $('alerts').innerHTML;
  emit('calc', id, 'out_alert_bad', /warn bad/.test(al) ? 1 : 0);
  emit('calc', id, 'out_alert_zeroA', /A값이 0이면/.test(al) ? 1 : 0);
  emit('calc', id, 'out_alert_c', (/순공사원가의? 98%\(([\d,]+)원\)/.exec(al) || [])[1] || '');
  // 시나리오 표
  const rows = $('scenTbl').innerHTML.split('</tr>').filter(x => x.includes('<td')).map(r => ({ hl: /class="hl"/.test(r), cells: [...r.matchAll(/<td[^>]*>(.*?)<\/td>/g)].map(x => x[1]) }));
  emit('calc', id, 'out_scen_n', rows.length);
  rows.forEach((r, j) => { emit('calc', id, 'out_scen' + j + '_hl', r.hl ? 1 : 0); r.cells.forEach((t, k) => emit('calc', id, `out_scen${j}_c${k}`, t)); });
  // kv / kvX 의 dt-dd 쌍
  const pairs = h => [...h.matchAll(/<dt>(.*?)<\/dt><dd[^>]*>(.*?)<\/dd>/g)].map(x => [x[1], x[2]]);
  pairs($('kv').innerHTML).forEach(([k, v], j) => { emit('calc', id, `out_kv${j}_k`, k); emit('calc', id, `out_kv${j}_v`, v); });
  pairs($('kvX').innerHTML).forEach(([k, v], j) => { emit('calc', id, `out_kvx${j}_k`, k); emit('calc', id, `out_kvx${j}_v`, v); });
});


// ---- K. 화면 차트 계산 (원본 drawPick / drawPre / chipsS 를 그대로 실행) ----
const one = re => { const l = lines.find(x => re.test(x)); if (!l) throw new Error('없음 ' + re); return l; };
vm.runInContext([one(/^const man=/), one(/^const MK_COL/), 'function css(v){return ""}', 'var __CHARTS={}; function mkChart(id,cfg){__CHARTS[id]=cfg}', 'function pickInfo(){}'].join('\n'), ctx);
vm.runInContext(seg(/^function drawPick/, /^let HERO_SNAP/).replace('const yV=', 'globalThis.__DBG={vol,kde,modeS,meanS,q25,q75,lo,hi,nb,bw,vmax};const yV='), ctx);
vm.runInContext(seg(/^function drawPre/, /^const man=/), ctx);
const manVals = [123456.7, 141234.6, 141234.4, 150000, 0.04, 99995, 99994.9999, 1234567.89, 14123.96, 14123.46, 14123.45, 14123.55, 1e8, 191980000.5, 0, 5000, 4999.9];
manVals.forEach((v, i) => { emit('man', 'm' + i, 'in', v); emit('man', 'm' + i, 'out', run(`man(${v})`)); emit('man', 'm' + i, 'axis', run(`man(${v}).split('.')[0]+'만'`)); });

const pickGroups = ['전체', '강남구', '송파구', '공사·공단', '없는그룹'];
let pi = 0;
for (const g of pickGroups) for (const [W, pw] of [[500, 0.05], [820, 0.05], [820, 0.01], [820, 0.1]]) {
  if (g === '없는그룹' && pw !== 0.05) continue;
  const id = 'p' + (pi++), b = rint(5e7, 3e9), a = rint(0, Math.floor(b * 0.08)), rate = [89.745, 88.745, 87.745][rint(0, 2)], s = r4((rnd() * 2 - 1) * 1.2);
  ['g', 'W', 'pw', 'b', 'a', 'rate', 's'].forEach((k, j) => emit('pick', id, 'in_' + k, [g, W, pw, b, a, rate, s][j]));
  $('pickSvg').clientWidth = W; run(`PROB_W=${pw}`);
  run(`drawPick(dist(${JSON.stringify(g)}),${b},${a},${rate},${s})`);
  const dbg = run('__DBG'), PKx = run('PK');
  emit('pick', id, 'nb', dbg.nb); emit('pick', id, 'lo', dbg.lo); emit('pick', id, 'hi', dbg.hi);
  emit('pick', id, 'vol', dbg.vol.join(';')); emit('pick', id, 'kdeX', dbg.kde.map(p => p[0]).join(';')); emit('pick', id, 'kdeY', dbg.kde.map(p => p[1]).join(';'));
  ['modeS', 'meanS', 'q25', 'q75', 'vmax'].forEach(k => emit('pick', id, k, dbg[k])); emit('pick', id, 'med', PKx.med);
  emit('pick', id, 'pbMode', $('pbMode').textContent); emit('pick', id, 'pbMed', $('pbMed').textContent); emit('pick', id, 'pbMean', $('pbMean').textContent);
}
run('PROB_W=0.05');

const cells = h => h.split('</tr>').filter(x => x.includes('<t')).map(r => [...r.matchAll(/<t[hd][^>]*>(.*?)<\/t[hd]>/g)].map(x => x[1]));
const preCases = [
  ['전체', 148870940, 12056532, 89.745, '', 100000], ['강남구', 148870940, 12056532, 89.745, '', 10000], ['공사·공단', 520000000, 30000000, 89.745, '2', 100000],
  ['국가·기타', 90000000, 0, 89.745, '', 100000], ['송파구', 1500000000, 80000000, 88.745, '3', 10000], ['전체', 2400000000, 150000000, 88.745, '', 100000],
  ['서울시 본청·사업소', 310000000, 20000000, 89.745, '', 100000], ['없는그룹', 75000000, 5000000, 89.745, '', 10000],
];
preCases.forEach(([g, b, a, rate, inV, unit], i) => {
  const id = 'q' + i;
  ['g', 'b', 'a', 'rate', 'inV', 'unit'].forEach((k, j) => emit('pre', id, 'in_' + k, [g, b, a, rate, inV, unit][j]));
  $('inV').value = inV; run(`P_UNIT=${unit}`); $('pStat').innerHTML = ''; $('pTbl').innerHTML = '';
  run(`drawPre(dist(${JSON.stringify(g)}),${b},${a},${rate},${JSON.stringify(g)})`);
  emit('pre', id, 'note', $('pNote').textContent);
  cells($('pStat').innerHTML).slice(1).forEach((r, j) => r.forEach((t, k) => emit('pre', id, `stat${j}_c${k}`, t)));
  const tb = cells($('pTbl').innerHTML).slice(1); emit('pre', id, 'tbl_n', tb.length);
  tb.forEach((r, j) => r.forEach((t, k) => emit('pre', id, `tbl${j}_c${k}`, t)));
  const ch = run('__CHARTS.cPre');
  emit('pre', id, 'labels', ch.data.labels.join(';'));
  ch.data.datasets.forEach((d, j) => emit('pre', id, 'ds' + j, d.data.join(';')));
});
['전체', '강남구', '송파구', '없는그룹'].forEach((g, i) => {
  const id = 'k' + i; emit('chips', id, 'in_g', g);
  $('inG').value = g; $('inS').value = '0.0123'; $('sChips').innerHTML = '';
  run(`chipsS(dist(${JSON.stringify(g)}))`);
  [...$('sChips').innerHTML.matchAll(/data-v="([^"]+)" aria-pressed="([^"]+)">([^<]*)<\/button>/g)].forEach((m, j) => { emit('chips', id, `v${j}`, m[1]); emit('chips', id, `pressed${j}`, m[2]); emit('chips', id, `label${j}`, m[3]); });
});


// ---- L. 엑셀 읽기 / 공고 목록 (원본 parseRows, parseNotices, renderNotices 를 그대로 실행) ----
vm.runInContext([seg(/^function toDate/, /^\$\('fileIn'\)/), seg(/^function parseDeadline/, /^const DCACHE/), seg(/^const DCACHE/, /^function renderNotices/),
  seg(/^function renderNotices/, /^function quickAmt/), seg(/^function quickAmt/, /^function applyNotice/), 'var NOTICES=[];'].join('\n'), ctx);
const encRows = (id, rows) => {
  emit('imp_in', id, 'nrows', rows.length);
  rows.forEach((r, i) => { emit('imp_in', id, `r${i}n`, r.length); r.forEach((c, j) => emit('imp_in', id, `r${i}c${j}`, typeof c === 'number' ? 'n|' + c : 's|' + c)); });
};
const shuffle = a => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = rint(0, i); [a[i], a[j]] = [a[j], a[i]]; } return a; };
const fmtComma = v => Number(v).toLocaleString('en-US');
const dateVar = (d, k) => { const [y, m, dd] = d.split('-'); return [d, `${y}.${m}.${dd}`, `${y.slice(2)}/${+m}/${+dd}`, `${y}-${+m}-${+dd} 10:00`, (Date.UTC(+y, +m - 1, +dd) / 864e5 + 25569)][k % 5]; };
const RES_COLS = ['공고명', '공고번호', '발주기관', '기초금액', '1순위투찰금액', '개찰일', 'A값', '추정가격', '순공사원가', '낙찰하한가', '예정가격', '1순위업체'];
const rkey = { 공고명: 'n', 공고번호: 'no', 발주기관: 'o', 기초금액: 'b', '1순위투찰금액': 'x', 개찰일: 'd', A값: 'a', 추정가격: 'e', 순공사원가: 'c', 낙찰하한가: 'f', 예정가격: 'p', '1순위업체': 'co' };
const mkResults = (cols, nrec, opt) => {
  const rows = [['인포21C 낙찰정보리스트', '', ''], ['조회조건: 서울 조경', '', '']];
  rows.push(opt.pad ? ['', ...cols.map(c => ' ' + c + ' ')] : cols.slice());
  for (let i = 0; i < nrec; i++) {
    const r = BASE[rint(0, BASE.length - 1)], v = {};
    for (const k of Object.keys(rkey)) v[rkey[k]] = r[rkey[k]];
    const mode = i % 9;
    if (mode === 1) v.f = 0; if (mode === 2) v.e = 0; if (mode === 3) v.c = 0; if (mode === 4) v.x = 0; if (mode === 5) v.n = '';
    if (mode === 6) v.a = ''; if (mode === 7) v.d = '날짜없음'; if (mode === 8) v.p = fmtComma(v.p) + '.4';
    const cellFor = (k) => { const val = v[k]; if (k === 'd') return typeof val === 'string' && val === '날짜없음' ? val : dateVar(val, i); if (typeof val === 'number') return i % 2 ? val : fmtComma(val); return val; };
    const row = cols.map(c => cellFor(rkey[c])); if (opt.pad) row.unshift(i);
    rows.push(row);
    if (i % 11 === 5) rows.push(cols.map(() => ''));        // 빈 줄
  }
  return rows;
};
const resCases = {
  full: mkResults(RES_COLS, 45, {}), shuffled: mkResults(shuffle(RES_COLS), 30, { pad: true }),
  noOptional: mkResults(['공고명', '기초금액', '1순위투찰금액', '개찰일', 'A값', '예정가격'], 25, {}),
  missingCol: mkResults(RES_COLS.filter(c => c !== 'A값' && c !== '예정가격' ? true : c === '예정가격'), 5, {}),
  noHeader: [['아무', '표'], [1, 2]], empty: [],
};
const NOTICE_COLS = ['공고명', '공고번호', '발주기관', '기초금액', '투찰마감', '개찰일', '추정가격', 'A값', '순공사원가', '낙찰하한율', '예가변동폭'];
const orgPool = ['서울특별시 강남구', '서울특별시 송파구', '서울주택도시공사', '서울특별시 도봉구청', '서울특별시교육청 동부교육지원청', '서울특별시', '한국토지주택공사 서울지역본부', '국방부근무지원단'];
const varPool = ['±3', '기초금액의 -3% ~ +3%', '2', '', '3.0', '±2 %', '없음', 3, 2];
const dlPool = ['2026-11-10 14:00', '2026.11.12 10:30:00', '26/11/15 9:00', '', '2026-11-03 17:00', '2027-01-05 11:20', '마감미정', '2026-11-04 12:00'];
const mkNotices = (cols, n) => {
  const rows = [['입찰리스트', ''], cols.slice()];
  for (let i = 0; i < n; i++) {
    const b = rint(3e7, 3e9), e = i % 4 === 0 ? '' : Math.round(b / 1.1), a = i % 5 === 1 ? '' : rint(0, Math.floor(b * 0.08));
    const v = { 공고명: `공원 조경공사 ${i + 1}호 ${i % 3 === 0 ? '[A값][순]' : ''}`.trim(), 공고번호: `2026${rint(1e5, 9e5)}-0${i % 3}`, 발주기관: orgPool[i % orgPool.length],
      기초금액: i % 7 === 3 ? fmtComma(b) : b, 투찰마감: dlPool[i % dlPool.length], 개찰일: '2026-11-1' + (i % 9), 추정가격: e, A값: a, 순공사원가: i % 3 === 0 ? Math.round(b * 0.8) : '',
      낙찰하한율: [89.745, '', 88.745, '87.745', 0][i % 5], 예가변동폭: varPool[i % varPool.length] };
    if (i === 6) v.기초금액 = 0; if (i === 8) v.공고명 = '';
    rows.push(cols.map(c => v[c]));
  }
  return rows;
};
const noticeCases = { full: mkNotices(NOTICE_COLS, 40), minimal: mkNotices(['공고명', '기초금액'], 12), noHeader: [['공고명', '금액'], ['a', 1]], shuffled: mkNotices(shuffle(NOTICE_COLS), 30) };
for (const [id, rows] of Object.entries(resCases)) {
  encRows('res_' + id, rows);
  const res = run(`parseRows(${JSON.stringify(rows)})`);
  emit('imp_res', id, 'err', res.err || ''); emit('imp_res', id, 'skip', res.skip ?? 0); emit('imp_res', id, 'n', res.recs.length);
  res.recs.forEach((r, i) => ['n', 'no', 'o', 'b', 'x', 'a', 'p', 'd', 'e', 'c', 'f', 'co'].forEach(k => emit('imp_res', id, `r${i}_${k}`, r[k])));
}
const nanJson = o => JSON.stringify(o, (k, v) => (typeof v === 'number' && Number.isNaN(v)) ? '__NaN__' : v).replace(/"__NaN__"/g, 'NaN');
const avail = [...$('inG').innerHTML.matchAll(/<option value="([^"]*)"/g)].map(m => m[1]);
emit('imp_notice', 'meta', 'groups', avail.join('|'));
$('inG').options = avail.map(v => ({ value: v }));
for (const [id, rows] of Object.entries(noticeCases)) {
  encRows('nt_' + id, rows);
  const list = run(`parseNotices(${JSON.stringify(rows)})`);
  emit('imp_notice', id, 'isNull', list === null ? 1 : 0);
  if (!list) continue;
  emit('imp_notice', id, 'n', list.length);
  list.forEach((x, i) => ['n', 'no', 'o', 'b', 'dl', 'op', 'e', 'a', 'c', 'r', 'v'].forEach(k => emit('imp_notice', id, `x${i}_${k}`, x[k])));
  // 목록 화면: 전체 보기(시각과 무관한 부분) 와 값 채우기
  run('NOTICES=' + nanJson(list)); $('nbAll').checked = true; $('nbList').innerHTML = ''; run('renderNotices()');
  const items = [...$('nbList').innerHTML.matchAll(/<button class="nbitem [^"]*" data-i="(\d+)"><span class="t">(.*?)<\/span><span class="m">(.*?)<\/span>(?:<span class="q">50% <b>([\d,\-]+)<\/b> · 70% <b>([\d,\-]+)<\/b> · 90% <b>([\d,\-]+)<\/b><\/span>)?<\/button>/g)];
  emit('imp_list', id, 'n', items.length);
  items.forEach((m, i) => { emit('imp_list', id, `i${i}_idx`, m[1]); emit('imp_list', id, `i${i}_title`, m[2]); emit('imp_list', id, `i${i}_meta`, m[3]); emit('imp_list', id, `i${i}_q`, m[4] ? [m[4], m[5], m[6]].join(';') : ''); });
  list.forEach((x, i) => { emit('imp_form', id, `x${i}_rate`, Number.isFinite(x.r) && x.r > 0 ? String(+x.r.toFixed(3)) : ''); });
}

fs.writeFileSync(path.join(OUT, 'golden.tsv'), L.join('\n') + '\n');
const cnt = {}; L.forEach(l => { const s = l.split('\t')[0]; cnt[s] = (cnt[s] || 0) + 1; });
console.log('golden lines:', L.length, JSON.stringify(cnt));
console.log('DATA rows:', DATA.length, 'extra-run rows:', D2.length, 'calc cases:', calcCases.length);
