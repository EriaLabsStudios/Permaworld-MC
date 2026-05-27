const state = {
  admins: [],
  selectedAdmin: "",
  players: [],
  selectedPlayer: null,
  filter: "ALL",
  records: [],
  selectedRecord: null,
  stats: null,
};

const adminSelect = document.querySelector("#adminSelect");
const myUserSelect = document.querySelector("#myUserSelect");
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

  renderMyUsers();

  const savedUserUuid = localStorage.getItem("permaworld_my_user");
  let foundPlayer = null;
  if (savedUserUuid) {
    foundPlayer = state.players.find((p) => p.uuid === savedUserUuid);
  }

  if (foundPlayer) {
    state.selectedPlayer = foundPlayer;
    myUserSelect.value = savedUserUuid;
  } else {
    state.selectedPlayer = state.players[0] ?? null;
    myUserSelect.value = state.selectedPlayer ? state.selectedPlayer.uuid : "";
  }

  // Load glossy opacity from localStorage
  const savedGlossy = localStorage.getItem("permaworld_glossy_opacity");
  const initialGlossy = savedGlossy !== null ? parseInt(savedGlossy, 10) : 50;
  const glossySlider = document.querySelector("#glossySlider");
  if (glossySlider) {
    glossySlider.value = initialGlossy;
    updateGlossy(initialGlossy);
  }

  renderPlayers();
  bindEvents();

  if (state.selectedPlayer) {
    await loadCurrentView();
  } else {
    recordList.innerHTML = '<div class="empty">No players with records yet.</div>';
  }
  initTooltip();
}

