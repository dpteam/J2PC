package j2pc.gui;

/**
 * The web front end, embedded as a string.
 *
 * <p>Kept in source rather than as a resource file so the tool stays a single JAR
 * with no class-path assumptions about where its assets live. The page is plain
 * HTML/CSS/JS with no build step and no dependencies.</p>
 */
final class WebAssets {

    private WebAssets() {
    }

    static final String INDEX_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>J2PC</title>
<style>
  :root {
    --bg: #0d1420;
    --panel: #141d31;
    --panel2: #1b2540;
    --line: #24314f;
    --text: #e6eefc;
    --dim: #7f9ac4;
    --accent: #22d3ee;
    --accent2: #60a5fa;
    --ok: #34d399;
    --warn: #fbbf24;
    --err: #f87171;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; background: var(--bg); color: var(--text);
    font: 14px/1.5 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
    display: flex; flex-direction: column; height: 100vh;
  }
  header {
    display: flex; align-items: center; gap: 12px;
    padding: 10px 16px; border-bottom: 1px solid var(--line);
    background: linear-gradient(90deg, #131a2b, #1b2540);
  }
  header h1 { font-size: 16px; margin: 0; letter-spacing: 1px; }
  header .tag {
    font-size: 10px; letter-spacing: 2px; color: var(--dim);
    border: 1px solid var(--line); border-radius: 10px; padding: 2px 8px;
  }
  main { flex: 1; display: grid; grid-template-columns: 320px 1fr; min-height: 0; }
  aside {
    border-right: 1px solid var(--line); display: flex; flex-direction: column;
    min-height: 0; background: var(--panel);
  }
  .block { padding: 12px 14px; border-bottom: 1px solid var(--line); }
  .block h2 {
    font-size: 11px; text-transform: uppercase; letter-spacing: 1.5px;
    color: var(--dim); margin: 0 0 8px;
  }
  input[type=text] {
    width: 100%; padding: 7px 9px; border-radius: 7px;
    border: 1px solid var(--line); background: var(--panel2); color: var(--text);
    font: inherit;
  }
  input[type=text]:focus { outline: none; border-color: var(--accent2); }
  button {
    padding: 8px 14px; border-radius: 7px; border: 1px solid var(--line);
    background: var(--panel2); color: var(--text); font: inherit; cursor: pointer;
  }
  button:hover { border-color: var(--accent2); }
  button.primary {
    background: linear-gradient(135deg, #22d3ee22, #60a5fa22);
    border-color: var(--accent2); color: #cfe9ff; font-weight: 600;
  }
  button:disabled { opacity: .45; cursor: default; }
  .row { display: flex; gap: 8px; align-items: center; }
  .check { display: flex; gap: 8px; align-items: center; padding: 3px 0; color: var(--text); }
  .check input { accent-color: var(--accent); }
  ul.files { list-style: none; margin: 0; padding: 0; overflow: auto; flex: 1; }
  ul.files li {
    padding: 7px 14px; cursor: pointer; border-left: 2px solid transparent;
    font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  ul.files li:hover { background: var(--panel2); }
  ul.files li.active { background: #1d2b48; border-left-color: var(--accent); }
  ul.files li .sz { float: right; color: var(--dim); font-size: 11px; }
  section.right { display: flex; flex-direction: column; min-height: 0; }
  .tabs { display: flex; border-bottom: 1px solid var(--line); background: var(--panel); }
  .tabs div {
    padding: 9px 16px; cursor: pointer; color: var(--dim); font-size: 13px;
    border-bottom: 2px solid transparent;
  }
  .tabs div.active { color: var(--text); border-bottom-color: var(--accent); }
  pre {
    flex: 1; margin: 0; padding: 14px 16px; overflow: auto; min-height: 0;
    font: 12px/1.55 'Cascadia Code', Consolas, monospace;
    background: #0a1018; color: #cfe0f5; white-space: pre;
  }
  .statusbar {
    display: flex; gap: 16px; align-items: center; padding: 7px 16px;
    border-top: 1px solid var(--line); background: var(--panel);
    font-size: 12px; color: var(--dim);
  }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--dim); }
  .dot.busy { background: var(--warn); animation: pulse 1s infinite; }
  .dot.ok { background: var(--ok); }
  .dot.err { background: var(--err); }
  @keyframes pulse { 50% { opacity: .35; } }
  .log-line { display: block; }
  .log-line.error { color: var(--err); }
  .log-line.warn { color: var(--warn); }
  .log-line.info { color: #9fb6d6; }
  .badge { font-size: 11px; padding: 1px 7px; border-radius: 9px; background: #22d3ee18; color: var(--accent); }
</style>
</head>
<body>
<header>
  <h1>J2PC</h1>
  <span class="tag">J2ME STATIC RECOMPILER</span>
  <span id="version" class="badge"></span>
</header>

<main>
  <aside>
    <div class="block">
      <h2>Folder</h2>
      <div class="row">
        <input id="folder" type="text" spellcheck="false">
        <button id="scan" title="Scan">Scan</button>
      </div>
    </div>

    <div class="block">
      <h2>Applications</h2>
      <ul class="files" id="apps"></ul>
    </div>

    <div class="block">
      <h2>Options</h2>
      <label class="check"><input type="checkbox" id="optBuild" checked> compile &amp; package JAR</label>
      <label class="check"><input type="checkbox" id="optExe"> native launcher (jpackage)</label>
      <label class="check"><input type="checkbox" id="optStubs" checked> stub missing APIs</label>
      <label class="check"><input type="checkbox" id="optLines" checked> line comments</label>
    </div>

    <div class="block">
      <div class="row">
        <button class="primary" id="run" style="flex:1">Recompile</button>
        <button id="launch" title="Start the packaged application" disabled>Play</button>
        <button id="clear" title="Clear log">Clear</button>
      </div>
    </div>
  </aside>

  <section class="right">
    <div class="tabs">
      <div data-tab="log" class="active">Log</div>
      <div data-tab="sources">Generated sources</div>
    </div>
    <pre id="pane-log"></pre>
    <pre id="pane-sources" style="display:none"></pre>
    <div class="statusbar">
      <span class="dot" id="dot"></span>
      <span id="status">idle</span>
      <span id="stats" style="margin-left:auto"></span>
    </div>
  </section>
</main>

<script>
const $ = (id) => document.getElementById(id);
let state = { folder: '', apps: [], files: [] };
let logLines = [];
let busy = false;

function setStatus(kind, text) {
  $('dot').className = 'dot' + (kind ? ' ' + kind : '');
  $('status').textContent = text;
}

function appendLog(text) {
  for (const line of String(text).split('\\n')) {
    if (!line) continue;
    logLines.push(line);
  }
  if (logLines.length > 4000) logLines = logLines.slice(-4000);
  renderLog();
}

function renderLog() {
  const pane = $('pane-log');
  const nearBottom = pane.scrollTop + pane.clientHeight >= pane.scrollHeight - 40;
  pane.innerHTML = logLines.map(l => {
    let cls = 'info';
    if (l.includes('[error]')) cls = 'error';
    else if (l.includes('[warn]')) cls = 'warn';
    const esc = l.replace(/&/g, '&amp;').replace(/</g, '&lt;');
    return '<span class="log-line ' + cls + '">' + esc + '</span>';
  }).join('');
  if (nearBottom) pane.scrollTop = pane.scrollHeight;
}

function renderApps() {
  const ul = $('apps');
  ul.innerHTML = '';
  state.selected = null;
  if (!state.apps.length) {
    ul.innerHTML = '<li style="color:var(--dim);cursor:default">no .jar found</li>';
    return;
  }
  for (const app of state.apps) {
    const li = document.createElement('li');
    li.innerHTML = '<span class="sz">' + app.size + '</span>' + app.name;
    li.title = app.path;
    li.onclick = () => selectApp(li, app.path);
    ul.appendChild(li);
  }
}

function selectApp(li, path) {
  document.querySelectorAll('ul.files li').forEach(e => e.classList.remove('active'));
  li.classList.add('active');
  state.selected = path;
}

async function scan() {
  const folder = $('folder').value.trim();
  setStatus('busy', 'scanning');
  const res = await fetch('/api/scan?folder=' + encodeURIComponent(folder));
  const data = await res.json();
  state = Object.assign(state, data);
  $('folder').value = state.folder;
  renderApps();
  setStatus('', data.apps.length + ' application(s)');
}

async function recompile() {
  if (busy) return;
  const target = state.selected || '';
  if (!target) { appendLog('[error] select an application first'); return; }
  busy = true;
  $('run').disabled = true;
  logLines = [];
  renderLog();
  setStatus('busy', 'recompiling ' + target.split(/[\\\\/]/).pop());
  const body = {
    jar: target,
    build: $('optBuild').checked,
    exe: $('optExe').checked,
    stubs: $('optStubs').checked,
    lines: $('optLines').checked
  };
  try {
    const res = await fetch('/api/recompile', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const data = await res.json();
    appendLog(data.log || '');
    $('stats').textContent = data.summary || '';
    setStatus(data.ok ? 'ok' : 'err', data.ok ? 'done' : 'failed');
    if (data.ok) $('launch').disabled = false;
    await loadFiles();
  } catch (e) {
    appendLog('[error] ' + e);
    setStatus('err', 'failed');
  } finally {
    busy = false;
    $('run').disabled = false;
  }
}

async function loadFiles() {
  const res = await fetch('/api/files');
  const data = await res.json();
  state.files = data.files || [];
  if (document.querySelector('.tabs div.active').dataset.tab === 'sources') {
    renderFileList();
  }
}

function renderFileList() {
  const pane = $('pane-sources');
  if (!state.files.length) { pane.textContent = 'no generated sources yet'; return; }
  pane.innerHTML = state.files.map(f =>
    '<span class="log-line" style="cursor:pointer" onclick="openFile(\\'' + encodeURIComponent(f) + '\\')">'
    + f.replace(/&/g,'&amp;').replace(/</g,'&lt;') + '</span>').join('');
}

async function openFile(path) {
  const res = await fetch('/api/file?path=' + path);
  const data = await res.json();
  const pane = $('pane-sources');
  if (data.error) {
    pane.textContent = data.error;
    return;
  }
  pane.innerHTML = '<span class="log-line" style="cursor:pointer;color:var(--accent)" '
    + 'onclick="renderFileList()">&lt; back to the list</span>\\n\\n'
    + (data.content || '').replace(/&/g,'&amp;').replace(/</g,'&lt;');
  pane.scrollTop = 0;
}

document.querySelectorAll('.tabs div').forEach(tab => {
  tab.onclick = () => {
    document.querySelectorAll('.tabs div').forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    const showLog = tab.dataset.tab === 'log';
    $('pane-log').style.display = showLog ? '' : 'none';
    $('pane-sources').style.display = showLog ? 'none' : '';
    if (!showLog) renderFileList();
  };
});

$('scan').onclick = scan;
$('run').onclick = recompile;
$('clear').onclick = () => { logLines = []; renderLog(); };
$('launch').onclick = async () => {
  setStatus('busy', 'starting');
  const res = await fetch('/api/run', { method: 'POST' });
  const data = await res.json();
  appendLog(data.log || '');
  setStatus(data.ok ? 'ok' : 'err', data.ok ? 'running' : 'failed');
};

(async function init() {
  const res = await fetch('/api/state');
  const data = await res.json();
  state = Object.assign(state, data);
  $('version').textContent = 'v' + (data.version || '');
  $('folder').value = state.folder;
  renderApps();
  if (data.built) $('launch').disabled = false;
  setStatus('', 'idle');
})();
</script>
</body>
</html>
""";
}
