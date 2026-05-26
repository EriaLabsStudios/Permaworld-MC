const state = {
  admins: [],
  selectedAdmin: "",
  players: [],
  selectedPlayer: null,
  filter: "DEATH",
  records: [],
  selectedRecord: null,
  stats: null,
};

const adminSelect = document.querySelector("#adminSelect");
const playerSearch = document.querySelector("#playerSearch");
const playerList = document.querySelector("#playerList");
const filterBar = document.querySelector("#filterBar");
const recordList = document.querySelector("#recordList");
const recordDetail = document.querySelector("#recordDetail");

async function loadJson(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

async function boot() {
  const session = await loadJson("/api/session");
  state.admins = session.admins ?? [];
  state.selectedAdmin = state.admins[0] ?? "";
  renderAdmins();

  const playersPayload = await loadJson("/api/players");
  state.players = playersPayload.players ?? [];
  state.selectedPlayer = state.players[0] ?? null;
  renderPlayers();
  bindEvents();

  if (state.selectedPlayer) {
    await loadCurrentView();
  } else {
    recordList.innerHTML = '<div class="empty">No players with records yet.</div>';
  }
}

function bindEvents() {
  adminSelect.addEventListener("change", () => {
    state.selectedAdmin = adminSelect.value;
  });
  playerSearch.addEventListener("input", renderPlayers);
  filterBar.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-filter]");
    if (!button) {
      return;
    }
    state.filter = button.dataset.filter;
    [...filterBar.querySelectorAll(".mc-button")].forEach((node) => {
      node.classList.toggle("is-active", node === button);
    });
    await loadCurrentView();
  });
}

function renderAdmins() {
  adminSelect.innerHTML = "";
  for (const admin of state.admins) {
    const option = document.createElement("option");
    option.value = admin;
    option.textContent = admin;
    adminSelect.append(option);
  }
}

function renderPlayers() {
  const term = playerSearch.value.trim().toLowerCase();
  playerList.innerHTML = "";
  const filtered = state.players.filter((player) => player.playerName.toLowerCase().includes(term));
  for (const player of filtered) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "player-card";
    if (state.selectedPlayer && state.selectedPlayer.uuid === player.uuid) {
      card.classList.add("active");
    }
    card.innerHTML = `
      <h3>${player.playerName}</h3>
      <div class="record-meta">${player.recordCount} logs</div>
      <div class="record-note">${player.lastReason} · ${formatTime(player.lastTimestamp)}</div>
    `;
    card.addEventListener("click", async () => {
      state.selectedPlayer = player;
      renderPlayers();
      await loadCurrentView();
    });
    playerList.append(card);
  }
}

async function loadCurrentView() {
  if (state.filter === "STATS") {
    await loadStats();
    return;
  }
  await loadRecords();
}

async function loadRecords() {
  state.selectedRecord = null;
  state.stats = null;
  const payload = await loadJson(`/api/players/${state.selectedPlayer.uuid}/records?filter=${encodeURIComponent(state.filter)}`);
  state.records = payload.records ?? [];
  renderRecords();
  if (state.filter === "ADVANCEMENT_DONE" && state.records[0]) {
    renderAdvancementPlaceholder();
  } else if (state.records[0]) {
    await selectRecord(state.records[0].id);
  } else {
    recordDetail.innerHTML = state.filter === "ADVANCEMENT_DONE"
      ? '<div class="empty">No advancements recorded yet.</div>'
      : '<div class="empty">No records for this filter.</div>';
  }
}

async function loadStats() {
  state.records = [];
  state.selectedRecord = null;
  state.stats = await loadJson(`/api/players/${state.selectedPlayer.uuid}/stats`);
  renderStats();
  renderStatsDetail();
}