function bindEvents() {
  adminSelect.addEventListener("change", () => {
    state.selectedAdmin = adminSelect.value;
  });
  myUserSelect.addEventListener("change", async () => {
    const selectedUuid = myUserSelect.value;
    if (selectedUuid) {
      localStorage.setItem("permaworld_my_user", selectedUuid);
      const player = state.players.find((p) => p.uuid === selectedUuid);
      if (player) {
        state.selectedPlayer = player;
      }
    } else {
      localStorage.removeItem("permaworld_my_user");
      if (state.players[0]) {
        state.selectedPlayer = state.players[0];
      }
    }
    renderPlayers();
    await loadCurrentView();
  });
  
  const glossySlider = document.querySelector("#glossySlider");
  if (glossySlider) {
    glossySlider.addEventListener("input", () => {
      const val = parseInt(glossySlider.value, 10);
      localStorage.setItem("permaworld_glossy_opacity", val);
      updateGlossy(val);
    });
  }

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

function renderMyUsers() {
  myUserSelect.innerHTML = "";
  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = "-- Seleccionar --";
  myUserSelect.append(placeholder);

  for (const player of state.players) {
    const option = document.createElement("option");
    option.value = player.uuid;
    option.textContent = player.playerName;
    myUserSelect.append(option);
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
      <img class="player-head" src="https://minotar.net/helm/${player.playerName}/32.png" alt="${player.playerName}">
      <div class="player-info-wrapper">
        <h3>${player.playerName}</h3>
        <div class="record-meta">${player.recordCount} logs · ${player.lastReason}</div>
      </div>
    `;
    card.addEventListener("click", async () => {
      state.selectedPlayer = player;
      myUserSelect.value = player.uuid;
      localStorage.setItem("permaworld_my_user", player.uuid);
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
  try {
    state.selectedRecord = null;
    state.stats = null;

    if (state.filter === "ADVANCEMENT_DONE" && !state.allAdvancements) {
      try {
        state.allAdvancements = await loadJson("/api/advancements");
      } catch (e) {
        console.warn("Failed to load advancements from API, falling back to completed only:", e);
        state.allAdvancements = [];
      }
    }

    const payload = await loadJson(`/api/players/${state.selectedPlayer.uuid}/records?filter=${encodeURIComponent(state.filter)}`);
    state.records = payload.records ?? [];
    renderRecords();
    
    if (state.filter === "ADVANCEMENT_DONE") {
      renderAdvancementPlaceholder();
    } else if (state.records[0]) {
      await selectRecord(state.records[0].id);
    } else {
      recordDetail.innerHTML = '<div class="empty">No records for this filter.</div>';
    }
  } catch (error) {
    console.error("Error loading records:", error);
    recordList.innerHTML = `<div class="status error">Error loading records: ${error.message}</div>`;
  }
}

async function loadStats() {
  state.records = [];
  state.selectedRecord = null;
  state.stats = await loadJson(`/api/players/${state.selectedPlayer.uuid}/stats`);
  renderStats();
  renderStatsDetail();
}

function getRecordIcon(record) {
  if (record.advancementIconItemId) {
    return record.advancementIconItemId;
  }
  
  const reason = (record.reason || "").toUpperCase();
  
  if (reason.includes("DEATH")) {
    return "minecraft:skeleton_skull";
  }
  if (reason.includes("CURRENT_STATE") || reason.includes("CURRENT STATE")) {
    return "minecraft:totem_of_undying";
  }
  if (reason.includes("JOIN")) {
    return "minecraft:compass";
  }
  if (reason.includes("DISCONNECT") || reason.includes("QUIT") || reason.includes("LEFT")) {
    return "minecraft:barrier";
  }
  if (reason.includes("RESPAWN")) {
    return "minecraft:red_bed";
  }
  if (reason.includes("DIMENSION")) {
    return "minecraft:ender_eye";
  }
  if (reason.includes("GAME_MODE") || reason.includes("GAMEMODE")) {
    return "minecraft:command_block";
  }
  if (reason.includes("SNAPSHOT")) {
    return "minecraft:chest";
  }
  if (reason.includes("PATH") || reason.includes("ROUTE") || reason.includes("WALK")) {
    return "minecraft:map";
  }
  if (reason.includes("STRUCTURE") || reason.includes("DISCOVERED")) {
    return "minecraft:filled_map";
  }
  
  // General fallback
  return "minecraft:paper";
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
    
    const iconId = getRecordIcon(record);
    const iconLabel = record.advancementIconLabel || record.advancementTitle || record.reason;
    const iconHtml = renderItemIcon(iconId, iconLabel);

    card.innerHTML = `
      <div class="record-main">
        <div class="slot-icon large">${iconHtml}</div>
        <div class="record-content-wrapper">
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
  try {
    const hasAllAdv = state.allAdvancements && state.allAdvancements.length > 0;
    
    if (!hasAllAdv && (!state.records || !state.records.length)) {
      recordList.innerHTML = '<div class="empty">No advancements recorded yet.</div>';
      return;
    }

    const grid = document.createElement("div");
    grid.className = "advancement-grid";

    if (hasAllAdv) {
      for (const adv of state.allAdvancements) {
        if (!adv || !adv.id) continue;

        const completedRecord = (state.records || []).find((r) => r && r.advancementId === adv.id);
        const isCompleted = !!completedRecord;

        const tile = document.createElement("button");
        tile.type = "button";
        tile.className = "advancement-tile";
        
        let frameClass = (adv.frame || "task").toLowerCase();
        if (frameClass.includes("challenge")) {
          frameClass = "challenge";
        } else if (frameClass.includes("goal")) {
          frameClass = "goal";
        } else {
          frameClass = "task";
        }
        
        tile.classList.add(frameClass);
        if (!isCompleted) {
          tile.classList.add("locked");
        }
        
        const isSelected = state.selectedRecord && 
          (isCompleted 
            ? (completedRecord && state.selectedRecord.id === completedRecord.id) 
            : state.selectedRecord.advancementId === adv.id);
            
        if (isSelected) {
          tile.classList.add("active");
        }

        // Add custom metadata for the tooltip engine
        tile.dataset.title = adv.title || "Advancement";
        tile.dataset.description = adv.description || "";
        tile.dataset.frame = adv.frame || "task";
        tile.dataset.status = isCompleted ? "COMPLETED" : "LOCKED";
        if (isCompleted && completedRecord) {
          tile.dataset.dimension = completedRecord.dimension || "unknown";
          tile.dataset.timestamp = formatTime(completedRecord.timestamp);
        }

        tile.setAttribute("aria-label", adv.title || "Advancement");
        tile.innerHTML = `
          <div class="slot-icon large">${renderItemIcon(adv.iconItemId, adv.iconLabel || adv.title)}</div>
        `;
        
        if (isCompleted && completedRecord) {
          tile.addEventListener("click", () => selectRecord(completedRecord.id));
        } else {
          tile.addEventListener("click", () => selectLockedAdvancement(adv));
        }
        grid.append(tile);
      }
    } else {
      // Fallback: render only completed ones from records
      for (const record of (state.records || [])) {
        if (!record) continue;
        const tile = document.createElement("button");
        tile.type = "button";
        tile.className = "advancement-tile";
        
        let frameClass = (record.advancementFrame || "task").toLowerCase();
        if (frameClass.includes("challenge")) {
          frameClass = "challenge";
        } else if (frameClass.includes("goal")) {
          frameClass = "goal";
        } else {
          frameClass = "task";
        }
        
        tile.classList.add(frameClass);
        if (state.selectedRecord && state.selectedRecord.id === record.id) {
          tile.classList.add("active");
        }
        
        tile.dataset.title = record.advancementTitle || record.reason || "Advancement";
        tile.dataset.description = record.advancementDescription || "";
        tile.dataset.frame = record.advancementFrame || "task";
        tile.dataset.status = "COMPLETED";
        tile.dataset.dimension = record.dimension || "unknown";
        tile.dataset.timestamp = formatTime(record.timestamp);

        tile.setAttribute("aria-label", record.advancementTitle || record.reason);
        tile.innerHTML = `
          <div class="slot-icon large">${renderItemIcon(record.advancementIconItemId, record.advancementIconLabel || record.advancementTitle || record.reason)}</div>
        `;
        tile.addEventListener("click", () => selectRecord(record.id));
        grid.append(tile);
      }
    }

    recordList.innerHTML = "";
    recordList.append(grid);
  } catch (error) {
    console.error("Error rendering advancement grid:", error);
    recordList.innerHTML = `<div class="status error">Error rendering achievements: ${error.message}</div>`;
  }
}

function renderStats() {
  recordList.classList.add("stats-view");
  recordList.classList.remove("advancement-view");
  if (!state.stats?.available) {
    recordList.innerHTML = `<div class="status error">${state.stats?.message || "Statistics unavailable."}</div>`;
    return;
  }
  const highlights = (state.stats.highlights ?? []).map((stat) => {
    const icon = getStatIcon(stat.key);
    return `
      <div class="stat-card" data-stat-key="${stat.key}" data-label="${escapeHtml(stat.label)}" data-value="${escapeHtml(stat.formatted)}">
        <div class="stat-card-main">
          ${icon ? `<div class="slot-icon">${renderItemIcon(icon, stat.label)}</div>` : ""}
          <div>
            <div class="record-meta">${stat.label}</div>
            <div class="stat-value">${stat.formatted}</div>
          </div>
        </div>
      </div>
    `;
  }).join("");

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

  const items = (record.items ?? []).slice(0, 36).map((item) => `
    <div class="item-row" data-item-id="${item.itemId}" data-count="${item.count}" data-section="${item.section}" data-slot="${item.slot}" data-custom-name="${escapeHtml(item.customName || '')}">
      <div class="slot-icon">${renderItemIcon(item.itemId, item.customName || item.itemId)}</div>
      <div>
        <div style="font-weight: bold; color: #ffffff;">${formatItemName(item.itemId)}</div>
        <div class="record-note">${item.itemId} · ${item.section}/${item.slot}${item.customName ? ` · ${item.customName}` : ""}</div>
      </div>
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
  const isLocked = !!record.locked;
  const statusPill = isLocked 
    ? '<span class="reason-pill" style="background: rgba(183, 74, 74, 0.4); border-color: #b74a4a; color: #ff8888;">LOCKED</span>' 
    : `<span class="reason-pill">${record.advancementFrame || "Advancement"}</span>`;
  
  const titleStyle = isLocked 
    ? 'font-weight: normal; color: #888888;' 
    : 'font-weight: bold; color: var(--gold-1); text-shadow: 1px 1px 0px #000;';

  recordDetail.innerHTML = `
    <div class="detail-head">
      <h3 style="${titleStyle}">${record.advancementTitle}</h3>
      ${statusPill}
    </div>
    <div class="record-main" style="${isLocked ? 'filter: grayscale(1) opacity(0.5);' : ''}">
      <div class="slot-icon large">${renderItemIcon(record.advancementIconItemId, record.advancementIconLabel || record.advancementTitle)}</div>
      <div>
        <div class="advancement-description" style="${isLocked ? 'color: #888888;' : ''}">${record.advancementDescription || "No description available."}</div>
        ${!isLocked ? `
        <div class="advancement-meta">
          <span class="reason-pill">${formatTime(record.timestamp)}</span>
          <span class="reason-pill">${record.dimension || "unknown"}</span>
        </div>
        ` : ""}
      </div>
    </div>
    <dl class="detail-grid">
      <dt>Player</dt><dd>${state.selectedPlayer ? state.selectedPlayer.playerName : "unknown"}</dd>
      <dt>Advancement</dt><dd>${record.advancementId || "unknown"}</dd>
      ${!isLocked ? `<dt>Criterion</dt><dd>${record.criterion || "completed"}</dd>` : ""}
      <dt>Icon Item</dt><dd>${record.advancementIconItemId || "unknown"}</dd>
    </dl>
  `;
}

function selectLockedAdvancement(adv) {
  state.selectedRecord = {
    advancementId: adv.id,
    advancementTitle: adv.title,
    advancementDescription: adv.description,
    advancementFrame: adv.frame,
    advancementIconItemId: adv.iconItemId,
    advancementIconLabel: adv.iconLabel,
    locked: true
  };
  renderRecords();
  renderDetail();
}

function renderAdvancementPlaceholder() {
  recordDetail.innerHTML = '<div class="empty">Select an advancement to inspect it here.</div>';
}

function renderStatsDetail() {
  const hasStats = !!state.stats?.available;
  const firstJoined = state.stats?.firstJoined ? formatTime(state.stats.firstJoined) : "unknown";
  const lastConnected = state.stats?.lastConnected ? formatTime(state.stats.lastConnected) : "unknown";
  const lastPos = state.stats?.lastKnownPosition || "unknown";
  const playerName = state.stats?.playerName || (state.selectedPlayer ? state.selectedPlayer.playerName : "Player");
  
  const isOnline = hasStats && !!state.stats?.online;
  const statusBadge = isOnline 
    ? '<span class="reason-pill" style="background: rgba(85, 255, 85, 0.2); border-color: #55ff55; color: #55ff55;">ONLINE</span>'
    : '<span class="reason-pill" style="background: rgba(183, 74, 74, 0.4); border-color: #b74a4a; color: #ff8888;">OFFLINE</span>';

  const highlights = hasStats ? (state.stats.highlights ?? []).map((stat) => {
    const icon = getStatIcon(stat.key);
    return `
      <div class="item-row" data-stat-key="${stat.key}" data-label="${escapeHtml(stat.label)}" data-value="${escapeHtml(stat.formatted)}">
        <div class="slot-icon">${renderItemIcon(icon, stat.label)}</div>
        <div style="font-weight: bold; color: #ffffff;">${stat.label}</div>
        <div>${stat.formatted}</div>
      </div>
    `;
  }).join("") : `<div class="status error">${state.stats?.message || "Statistics unavailable."}</div>`;

  recordDetail.innerHTML = `
    <div class="detail-head">
      <h3>Player Summary</h3>
      <div style="display: flex; gap: 8px; align-items: center;">
        <span class="reason-pill">${playerName}</span>
        ${statusBadge}
      </div>
    </div>
    <div style="margin: 14px 0; border: 3px solid #050505; background: rgba(0, 0, 0, 0.24); padding: 12px; box-shadow: 3px 3px 0 #0a0a0a;">
      <div style="font-weight: bold; color: var(--gold-1); margin-bottom: 8px; border-bottom: 2px solid rgba(255,255,255,0.08); padding-bottom: 4px;">General Info</div>
      <div style="font-size: 13px; margin-bottom: 6px; color: var(--muted); text-align: left;">First Joined: <span style="color: #ffffff; font-weight: bold;">${firstJoined}</span></div>
      <div style="font-size: 13px; margin-bottom: 6px; color: var(--muted); text-align: left;">Last Connected: <span style="color: #ffffff; font-weight: bold;">${lastConnected}</span></div>
      <div style="font-size: 13px; color: var(--muted); text-align: left;">Last Position: <span style="color: #ffffff; font-weight: bold;">${lastPos}</span></div>
    </div>
    <div style="font-weight: bold; margin-bottom: 8px; font-size: 14px; text-transform: uppercase; color: var(--muted); text-align: left;">Activity Metrics</div>
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

// Map stats keys to standard premium item textures
function getStatIcon(key) {
  switch (key) {
    case "deaths":
      return "minecraft:skeleton_skull";
    case "play_time":
      return "minecraft:clock";
    case "distance":
      return "minecraft:leather_boots";
    case "mob_kills":
      return "minecraft:diamond_sword";
    case "time_since_death":
      return "minecraft:recovery_compass";
    case "days_survived":
      return "minecraft:sunflower";
    case "food_eaten":
      return "minecraft:cooked_beef";
    case "animals_bred":
      return "minecraft:wheat";
    case "hostile_kills":
      return "minecraft:zombie_head";
    case "passive_kills":
      return "minecraft:pig_spawn_egg";
    case "tools_broken":
      return "minecraft:wooden_pickaxe";
    case "structures_discovered":
      return "minecraft:spyglass";
    default:
      return "";
  }
}

function getStatDescription(key) {
  switch (key) {
    case "deaths":
      return "Total times this player has died in the world.";
    case "play_time":
      return "Total active playing time logged on the server.";
    case "distance":
      return "Total distance traversed by the player across all dimensions.";
    case "mob_kills":
      return "Number of aggressive and passive mobs defeated.";
    case "time_since_death":
      return "Real-life time elapsed since this player's last death.";
    case "days_survived":
      return "Minecraft in-game days successfully survived since the player's last death.";
    case "food_eaten":
      return "Total food items consumed by this player.";
    case "animals_bred":
      return "Number of baby animals successfully bred by the player.";
    case "hostile_kills":
      return "Hostile monsters defeated by this player.";
    case "passive_kills":
      return "Passive and neutral animals/mobs defeated.";
    case "tools_broken":
      return "Total number of tools, weapons, or items completely broken through use.";
    case "structures_discovered":
      return "Unique historical structures discovered and explored.";
    default:
      return "";
  }
}

function formatItemName(itemId) {
  if (!itemId) return "";
  const parts = itemId.split(":");
  const name = parts[1] || parts[0] || itemId;
  return name
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function initTooltip() {
  const tooltip = document.querySelector("#mcTooltip");
  if (!tooltip) return;

  let mouseX = 0;
  let mouseY = 0;

  document.addEventListener("mousemove", (e) => {
    mouseX = e.clientX;
    mouseY = e.clientY;
    if (tooltip.style.display === "block") {
      positionTooltip();
    }
  });

  function positionTooltip() {
    const offsetX = 15;
    const offsetY = -15;
    
    let x = mouseX + offsetX;
    let y = mouseY + offsetY;
    
    const width = tooltip.offsetWidth;
    const height = tooltip.offsetHeight;
    
    if (x + width > window.innerWidth) {
      x = mouseX - width - 15;
    }
    if (y + height > window.innerHeight) {
      y = window.innerHeight - height - 15;
    }
    if (x < 10) x = 10;
    if (y < 10) y = 10;

    tooltip.style.left = `${x}px`;
    tooltip.style.top = `${y}px`;
  }

  document.addEventListener("mouseover", (e) => {
    const itemRow = e.target.closest(".item-row");
    const advancementTile = e.target.closest(".advancement-tile");
    const statCard = e.target.closest(".stat-card");
    const mcButton = e.target.closest(".mc-button");
    const playerCard = e.target.closest(".player-card");
    const recordCard = e.target.closest(".record-card");

    let content = "";

    if (itemRow) {
      const itemId = itemRow.dataset.itemId;
      if (itemId) {
        const count = itemRow.dataset.count;
        const section = itemRow.dataset.section;
        const slot = itemRow.dataset.slot;
        const customName = itemRow.dataset.customName;

        content = `<div class="tooltip-title" style="color: #ffffff; font-weight: bold;">${customName || formatItemName(itemId)}</div>`;
        if (customName) {
          content += `<div class="tooltip-sub" style="color: #55ffff; font-style: italic;">Original: ${formatItemName(itemId)}</div>`;
        }
        content += `<div style="color: #aaaaaa; margin-top: 4px;">ID: <span style="color: #55ff55;">${itemId}</span></div>`;
        content += `<div style="color: #aaaaaa;">Location: <span style="color: #ff55ff;">${section}</span> (Slot ${slot})</div>`;
        content += `<div style="color: #aaaaaa;">Amount: <span style="color: #ffff55;">x${count}</span></div>`;
      } else if (itemRow.dataset.statKey) {
        // Special case: stat rows in detail view
        const label = itemRow.dataset.label;
        const val = itemRow.dataset.value;
        const desc = getStatDescription(itemRow.dataset.statKey);
        content = `<div class="tooltip-title" style="color: #f3e46b; font-weight: bold;">${label}</div>`;
        content += `<div style="color: #ffffff; font-size: 14px; margin-top: 4px;">Value: ${val}</div>`;
        if (desc) {
          content += `<div style="color: #bdc2c8; margin-top: 6px; font-size: 12px;">${desc}</div>`;
        }
      }
    } else if (advancementTile) {
      const title = advancementTile.dataset.title;
      if (title) {
        const desc = advancementTile.dataset.description;
        const frame = advancementTile.dataset.frame || "task";
        const dimension = advancementTile.dataset.dimension;
        const timestamp = advancementTile.dataset.timestamp;
        const status = advancementTile.dataset.status || "COMPLETED";
        const isCompleted = status === "COMPLETED";

        let titleColor = "#ffffff";
        if (frame === "challenge") titleColor = "#d334b9";
        else if (frame === "goal") titleColor = "#f3e46b";

        content = `<div class="tooltip-title" style="color: ${isCompleted ? titleColor : '#888888'}; font-weight: bold; ${!isCompleted ? 'text-decoration: line-through; opacity: 0.6;' : ''}">${title}</div>`;
        content += `<div class="tooltip-sub" style="color: #ff55ff; font-size: 11px; text-transform: uppercase;">[${frame}] - <span style="color: ${isCompleted ? '#55ff55' : '#ff5555'}">${status}</span></div>`;
        if (desc) {
          content += `<div style="color: ${isCompleted ? '#aaccff' : '#666666'}; margin-top: 6px; font-style: italic;">"${desc}"</div>`;
        }
        if (isCompleted) {
          if (dimension) {
            content += `<div style="color: #aaaaaa; margin-top: 6px;">Dimension: <span style="color: #55ff55;">${dimension}</span></div>`;
          }
          if (timestamp) {
            content += `<div style="color: #aaaaaa;">Earned: <span style="color: #55ffff;">${timestamp}</span></div>`;
          }
        }
      }
    } else if (statCard) {
      const label = statCard.dataset.label;
      if (label) {
        const val = statCard.dataset.value;
        const desc = getStatDescription(statCard.dataset.statKey);
        content = `<div class="tooltip-title" style="color: #f3e46b; font-weight: bold;">${label}</div>`;
        content += `<div style="color: #ffffff; font-size: 14px; margin-top: 4px;">Value: ${val}</div>`;
        if (desc) {
          content += `<div style="color: #bdc2c8; margin-top: 6px; font-size: 12px;">${desc}</div>`;
        }
      }
    } else if (playerCard) {
      const name = playerCard.querySelector("h3")?.textContent;
      if (name) {
        const logs = playerCard.querySelector(".record-meta")?.textContent;
        const note = playerCard.querySelector(".record-note")?.textContent;
        content = `<div class="tooltip-title" style="color: #55ff55; font-weight: bold;">${name}</div>`;
        if (logs) content += `<div style="color: #ffffff; margin-top: 4px;">${logs}</div>`;
        if (note) content += `<div style="color: #bdc2c8; margin-top: 2px; font-size: 12px;">${note}</div>`;
      }
    } else if (recordCard) {
      const title = recordCard.querySelector("h3")?.textContent;
      if (title) {
        const count = recordCard.querySelector(".reason-pill")?.textContent || "";
        const meta = recordCard.querySelector(".record-meta")?.textContent || "";
        const note = recordCard.querySelector(".record-note")?.textContent || "";
        content = `<div class="tooltip-title" style="color: #ffff55; font-weight: bold;">${title} ${count ? `<span style="color: #ffffff; font-size: 11px;">(${count})</span>` : ""}</div>`;
        if (meta) content += `<div style="color: #aaaaaa; margin-top: 4px;">${meta}</div>`;
        if (note) content += `<div style="color: #bdc2c8; margin-top: 2px; font-size: 12px; font-style: italic;">"${note}"</div>`;
      }
    } else if (mcButton) {
      const title = mcButton.getAttribute("title");
      if (title) {
        content = `<div style="color: #ffffff;">${title}</div>`;
      }
    }

    if (content) {
      tooltip.innerHTML = content;
      tooltip.style.display = "block";
      positionTooltip();
    }
  });

  document.addEventListener("mouseout", (e) => {
    const itemRow = e.target.closest(".item-row");
    const advancementTile = e.target.closest(".advancement-tile");
    const statCard = e.target.closest(".stat-card");
    const mcButton = e.target.closest(".mc-button");
    const playerCard = e.target.closest(".player-card");
    const recordCard = e.target.closest(".record-card");

    const related = e.relatedTarget;
    if (related) {
      if (itemRow && itemRow.contains(related)) return;
      if (advancementTile && advancementTile.contains(related)) return;
      if (statCard && statCard.contains(related)) return;
      if (mcButton && mcButton.contains(related)) return;
      if (playerCard && playerCard.contains(related)) return;
      if (recordCard && recordCard.contains(related)) return;
    }

    tooltip.style.display = "none";
  });
}

boot().catch((error) => {
  recordDetail.innerHTML = `<div class="status error">${error.message}</div>`;
});

function updateGlossy(value) {
  const decimal = value / 100;
  // Set glossy opacity between 0.05 (almost fully clear) and 0.85 (deep dark glass blur)
  const opacity = 0.05 + (decimal * 0.80);
  const blur = decimal * 24; // Blur up to 24px
  document.documentElement.style.setProperty("--glossy-opacity", opacity);
  document.documentElement.style.setProperty("--glossy-blur", `${blur}px`);
}
