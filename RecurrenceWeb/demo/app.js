const $ = id => document.getElementById(id);
const locale = navigator.language || 'en-US';
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
$('context').textContent = `Langue du navigateur : ${locale} · Fuseau : ${timezone} (sans DTSTART, le fuseau est informatif)`;

// The webpack bundles expose their Kotlin @JsExport API on window via libraryTarget=window.
// Do not attempt import() on a non-ES-module webpack bundle.
const runtimes = [
  {key:'js', file:'./dist/js/recurrence-js.js', global:'RecurrenceWebJs'},
  {key:'wasm', file:'./dist/wasm/recurrence-wasm.js', global:'RecurrenceWebWasm'},
];
const loadScript = src => new Promise((resolve, reject) => {
  const s=document.createElement('script'); s.src=src; s.onload=resolve;
  s.onerror=()=>reject(new Error(`Impossible de charger ${src} — vérifier build-demo.sh et le serveur`));
  document.head.appendChild(s);
});

// Kotlin/JS may put top-level @JsExport declarations below a package namespace.
// Kotlin/Wasm may expose them under `default`, and webpack may return a promise
// while the WebAssembly binary is being initialized.
function findExport(root, target = 'formatRRule') {
  const visited = new Set();
  const queue = [{value: root, path: 'library', depth: 0}];
  const inspected = [];
  while (queue.length) {
    const {value, path, depth} = queue.shift();
    if (value == null || (typeof value !== 'object' && typeof value !== 'function')) continue;
    if (visited.has(value)) continue;
    visited.add(value);
    let keys;
    try { keys = Object.keys(value); } catch { continue; }
    inspected.push(`${path}: ${keys.slice(0, 20).join(', ')}`);
    if (typeof value[target] === 'function') {
      return {fn: value[target].bind(value), path: `${path}.${target}`, inspected};
    }
    if (depth >= 7 || visited.size >= 150) continue;
    for (const key of keys) {
      if (key === '__proto__' || key === 'prototype' || key === 'constructor') continue;
      let child;
      try { child = value[key]; } catch { continue; }
      if (child != null && (typeof child === 'object' || typeof child === 'function')) {
        queue.push({value: child, path: `${path}.${key}`, depth: depth + 1});
      }
    }
  }
  return {fn: null, path: null, inspected};
}

async function resolveExport(name) {
  // Check both window globals and the resolved promise, if webpack returns one.
  let library = window[name];
  if (library && typeof library.then === 'function') library = await library;
  let found = findExport(library);
  if (found.fn) return found;
  // Wasm startup can finish on a later microtask; allow the load event to settle.
  await new Promise(resolve => setTimeout(resolve, 0));
  library = window[name];
  if (library && typeof library.then === 'function') library = await library;
  found = findExport(library);
  if (!found.fn) {
    console.error(`[${name}] Kotlin export not found. Available paths:`, found.inspected, library);
    throw new Error(`Export formatRRule introuvable dans ${name}. Ouvre la console : chemins disponibles affichés.`);
  }
  return found;
}

for (const runtime of runtimes) {
  try {
    const start = performance.now();
    await loadScript(runtime.file);
    const {fn, path} = await resolveExport(runtime.global);
    console.info(`[${runtime.global}] export resolved: ${path}`);
    runtime.format = (raw, lang) => new Promise((resolve, reject) => {
      try { fn(raw, lang, resolve, message => reject(new Error(String(message)))); }
      catch (e) { reject(e); }
    });
    runtime.loadMs = performance.now() - start;
    $(`${runtime.key}Status`).textContent = `Prêt · ${path} · chargement ${runtime.loadMs.toFixed(1)} ms`;
  } catch (err) {
    $(`${runtime.key}Status`).textContent = `Chargement impossible : ${err.message}`;
  }
}

let revision=0;
async function render() {
  const current=++revision;
  const rule=$('rrule').value;
  await Promise.all(runtimes.map(async r=>{
    if(!r.format){$(r.key+'Result').textContent='Non disponible'; return;}
    try {
      const value=await r.format(rule,locale);
      if(current===revision) $(r.key+'Result').textContent=value;
    }catch(e){if(current===revision) $(r.key+'Result').textContent=`Erreur : ${e.message}`;}
  }));
}
$('convert').addEventListener('click',render);
$('rrule').addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key==='Enter') render();});
document.querySelectorAll('[data-rule]').forEach(b=>b.addEventListener('click',()=>{$('rrule').value=b.dataset.rule;render();}));
await render();

const median=values=>[...values].sort((a,b)=>a-b)[Math.floor(values.length/2)];
$('bench').addEventListener('click',async()=>{
  const button=$('bench');button.disabled=true;
  const original=$('rrule').value;
  $('benchStatus').textContent='Mesures en cours…';
  try {
    const active=runtimes.filter(r=>r.format);
    if(active.length!==2) throw new Error('Les deux runtimes doivent être disponibles.');
    // Check equivalent output before comparing performance.
    const output=await Promise.all(active.map(r=>r.format(original,locale)));
    if(output[0]!==output[1]) throw new Error('Résultats divergents : benchmark refusé.');
    const repetitions=1000, warmup=200, samples={js:[],wasm:[]};
    for(const r of active) for(let i=0;i<warmup;i++) await r.format(original,locale);
    let checksum=0;
    for(let round=0;round<5;round++) {
      for(const r of round%2?[...active].reverse():active) {
        const t=performance.now();
        for(let i=0;i<repetitions;i++) checksum+=(await r.format(original,locale)).length;
        samples[r.key].push(performance.now()-t);
      }
      await new Promise(resolve=>requestAnimationFrame(resolve));
    }
    for(const r of active) $(r.key+'Metric').textContent=median(samples[r.key]).toFixed(2);
    $('benchStatus').textContent=`Médiane / 1 000 appels asynchrones, 5 échantillons · ${navigator.userAgent} · checksum ${checksum}. Inclut bridge et chargement des traductions; ne mesure pas uniquement le calcul Kotlin.`;
  }catch(e){$('benchStatus').textContent=`Benchmark indisponible : ${e.message}`;}
  finally{button.disabled=false;}
});

try {
  const resp=await fetch('./build-timing.json', {cache:'no-store'});
  if(resp.ok) {
    const timing=await resp.json();
    $('buildTiming').textContent=`Build Gradle (exécution séquentielle) · JS ${timing.jsSeconds}s · Wasm ${timing.wasmSeconds}s · inclut Gradle, cache et dépendances.`;
  }
} catch (_) { /* Builds are optional to display, but runtime loading is not. */ }