function renderRecords() {
  recordList.classList.remove("stats-view");
  recordList.classList.toggle("advancement-view", state.filter === "ADVANCEMENT_DONE");
  recordList.innerHTML = "";

  if (state.filter === "ADVANCEMENT_DONE") {
    renderAdvancementGrid();
    return;
  }

  for (const record of state.records) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "record-card";
    if (state.selectedRecord && state.selectedRecord.id === record.id) {
      card.classList.add("active");
    }
    const icon = record.advancementIconItemId
      ? renderItemIcon(record.advancementIconItemId, record.advancementIconLabel || record.advancementTitle || record.reason)
      : "";
    card.innerHTML = `
      <div class="record-main">
        ${icon ? `<div class="slot-icon large">${icon}</div>` : ""}
        <div>
          <div class="record-header">
            <h3>${record.reason}</h3>
            <span class="reason-pill">${record.itemCount} items</span>
          </div>
          <div class="record-meta">${formatTime(record.timestamp)} · ${record.dimension || "unknown"}</div>
          <div class="record-note">${record.summary}</div>
        </div>
      </div>
    `;
    card.addEventListener("click", () => selectRecord(record.id));
    recordList.append(card);
  }
}

function renderAdvancementGrid() {
  if (!state.records.length) {
    recordList.innerHTML = '<div class="empty">No advancements recorded yet.</div>';
    return;
  }

  const grid = document.createElement("div");
  grid.className = "advancement-grid";

  for (const record of state.records) {
    const tile = document.createElement("button");
    tile.type = "button";
    tile.className = "advancement-tile";
    if (state.selectedRecord && state.selectedRecord.id === record.id) {
      tile.classList.add("active");
    }
    tile.setAttribute("title", record.advancementTitle || record.reason);
    tile.setAttribute("aria-label", record.advancementTitle || record.reason);
    tile.innerHTML = `
      <div class="slot-icon large">${renderItemIcon(record.advancementIconItemId, record.advancementIconLabel || record.advancementTitle || record.reason)}</div>
    `;
    tile.addEventListener("click", () => selectRecord(record.id));
    grid.append(tile);
  }

  recordList.append(grid);
}

function renderStats() {
  recordList.classList.add("stats-view");
  recordList.classList.remove("advancement-view");
  if (!state.stats?.available) {
    recordList.innerHTML = `<div class="status error">${state.stats?.message || "Statistics unavailable."}</div>`;
    return;
  }
  const highlights = (state.stats.highlights ?? []).map((stat) => `
    <div class="stat-card">
      <div class="record-meta">${stat.label}</div>
      <div class="stat-value">${stat.formatted}</div>
    </div>
  `).join("");

  recordList.innerHTML = `
    <div class="stat-grid">${highlights}</div>
    <div class="leaderboards">
      ${renderLeaderboard("Blocks Mined", state.stats.blocksMined)}
      ${renderLeaderboard("Items Crafted", state.stats.itemsCrafted)}
      ${renderLeaderboard("Items Picked Up", state.stats.itemsPickedUp)}
      ${renderLeaderboard("Entities Killed", state.stats.entitiesKilled)}
    </div>
  `;
}

function renderLeaderboard(title, entries = []) {
  return `
    <section class="leaderboard">
      <h3>${title}</h3>
      <div class="leaderboard-list">
        ${entries.length
          ? entries.map((entry) => `
              <div class="leaderboard-row">
                <div>${entry.label}<div class="record-note">${entry.key}</div></div>
                <div>${entry.value}</div>
              </div>`).join("")
          : '<div class="leaderboard-row"><div>No data.</div><div>0</div></div>'}
      </div>
    </section>
  `;
}

async function selectRecord(recordId) {
  state.selectedRecord = await loadJson(`/api/players/${state.selectedPlayer.uuid}/records/${encodeURIComponent(recordId)}`);
  renderRecords();
  renderDetail();
}

function renderDetail() {
  const record = state.selectedRecord;
  if (!record) {
    recordDetail.innerHTML = '<div class="empty">Select a record.</div>';
    return;
  }

  if (record.advancementTitle) {
    renderAdvancementDetail(record);
    return;
  }

  const items = (record.items ?? []).slice(0, 8).map((item) => `
    <div class="item-row">
      <div class="slot-icon"></div>
      <div>${item.itemId}<div class="record-note">${item.section}/${item.slot}${item.customName ? ` · ${item.customName}` : ""}</div></div>
      <div>x${item.count}</div>
    </div>
  `).join("");

  recordDetail.innerHTML = `
    <div class="detail-head">
      <h3>${record.reason}</h3>
      <span class="reason-pill">${record.itemCount} items</span>
    </div>
    <dl class="detail-grid">
      <dt>Player</dt><dd>${record.playerName}</dd>
      <dt>When</dt><dd>${formatTime(record.timestamp)}</dd>
      <dt>Dimension</dt><dd>${record.dimension || "unknown"}</dd>
      <dt>Id</dt><dd>${record.id}</dd>
    </dl>
    ${record.restorable ? '<button id="restoreButton" class="mc-button restore">Create recovery chest</button>' : ""}
    <div class="item-list">${items || '<div class="empty">No items stored.</div>'}</div>
    <div id="restoreStatus"></div>
  `;

  const restoreButton = document.querySelector("#restoreButton");
  if (restoreButton) {
    restoreButton.addEventListener("click", restoreSelectedRecord);
  }
}

function renderAdvancementDetail(record) {
  recordDetail.innerHTML = `
    <div class="detail-head">
      <h3>${record.advancementTitle}</h3>
      <span class="reason-pill">${record.advancementFrame || "Advancement"}</span>
    </div>
    <div class="record-main">
      <div class="slot-icon large">${renderItemIcon(record.advancementIconItemId, record.advancementIconLabel || record.advancementTitle)}</div>
      <div>
        <div class="advancement-description">${record.advancementDescription || "No description available."}</div>
        <div class="advancement-meta">
          <span class="reason-pill">${formatTime(record.timestamp)}</span>
          <span class="reason-pill">${record.dimension || "unknown"}</span>
        </div>
      </div>
    </div>
    <dl class="detail-grid">
      <dt>Player</dt><dd>${record.playerName}</dd>
      <dt>Advancement</dt><dd>${record.advancementId || "unknown"}</dd>
      <dt>Criterion</dt><dd>${record.criterion || "completed"}</dd>
      <dt>Icon Item</dt><dd>${record.advancementIconItemId || "unknown"}</dd>
    </dl>
  `;
}

function renderAdvancementPlaceholder() {
  recordDetail.innerHTML = '<div class="empty">Select an advancement to inspect it here.</div>';
}

function renderStatsDetail() {
  if (!state.stats?.available) {
    recordDetail.innerHTML = `<div class="status error">${state.stats?.message || "Statistics unavailable."}</div>`;
    return;
  }
  const highlights = (state.stats.highlights ?? []).map((stat) => `
    <div class="item-row">
      <div class="slot-icon"></div>
      <div>${stat.label}</div>
      <div>${stat.formatted}</div>
    </div>
  `).join("");

  recordDetail.innerHTML = `
    <div class="detail-head">
      <h3>Statistics</h3>
      <span class="reason-pill">${state.stats.playerName}</span>
    </div>
    <div class="item-list">${highlights}</div>
  `;
}

async function restoreSelectedRecord() {
  if (!state.selectedAdmin) {
    showStatus("Select an admin first.", false);
    return;
  }
  try {
    const result = await loadJson(
      `/api/players/${state.selectedPlayer.uuid}/records/${encodeURIComponent(state.selectedRecord.id)}/restore?admin=${encodeURIComponent(state.selectedAdmin)}`,
      { method: "POST" }
    );
    showStatus(`${result.message} (${result.restoredStacks} stacks)`, Boolean(result.ok));
  } catch (error) {
    showStatus(error.message, false);
  }
}

function showStatus(message, ok) {
  const status = document.querySelector("#restoreStatus");
  if (!status) {
    return;
  }
  status.className = `status ${ok ? "ok" : "error"}`;
  status.textContent = message;
}

function renderItemIcon(itemId, label) {
  if (!itemId) {
    return `<div class="slot-fallback">${shortLabel(label)}</div>`;
  }
  return `<img src="/api/item-texture?itemId=${encodeURIComponent(itemId)}" alt="${escapeHtml(label || itemId)}" onerror="this.replaceWith(document.createRange().createContextualFragment('<div class=&quot;slot-fallback&quot;>${shortLabel(label || itemId)}</div>'))">`;
}

function shortLabel(value) {
  return escapeHtml((value || "?").split(" ").slice(0, 2).map((part) => part[0] || "").join("").toUpperCase() || "?");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatTime(value) {
  if (!value) {
    return "unknown";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

boot().catch((error) => {
  recordDetail.innerHTML = `<div class="status error">${error.message}</div>`;
});
