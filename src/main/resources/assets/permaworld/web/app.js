const state = {
  admins: [],
  selectedAdmin: "",
  players: [],
  selectedPlayer: null,
  filter: "ALL",
  records: [],
  selectedRecord: null,
  stats: null,
  tab: "JUGADORES",
  mapVisiblePlayers: null,
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
    if (myUserSelect) myUserSelect.value = savedUserUuid;
  } else {
    state.selectedPlayer = state.players[0] ?? null;
    if (myUserSelect) myUserSelect.value = state.selectedPlayer ? state.selectedPlayer.uuid : "";
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

  await switchTab("JUGADORES");
  initTooltip();
}

function bindEvents() {
  if (adminSelect) {
    adminSelect.addEventListener("change", () => {
      state.selectedAdmin = adminSelect.value;
    });
  }
  if (myUserSelect) {
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
  }
  
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

  const globalTabs = document.querySelector(".global-tabs");
  if (globalTabs) {
    globalTabs.addEventListener("click", async (event) => {
      const button = event.target.closest("[data-tab]");
      if (!button) return;
      await switchTab(button.dataset.tab);
    });
  }
}

function renderAdmins() {
  if (!adminSelect) return;
  adminSelect.innerHTML = "";
  for (const admin of state.admins) {
    const option = document.createElement("option");
    option.value = admin;
    option.textContent = admin;
    adminSelect.append(option);
  }
}

function renderMyUsers() {
  if (!myUserSelect) return;
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
        <div class="record-meta">${player.recordCount} logs (${player.logSize}) · ${player.lastReason}</div>
      </div>
    `;
    card.addEventListener("click", async () => {
      state.selectedPlayer = player;
      if (myUserSelect) myUserSelect.value = player.uuid;
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
  if (state.filter === "MAP") {
    await loadMapView();
    return;
  }
  if (state.filter === "ADMIN") {
    await loadAdminConsole();
    return;
  }
  await loadRecords();
}

async function switchTab(tabName) {
  state.tab = tabName;
  
  if (state.mapUpdateInterval) {
    clearInterval(state.mapUpdateInterval);
    state.mapUpdateInterval = null;
  }
  if (state.mapRefreshInterval) {
    clearInterval(state.mapRefreshInterval);
    state.mapRefreshInterval = null;
  }

  const layout = document.querySelector(".layout");
  const rail = document.querySelector(".rail");
  
  rail.style.display = "";
  document.querySelector(".rail .panel-title").textContent = "Players";
  playerSearch.style.display = "";
  
  document.querySelectorAll(".global-tabs .tab-btn").forEach((btn) => {
    btn.classList.toggle("is-active", btn.dataset.tab === tabName);
  });

  if (tabName === "JUGADORES") {
    layout.className = "layout view-jugadores";
    renderPlayers();
    
    // Asynchronously refresh player sizes and log counts in the background (flicker-free!)
    loadJson("/api/players").then(playersPayload => {
      state.players = playersPayload.players ?? [];
      renderPlayers();
    }).catch(e => console.warn("Failed to refresh players payload:", e));
    
    if (state.filter === "MAP" || state.filter === "ADMIN") {
      state.filter = "ALL";
    }
    
    document.querySelectorAll("#filterBar .mc-button").forEach((btn) => {
      btn.classList.toggle("is-active", btn.dataset.filter === state.filter);
    });

    if (state.selectedPlayer) {
      await loadCurrentView();
    } else {
      recordList.innerHTML = '<div class="empty">No players with records yet.</div>';
    }
  } else if (tabName === "MAPA") {
    layout.className = "layout view-mapa";
    state.filter = "MAP";
    await loadMapView();
  } else if (tabName === "ADMIN") {
    layout.className = "layout view-admin";
    rail.style.display = "none";
    state.filter = "ADMIN";
    await loadAdminConsole();
  }
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
  
  if (!state.allStructures) {
    try {
      state.allStructures = await loadJson("/api/structures");
    } catch (e) {
      console.warn("Failed to load structures list:", e);
      state.allStructures = [];
    }
  }

  state.stats = await loadJson(`/api/players/${state.selectedPlayer.uuid}/stats`);
  renderStats();
  renderStatsDetail();
}

async function loadAdminConsole() {
  state.records = [];
  state.selectedRecord = null;
  state.stats = null;
  
  recordList.classList.remove("stats-view");
  recordList.classList.add("admin-view");

  if (!state.selectedAdmin) {
    recordList.innerHTML = `
      <div class="status error" style="margin: 20px; text-align: center; border-radius: 8px; border: 2px solid #ff5555; background: rgba(255, 85, 85, 0.1);">
        <h3 style="color: #ff5555; margin-bottom: 10px; text-shadow: 1px 1px 0 #000;">🔓 Acceso Denegado / Consola Bloqueada</h3>
        <p style="font-size: 11px;">Por favor, selecciona un Administrador en el menú superior izquierdo para autorizar el acceso y firmar las solicitudes de consola.</p>
      </div>`;
    recordDetail.innerHTML = `
      <div class="empty">
        <h3>Admin no seleccionado</h3>
        <p>Usa la barra de selección superior para identificarte como administrador OP.</p>
      </div>`;
    return;
  }

  recordList.innerHTML = `
    <div class="admin-dashboard">
      <!-- Left Side: Terminal -->
      <div class="admin-console-panel" style="height: 480px;">
        <div class="terminal-header">
          <span class="terminal-title">⌨ Consola de Logs del Servidor</span>
          <button class="mc-button font-small" id="refreshLogsBtn" style="padding: 2px 8px;">↻ Recargar Logs</button>
        </div>
        <div class="terminal-body" id="logTerminal" style="height: calc(100% - 38px);">Cargando logs del servidor...</div>
      </div>
      
      <!-- Right Side: Performance -->
      <div class="admin-performance-panel">
        <div style="font-weight: bold; margin-bottom: 8px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Rendimiento</div>
        <div class="perf-grid">
          <div class="perf-card">
            <div class="perf-label">TPS</div>
            <div class="perf-value" id="perfTps">--</div>
          </div>
          <div class="perf-card">
            <div class="perf-label">RAM</div>
            <div class="perf-value" id="perfRam">--</div>
          </div>
          <div class="perf-card">
            <div class="perf-label">Online</div>
            <div class="perf-value" id="perfPlayersCount">--</div>
          </div>
        </div>
      </div>
    </div>`;

  recordDetail.innerHTML = `
    <div class="online-players-detail">
      <div style="font-weight: bold; margin-bottom: 8px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Jugadores Activos</div>
      <div id="onlinePlayersList" class="online-players-list">Cargando jugadores online...</div>
    </div>`;

  const refreshBtn = document.querySelector("#refreshLogsBtn");
  if (refreshBtn) {
    refreshBtn.addEventListener("click", () => reloadLogs());
  }

  await reloadLogs();
  await reloadStatus();
}

async function reloadLogs() {
  const terminal = document.querySelector("#logTerminal");
  if (!terminal) return;
  try {
    const logs = await loadJson(`/api/admin/logs?admin=${encodeURIComponent(state.selectedAdmin)}`);
    terminal.innerHTML = "";
    if (logs.length === 0) {
      terminal.innerHTML = '<div class="terminal-line system">No hay logs registrados en el archivo logs/latest.log.</div>';
    } else {
      for (const line of logs) {
        const lineEl = document.createElement("div");
        lineEl.className = "terminal-line";
        if (line.includes("[ERROR]") || line.includes("/ERROR")) {
          lineEl.classList.add("error");
        } else if (line.includes("[WARN]") || line.includes("/WARN")) {
          lineEl.classList.add("warn");
        } else if (line.includes("[INFO]") || line.includes("/INFO")) {
          lineEl.classList.add("info");
        }
        lineEl.textContent = line;
        terminal.appendChild(lineEl);
      }
      terminal.scrollTop = terminal.scrollHeight;
    }
  } catch (e) {
    console.error("Failed to load logs:", e);
    terminal.innerHTML = `<div class="terminal-line system error">Error al cargar logs: ${e.message}</div>`;
  }
}

async function reloadStatus() {
  const tpsEl = document.querySelector("#perfTps");
  const ramEl = document.querySelector("#perfRam");
  const countEl = document.querySelector("#perfPlayersCount");
  const playersListEl = document.querySelector("#onlinePlayersList");
  
  if (!tpsEl) return;

  try {
    const status = await loadJson(`/api/admin/status?admin=${encodeURIComponent(state.selectedAdmin)}`);
    if (!status.online) {
      tpsEl.textContent = "OFFLINE";
      ramEl.textContent = "OFFLINE";
      countEl.textContent = "OFFLINE";
      playersListEl.innerHTML = '<div class="empty-small">Servidor desconectado.</div>';
      return;
    }

    tpsEl.textContent = status.tps;
    if (status.tps >= 18) {
      tpsEl.style.color = "#55ff55";
    } else if (status.tps >= 15) {
      tpsEl.style.color = "#ffff55";
    } else {
      tpsEl.style.color = "#ff5555";
    }

    ramEl.textContent = `${status.usedMemoryMb}MB / ${status.maxMemoryMb}MB`;
    countEl.textContent = `${status.players.length} jugadores`;

    playersListEl.innerHTML = "";
    if (status.players.length === 0) {
      playersListEl.innerHTML = '<div class="empty-small">No hay jugadores conectados al servidor.</div>';
    } else {
      for (const p of status.players) {
        const item = document.createElement("div");
        item.className = "online-player-item";
        
        const healthPercent = Math.min(100, (p.health / p.maxHealth) * 100);
        const dimensionName = p.dimension.includes(":") ? p.dimension.split(":")[1] : p.dimension;

        item.innerHTML = `
          <div class="player-item-header">
            <img src="https://minotar.net/helm/${p.name}/24.png" class="player-head" alt="${p.name}">
            <div class="player-item-name-box">
              <span class="player-item-name">${p.name}</span>
              <span class="player-item-ping">${p.ping}ms ping</span>
            </div>
          </div>
          <div class="player-item-details">
            <div>📍 <strong>Pos:</strong> ${p.x}, ${p.y}, ${p.z} (${dimensionName})</div>
            <div class="health-bar-row">
              ❤️ <strong>Vida:</strong>
              <div class="ench-bar-container" style="flex: 1; margin-left: 8px; height: 8px;">
                <div class="ench-bar" style="width: ${healthPercent}%; background: #ff5555;"></div>
              </div>
              <span style="font-size: 10px; margin-left: 6px;">${p.health}/${p.maxHealth}</span>
            </div>
          </div>
        `;
        playersListEl.appendChild(item);
      }
    }
  } catch (e) {
    console.error("Failed to load status:", e);
  }
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

  const ext = state.stats.extendedStats || {
    totalDamageDealt: 0,
    totalDamageTaken: 0,
    damageDealtSinceDeath: 0,
    damageTakenSinceDeath: 0,
    blocksFallen: 0,
    fallDamageReceived: 0,
    totalXpGained: 0,
    totalLevelsGained: 0,
    enchantedItemsCount: 0,
    mobsDamage: [],
    enchantments: [],
    discoveredStructures: []
  };

  const mobsDamageHtml = ext.mobsDamage && ext.mobsDamage.length
    ? ext.mobsDamage.map((entry) => `
        <div class="leaderboard-row">
          <div><span style="color: #ff5555; font-weight: bold;">☠ ${escapeHtml(entry.source)}</span></div>
          <div>${entry.damage.toFixed(1)} ❤</div>
        </div>`).join("")
    : '<div class="leaderboard-row"><div>Ningún daño registrado aún.</div><div>0 ❤</div></div>';

  const enchantmentsHtml = ext.enchantments && ext.enchantments.length
    ? ext.enchantments.map((entry) => {
        const percent = Math.min(100, (entry.count / Math.max(1, ext.enchantedItemsCount)) * 100);
        return `
        <div class="enchantment-row">
          <div class="ench-name">${formatEnchantmentName(entry.enchantment)}</div>
          <div class="ench-bar-container">
            <div class="ench-bar" style="width: ${percent}%;"></div>
          </div>
          <div class="ench-count">${entry.count} veces</div>
        </div>`;
      }).join("")
    : '<div class="empty-small">Ningún encantamiento realizado aún.</div>';

  const allStructs = state.allStructures || [];
  // discoveredStructures es ahora array de {structureId, coords}
  const discoveredMap = new Map(); // structureId -> coords
  for (const s of (ext.discoveredStructures || [])) {
    if (typeof s === "object" && s.structureId) {
      discoveredMap.set(s.structureId, s.coords || "");
    } else if (typeof s === "string") {
      discoveredMap.set(s, ""); // retrocompatibilidad
    }
  }
  const structuresHtml = allStructs.length
    ? allStructs.map((structId) => {
        const isDiscovered = discoveredMap.has(structId);
        const coords = isDiscovered ? discoveredMap.get(structId) : "";
        return `
        <div class="structure-tile ${isDiscovered ? 'found' : 'missing'}" data-struct-id="${structId}">
          <div class="struct-status">${isDiscovered ? '✔' : '🔒'}</div>
          <div class="struct-name">${formatStructureName(structId)}</div>
          ${coords ? `<div class="struct-coords">${coords}</div>` : ""}
        </div>`;
      }).join("")
    : '<div class="empty-small">No hay estructuras registradas en el servidor.</div>';

  recordList.innerHTML = `
    <div style="font-weight: bold; margin-bottom: 4px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Estadísticas Nativas</div>
    <div class="stat-grid">${highlights}</div>

    <div style="font-weight: bold; margin: 18px 0 6px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Registro de Combate y Caídas</div>
    <div class="stat-grid">
      <div class="stat-card ext-damage-dealt">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:netherite_sword", "Daño Causado")}</div>
          <div>
            <div class="record-meta">Daño Total Causado</div>
            <div class="stat-value" style="color: #ff5555;">${ext.totalDamageDealt.toFixed(1)} ❤</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-damage-taken">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:netherite_chestplate", "Daño Sufrido")}</div>
          <div>
            <div class="record-meta">Daño Total Sufrido</div>
            <div class="stat-value" style="color: #ff5555;">${ext.totalDamageTaken.toFixed(1)} ❤</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-damage-dealt-life">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:diamond_sword", "Daño en esta Vida")}</div>
          <div>
            <div class="record-meta">Daño Causado (Esta Vida)</div>
            <div class="stat-value" style="color: #ff8888;">${ext.damageDealtSinceDeath.toFixed(1)} ❤</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-damage-taken-life">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:diamond_chestplate", "Daño Sufrido en esta Vida")}</div>
          <div>
            <div class="record-meta">Daño Sufrido (Esta Vida)</div>
            <div class="stat-value" style="color: #ff8888;">${ext.damageTakenSinceDeath.toFixed(1)} ❤</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-fall-blocks">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:leather_boots", "Bloques Caídos")}</div>
          <div>
            <div class="record-meta">Bloques Totales Caídos</div>
            <div class="stat-value" style="color: #aa88ff;">${ext.blocksFallen.toFixed(1)} bloques</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-fall-damage">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:feather", "Daño de Caída")}</div>
          <div>
            <div class="record-meta">Daño de Caída Sufrido</div>
            <div class="stat-value" style="color: #aa88ff;">${ext.fallDamageReceived.toFixed(1)} ❤</div>
          </div>
        </div>
      </div>
    </div>

    <div style="font-weight: bold; margin: 18px 0 6px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Experiencia y Encantamientos</div>
    <div class="stat-grid">
      <div class="stat-card ext-xp">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:experience_bottle", "XP total")}</div>
          <div>
            <div class="record-meta">Experiencia Total Conseguida</div>
            <div class="stat-value" style="color: #55ff55;">${ext.totalXpGained} XP</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-xp-levels">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:emerald", "Niveles ganados")}</div>
          <div>
            <div class="record-meta">Niveles Totales Conseguidos</div>
            <div class="stat-value" style="color: #55ff55;">${ext.totalLevelsGained} niveles</div>
          </div>
        </div>
      </div>
      <div class="stat-card ext-enchant-count" style="grid-column: span 2;">
        <div class="stat-card-main">
          <div class="slot-icon">${renderItemIcon("minecraft:enchanting_table", "Objetos Encantados")}</div>
          <div>
            <div class="record-meta">Cosas Encantadas</div>
            <div class="stat-value" style="color: #55ffff;">${ext.enchantedItemsCount} objetos</div>
          </div>
        </div>
      </div>
    </div>

    <div class="leaderboards" style="margin-top: 14px;">
      <section class="leaderboard">
        <h3>Desglose de Encantamientos Obtenidos</h3>
        <div style="padding: 12px 16px;">
          ${enchantmentsHtml}
        </div>
      </section>
    </div>

    <div style="font-weight: bold; margin: 18px 0 6px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Daño Sufrido por Criatura & Tablas del Mundo</div>
    <div class="leaderboards">
      <section class="leaderboard">
        <h3>Criaturas y Fuentes más Letales (Daño infligido al jugador)</h3>
        <div class="leaderboard-list">
          ${mobsDamageHtml}
        </div>
      </section>
    </div>

    <div class="leaderboards" style="margin-top: 14px;">
      ${renderLeaderboard("Bloques Minados", state.stats.blocksMined)}
      ${renderLeaderboard("Objetos Fabricados", state.stats.itemsCrafted)}
      ${renderLeaderboard("Objetos Recogidos", state.stats.itemsPickedUp)}
      ${renderLeaderboard("Criaturas Derrotadas", state.stats.entitiesKilled)}
    </div>

    <div style="font-weight: bold; margin: 24px 0 6px; font-size: 14px; text-transform: uppercase; color: var(--gold-1); text-shadow: 1px 1px 0 #000; text-align: left;">Matriz de Descubrimiento de Estructuras</div>
    <div class="structure-matrix-panel">
      <div style="font-size: 12px; color: var(--muted); margin-bottom: 12px; text-align: left;">
        Muestra la lista de todas las estructuras oficiales del servidor y el estado de descubrimiento del jugador:
      </div>
      <div class="structure-grid">
        ${structuresHtml}
      </div>
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

const ITEM_EMOJI = {
  // Herramientas y armas
  "minecraft:netherite_sword":    "⚔️",
  "minecraft:diamond_sword":      "🗡️",
  "minecraft:iron_sword":         "⚔️",
  "minecraft:netherite_axe":      "🪓",
  "minecraft:diamond_axe":        "🪓",
  "minecraft:bow":                "🏹",
  "minecraft:crossbow":           "🏹",
  "minecraft:trident":            "🔱",
  // Armaduras
  "minecraft:netherite_chestplate": "🛡️",
  "minecraft:diamond_chestplate":   "🛡️",
  "minecraft:iron_chestplate":      "🛡️",
  "minecraft:netherite_helmet":     "⛑️",
  "minecraft:diamond_helmet":       "⛑️",
  "minecraft:leather_boots":        "👟",
  "minecraft:netherite_boots":      "👟",
  // Comida
  "minecraft:cooked_beef":        "🥩",
  "minecraft:bread":              "🍞",
  "minecraft:apple":              "🍎",
  "minecraft:golden_apple":       "🍎",
  "minecraft:enchanted_golden_apple": "🍎",
  // XP y magia
  "minecraft:experience_bottle":  "🍶",
  "minecraft:emerald":            "💚",
  "minecraft:enchanting_table":   "🔮",
  "minecraft:blaze_powder":       "✨",
  "minecraft:nether_star":        "⭐",
  "minecraft:end_crystal":        "💎",
  // Bloques y recursos
  "minecraft:diamond":            "💎",
  "minecraft:gold_ingot":         "🪙",
  "minecraft:iron_ingot":         "🔩",
  "minecraft:feather":            "🪶",
  "minecraft:bone":               "🦴",
  "minecraft:blaze_rod":          "🔥",
  "minecraft:ender_pearl":        "🌀",
  // Mobs / criaturas
  "minecraft:creeper_head":       "💣",
  "minecraft:skeleton_skull":     "💀",
  "minecraft:zombie_head":        "🧟",
  // Otros
  "minecraft:compass":            "🧭",
  "minecraft:map":                "🗺️",
  "minecraft:clock":              "🕐",
  "minecraft:paper":              "📄",
  "minecraft:book":               "📖",
  "minecraft:written_book":       "📖",
  "minecraft:filled_map":         "🗺️",
  "minecraft:chest":              "📦",
  "minecraft:ender_chest":        "📦",
  "minecraft:shulker_box":        "📦",
  "minecraft:barrier":            "🚫",
};

function getItemEmoji(itemId) {
  if (!itemId) return "❓";
  const direct = ITEM_EMOJI[itemId];
  if (direct) return direct;
  // fallbacks genéricos por categoría
  if (itemId.includes("sword") || itemId.includes("axe")) return "⚔️";
  if (itemId.includes("helmet") || itemId.includes("chestplate") || itemId.includes("leggings") || itemId.includes("boots")) return "🛡️";
  if (itemId.includes("bow")) return "🏹";
  if (itemId.includes("pickaxe") || itemId.includes("shovel") || itemId.includes("hoe")) return "⛏️";
  if (itemId.includes("food") || itemId.includes("bread") || itemId.includes("beef") || itemId.includes("pork") || itemId.includes("fish")) return "🍖";
  if (itemId.includes("apple")) return "🍎";
  if (itemId.includes("potion")) return "🧪";
  if (itemId.includes("arrow")) return "🏹";
  if (itemId.includes("ingot") || itemId.includes("nugget")) return "🪙";
  if (itemId.includes("block")) return "🧱";
  if (itemId.includes("log") || itemId.includes("wood") || itemId.includes("plank")) return "🪵";
  if (itemId.includes("stone") || itemId.includes("cobblestone")) return "🪨";
  if (itemId.includes("diamond")) return "💎";
  if (itemId.includes("emerald")) return "💚";
  if (itemId.includes("gold")) return "🪙";
  if (itemId.includes("iron")) return "🔩";
  if (itemId.includes("netherite")) return "🔱";
  if (itemId.includes("enchant")) return "🔮";
  if (itemId.includes("experience") || itemId.includes("xp")) return "🍶";
  if (itemId.includes("skull") || itemId.includes("head")) return "💀";
  if (itemId.includes("bone")) return "🦴";
  if (itemId.includes("book")) return "📖";
  if (itemId.includes("chest") || itemId.includes("shulker")) return "📦";
  return "📦";
}

function renderItemIcon(itemId, label) {
  const emoji = getItemEmoji(itemId);
  if (!itemId) {
    return `<div class="slot-fallback">${emoji}</div>`;
  }
  // Intenta cargar la textura del servidor (solo funciona en cliente).
  // Si falla (servidor dedicado no tiene texturas), usa el emoji mapeado.
  return `<img src="/api/item-texture?itemId=${encodeURIComponent(itemId)}" alt="${escapeHtml(label || itemId)}" onerror="this.outerHTML='<div class=\\'slot-fallback emoji\\'>${emoji}</div>'">`;
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

function formatEnchantmentName(id) {
  if (!id) return "";
  const path = id.includes(":") ? id.split(":")[1] : id;
  return path
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function formatStructureName(id) {
  if (!id) return "Structure";
  const path = id.includes(":") ? id.split(":")[1] : id;
  return path
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/* ==========================================
   INTERACTIVE CANVAS MAP GRAPHICS ENGINE
   ========================================== */

let mapCanvas = null;
let mapCtx = null;
let mapZoom = 1.0;
let mapPanX = 0;
let mapPanY = 0;
let mapIsDragging = false;
let mapLastMouseX = 0;
let mapLastMouseY = 0;
let mapActiveDimension = "minecraft:overworld";
let mapHovered = null;
let mapSelected = null;
let mapData = { paths: {}, structures: [], players: {} };
const skinImages = {}; // Face images cache
const regionImages = {}; // Terrain tiles cache

const PALETTE = [
  "#3498db", // Blue
  "#2ecc71", // Green
  "#e74c3c", // Red
  "#f1c40f", // Gold
  "#9b59b6", // Purple
  "#e67e22", // Orange
  "#1abc9c", // Aqua
  "#e84393", // Pink
  "#fdcb6e", // Warm Gold
  "#6c5ce7", // Soft Indigo
  "#00cec9", // Teal
  "#ff7675", // Coral
];

function getPlayerColor(uuid) {
  if (!uuid) return "#ffffff";
  let hash = 0;
  for (let i = 0; i < uuid.length; i++) {
    hash = uuid.charCodeAt(i) + ((hash << 5) - hash);
  }
  const index = Math.abs(hash) % PALETTE.length;
  return PALETTE[index];
}

function getPlayerFace(playerName, callback) {
  if (skinImages[playerName]) {
    if (skinImages[playerName].loaded) {
      callback(skinImages[playerName]);
    } else {
      skinImages[playerName].listeners.push(callback);
    }
    return;
  }
  const img = new Image();
  img.src = `https://minotar.net/helm/${playerName}/16.png`;
  img.loaded = false;
  img.listeners = [callback];
  img.onload = () => {
    img.loaded = true;
    img.listeners.forEach((cb) => cb(img));
    img.listeners = [];
    requestRender();
  };
  img.onerror = () => {
    img.loaded = true;
    img.listeners = [];
  };
  skinImages[playerName] = img;
}

async function loadMapView() {
  recordList.classList.remove("stats-view");
  recordList.classList.remove("advancement-view");
  recordList.classList.remove("admin-view");
  
  if (state.mapUpdateInterval) {
    clearInterval(state.mapUpdateInterval);
    state.mapUpdateInterval = null;
  }
  if (state.mapRefreshInterval) {
    clearInterval(state.mapRefreshInterval);
    state.mapRefreshInterval = null;
  }
  
  // Render layout skeletons
  recordList.innerHTML = `
    <div class="map-view-container">
      <div class="map-viewport">
        <canvas id="mapCanvas"></canvas>
        <div class="map-hud">
          <div id="mapCoords" class="map-coords">X: 0, Z: 0</div>
          <div class="map-hud-controls">
            <button id="mapZoomIn" class="mc-button map-control-btn">+</button>
            <button id="mapZoomOut" class="mc-button map-control-btn">-</button>
            <button id="mapReset" class="mc-button map-control-btn">⊙</button>
          </div>
        </div>
      </div>
    </div>
  `;

  // Render Map Controls inside the left rail!
  document.querySelector(".rail .panel-title").textContent = "Mapa / Capas";
  playerSearch.style.display = "none";
  
  playerList.innerHTML = `
    <div class="map-sidebar-options" style="padding: 12px; display: flex; flex-direction: column; gap: 16px;">
      
      <!-- Dimensión Section -->
      <div class="map-sidebar-section">
        <div class="panel-section-title" style="margin-bottom: 8px;">Dimensión</div>
        <div class="dim-selector-grid" id="mapDimSelector" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(70px, 1fr)); gap: 6px;">
          <!-- Populating dynamically -->
        </div>
      </div>
      
      <!-- Capas Visibles Section -->
      <div class="map-sidebar-section">
        <div class="panel-section-title" style="margin-bottom: 8px;">Capas Visibles</div>
        <div class="layers-check-list" style="display: flex; flex-direction: column; gap: 8px;">
          <label class="mc-checkbox-label" style="display: flex; align-items: center; gap: 8px;">
            <input type="checkbox" id="chkTerrain" checked>
            <span>Foto Aérea 📷</span>
          </label>
          <label class="mc-checkbox-label" style="display: flex; align-items: center; gap: 8px;">
            <input type="checkbox" id="chkPaths" checked>
            <span>Trazados 🗺</span>
          </label>
          <label class="mc-checkbox-label" style="display: flex; align-items: center; gap: 8px;">
            <input type="checkbox" id="chkStructures" checked>
            <span>Estructuras ◈</span>
          </label>
          <label class="mc-checkbox-label" style="display: flex; align-items: center; gap: 8px;">
            <input type="checkbox" id="chkDeaths" checked>
            <span>Muertes 💀</span>
          </label>
          <label class="mc-checkbox-label" style="display: flex; align-items: center; gap: 8px;">
            <input type="checkbox" id="chkOnline" checked>
            <span>Jugadores Live 🟢</span>
          </label>
        </div>
      </div>
      
      <!-- Trayectos de Jugadores Section -->
      <div class="map-sidebar-section">
        <div class="panel-section-title" style="margin-bottom: 8px;">Ver Caminos de:</div>
        <div class="map-player-toggles-header" style="display: flex; gap: 8px; margin-bottom: 8px;">
          <button class="mc-button font-small" id="mapPlayersAll" style="flex: 1; padding: 4px 0; font-size: 12px;">Todos</button>
          <button class="mc-button font-small" id="mapPlayersNone" style="flex: 1; padding: 4px 0; font-size: 12px;">Ninguno</button>
        </div>
        <div id="mapPlayerChecklist" class="map-player-checklist" style="display: flex; flex-direction: column; gap: 6px; max-height: 250px; overflow-y: auto; padding-right: 4px;">
          <!-- Dynamically populated checklist -->
        </div>
      </div>
      
    </div>
  `;

  // Render Selection panel in the right sidebar (recordDetail)
  recordDetail.innerHTML = `<div class="map-sidebar"><div class="empty-selection">Cargando datos de mapa...</div></div>`;

  // Initialize visible players set if null
  if (!state.mapVisiblePlayers) {
    state.mapVisiblePlayers = new Set(state.players.map(p => p.uuid));
  }

  // Populate checklist
  renderMapPlayerChecklist();

  // Fetch map consolidated dataset
  try {
    const rawData = await loadJson("/api/map/data");
    mapData = rawData;
    renderMapSidebar();
    await renderDynamicDimensions();
  } catch (err) {
    console.error("Error loading map data:", err);
    recordList.innerHTML = `<div class="status error" style="margin:20px;">Error al cargar datos del mapa: ${err.message}</div>`;
    return;
  }

  mapCanvas = document.querySelector("#mapCanvas");
  if (!mapCanvas) return;
  mapCtx = mapCanvas.getContext("2d");
  
  // Fit to screen (deferred slightly to ensure browser DOM layout has fully completed)
  setTimeout(() => {
    resizeCanvas();
    centerOnSelectedPlayer();
    requestRender();
  }, 100);
  window.addEventListener("resize", resizeCanvas);

  mapZoom = 1.0;
  mapPanX = 0;
  mapPanY = 0;
  mapActiveDimension = "minecraft:overworld";
  mapHovered = null;
  mapSelected = null;

  centerOnSelectedPlayer();
  requestRender();
  setupMapEvents();

  // Setup Live Online tracker (3s loop)
  state.mapUpdateInterval = setInterval(async () => {
    try {
      if (state.filter !== "MAP") {
        clearInterval(state.mapUpdateInterval);
        state.mapUpdateInterval = null;
        return;
      }
      
      const status = await loadJson("/api/admin/status");
      if (status.online && status.players) {
        for (const p of status.players) {
          if (!mapData.players[p.uuid]) {
            mapData.players[p.uuid] = {};
          }
          mapData.players[p.uuid].name = p.name;
          mapData.players[p.uuid].online = true;
          mapData.players[p.uuid].dimension = p.dimension;
          mapData.players[p.uuid].x = p.x;
          mapData.players[p.uuid].y = p.y;
          mapData.players[p.uuid].z = p.z;
        }
        
        const activeUuids = status.players.map((p) => p.uuid);
        for (const uuid in mapData.players) {
          if (!activeUuids.includes(uuid)) {
            mapData.players[uuid].online = false;
          }
        }
        requestRender();
      }
    } catch (e) {
      console.warn("Map live tracking failed:", e);
    }
  }, 3000);

  // Setup Map Data full refresh loop (every 10 seconds)
  state.mapRefreshInterval = setInterval(async () => {
    try {
      if (state.filter !== "MAP") {
        clearInterval(state.mapRefreshInterval);
        state.mapRefreshInterval = null;
        return;
      }
      const rawData = await loadJson("/api/map/data");
      if (rawData) {
        mapData.paths = rawData.paths || {};
        mapData.structures = rawData.structures || [];
        if (rawData.players) {
          for (const uuid in rawData.players) {
            if (!mapData.players[uuid]) {
              mapData.players[uuid] = {};
            }
            Object.assign(mapData.players[uuid], rawData.players[uuid]);
          }
        }
        
        renderMapSidebar();
        
        // Refresh all currently cached visible region images in the background (flicker-free!)
        for (const key in regionImages) {
          const parts = key.split(",");
          if (parts.length === 3) {
            const rx = parts[0];
            const rz = parts[1];
            const dim = parts[2];
            
            const img = new Image();
            img.src = `/api/map/region?rx=${rx}&rz=${rz}&dim=${encodeURIComponent(dim)}&_cb=${Date.now()}`;
            img.onload = () => {
              if (regionImages[key]) {
                regionImages[key].image = img;
                regionImages[key].loaded = true;
                regionImages[key].failed = false;
                requestRender();
              }
            };
          }
        }
        
        requestRender();
      }
    } catch (e) {
      console.warn("Map periodic refresh failed:", e);
    }
  }, 10000);
}

function renderMapPlayerChecklist() {
  const checklistContainer = document.querySelector("#mapPlayerChecklist");
  if (!checklistContainer) return;

  checklistContainer.innerHTML = state.players.map(p => {
    const isChecked = state.mapVisiblePlayers.has(p.uuid);
    const color = getPlayerColor(p.uuid);
    return `
      <label class="mc-checkbox-label" style="display: flex; align-items: center; gap: 8px;">
        <input type="checkbox" class="chk-map-player" data-uuid="${p.uuid}" ${isChecked ? 'checked' : ''}>
        <span class="player-dot" style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background-color: ${color};"></span>
        <span style="font-size: 13px;">${p.playerName}</span>
      </label>
    `;
  }).join("");
  
  checklistContainer.querySelectorAll(".chk-map-player").forEach(chk => {
    chk.addEventListener("change", () => {
      const uuid = chk.dataset.uuid;
      if (chk.checked) {
        state.mapVisiblePlayers.add(uuid);
      } else {
        state.mapVisiblePlayers.delete(uuid);
      }
      requestRender();
    });
  });

  document.querySelector("#mapPlayersAll")?.addEventListener("click", () => {
    state.players.forEach(p => state.mapVisiblePlayers.add(p.uuid));
    document.querySelectorAll(".chk-map-player").forEach(chk => chk.checked = true);
    requestRender();
  });
  
  document.querySelector("#mapPlayersNone")?.addEventListener("click", () => {
    state.mapVisiblePlayers.clear();
    document.querySelectorAll(".chk-map-player").forEach(chk => chk.checked = false);
    requestRender();
  });
}

function setupMapEvents() {
  if (!mapCanvas) return;
  
  mapCanvas.addEventListener("mousedown", (e) => {
    mapIsDragging = true;
    mapLastMouseX = e.clientX;
    mapLastMouseY = e.clientY;
  });

  document.addEventListener("mousemove", handleMapMouseMove);
  document.addEventListener("mouseup", handleMapMouseUp);

  mapCanvas.addEventListener("wheel", (e) => {
    e.preventDefault();
    const rect = mapCanvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    const factor = e.deltaY < 0 ? 1.2 : (1.0 / 1.2);
    changeZoom(factor, mouseX, mouseY);
  });

  document.querySelector("#mapZoomIn").addEventListener("click", () => {
    changeZoom(1.3, mapCanvas.width / 2, mapCanvas.height / 2);
  });
  
  document.querySelector("#mapZoomOut").addEventListener("click", () => {
    changeZoom(1.0 / 1.3, mapCanvas.width / 2, mapCanvas.height / 2);
  });
  
  document.querySelector("#mapReset").addEventListener("click", () => {
    mapZoom = 1.0;
    mapPanX = 0;
    mapPanY = 0;
    centerOnSelectedPlayer();
    requestRender();
  });



  ["chkTerrain", "chkPaths", "chkStructures", "chkDeaths", "chkOnline"].forEach((id) => {
    const chk = document.querySelector("#" + id);
    if (chk) {
      chk.addEventListener("change", () => {
        requestRender();
      });
    }
  });
}

function handleMapMouseMove(e) {
  if (!mapCanvas || state.filter !== "MAP") return;
  
  const rect = mapCanvas.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;
  
  if (mapIsDragging) {
    const dx = e.clientX - mapLastMouseX;
    const dy = e.clientY - mapLastMouseY;
    mapPanX += dx;
    mapPanY += dy;
    mapLastMouseX = e.clientX;
    mapLastMouseY = e.clientY;
    
    const mcCoords = canvasToMc(mouseX, mouseY);
    updateCoordsHUD(mcCoords.x, mcCoords.z);
    
    requestRender();
  } else {
    const mcCoords = canvasToMc(mouseX, mouseY);
    updateCoordsHUD(mcCoords.x, mcCoords.z);
    
    const item = checkHoverProximity(mouseX, mouseY);
    const tooltip = document.querySelector("#mcTooltip");
    
    if (item) {
      mapHovered = item;
      mapCanvas.style.cursor = "pointer";
      
      if (tooltip) {
        tooltip.innerHTML = getTooltipHTML(item);
        tooltip.style.display = "block";
      }
    } else {
      mapHovered = null;
      mapCanvas.style.cursor = "grab";
      if (tooltip) {
        tooltip.style.display = "none";
      }
    }
  }
}

function handleMapMouseUp(e) {
  if (state.filter !== "MAP") return;
  if (mapIsDragging) {
    mapIsDragging = false;
    requestRender();
  } else {
    if (mapHovered) {
      mapSelected = mapHovered;
      renderMapSidebar();
    } else {
      if (mapCanvas) {
        const rect = mapCanvas.getBoundingClientRect();
        const clickX = e.clientX - rect.left;
        const clickY = e.clientY - rect.top;
        if (clickX >= 0 && clickX <= mapCanvas.width && clickY >= 0 && clickY <= mapCanvas.height) {
          mapSelected = null;
          renderMapSidebar();
        }
      }
    }
    requestRender();
  }
}

function checkHoverProximity(mouseX, mouseY) {
  if (!mapData) return null;
  
  const showOnline = document.querySelector("#chkOnline")?.checked;
  const showStructures = document.querySelector("#chkStructures")?.checked;
  const showDeaths = document.querySelector("#chkDeaths")?.checked;
  const showPaths = document.querySelector("#chkPaths")?.checked;

  // 1. Online live players
  if (showOnline && mapData.players) {
    for (const uuid in mapData.players) {
      if (state.mapVisiblePlayers && !state.mapVisiblePlayers.has(uuid)) continue;
      const p = mapData.players[uuid];
      if (p.online && p.dimension === mapActiveDimension && p.x !== undefined) {
        const cPos = mcToCanvas(p.x, p.z);
        const dist = Math.hypot(mouseX - cPos.x, mouseY - cPos.y);
        if (dist <= 16) {
          return { type: "online_player", uuid, data: p };
        }
      }
    }
  }

  // 2. Structures
  if (showStructures && mapData.structures) {
    for (const struct of mapData.structures) {
      if (struct.dimension === mapActiveDimension && struct.x !== undefined) {
        const cPos = mcToCanvas(struct.x, struct.z);
        const dist = Math.hypot(mouseX - cPos.x, mouseY - cPos.y);
        if (dist <= 12) {
          return { type: "structure", data: struct };
        }
      }
    }
  }

  // 3. Deaths
  if (showDeaths && mapData.paths) {
    for (const uuid in mapData.paths) {
      if (state.mapVisiblePlayers && !state.mapVisiblePlayers.has(uuid)) continue;
      const pts = mapData.paths[uuid];
      for (const pt of pts) {
        if (pt.type === "DEATH" && pt.dimension === mapActiveDimension) {
          const cPos = mcToCanvas(pt.x, pt.z);
          const dist = Math.hypot(mouseX - cPos.x, mouseY - cPos.y);
          if (dist <= 12) {
            const player = mapData.players[uuid] || { name: "Jugador" };
            return { type: "death", uuid, playerName: player.name, data: pt };
          }
        }
      }
    }
  }

  // 4. Path Points exploration
  if (showPaths && mapData.paths) {
    for (const uuid in mapData.paths) {
      if (state.mapVisiblePlayers && !state.mapVisiblePlayers.has(uuid)) continue;
      const pts = mapData.paths[uuid];
      for (const pt of pts) {
        if (pt.dimension === mapActiveDimension) {
          const cPos = mcToCanvas(pt.x, pt.z);
          const dist = Math.hypot(mouseX - cPos.x, mouseY - cPos.y);
          if (dist <= 8) {
            const player = mapData.players[uuid] || { name: "Jugador" };
            return { type: "path_point", uuid, playerName: player.name, data: pt };
          }
        }
      }
    }
  }
  
  return null;
}

function getTooltipHTML(item) {
  if (item.type === "online_player") {
    return `
      <div style="padding: 4px; font-family: var(--font-mono);">
        <h4 style="color: var(--gold-1); margin: 0 0 4px; font-size:13px;">👤 ${item.data.name}</h4>
        <p style="margin: 0; font-size:11px; color:#55ff55;">● JUGADOR LIVE</p>
        <p style="margin: 4px 0 0; font-size:11px; color:var(--stone-4);">Pos: ${Math.round(item.data.x)}, ${Math.round(item.data.z)}</p>
      </div>
    `;
  }
  if (item.type === "structure") {
    return `
      <div style="padding: 4px; font-family: var(--font-mono);">
        <h4 style="color: var(--gold-1); margin: 0 0 4px; font-size:13px;">◈ ${item.data.name}</h4>
        <p style="margin: 0; font-size:11px; color:#bdc2c8;">Estructura Descubierta</p>
        <p style="margin: 4px 0 0; font-size:11px; color:var(--stone-4);">Por: <span style="color:#fff;">${item.data.discoveredBy}</span></p>
        <p style="margin: 2px 0 0; font-size:11px; color:var(--stone-4);">Pos: ${Math.round(item.data.x)}, ${Math.round(item.data.z)}</p>
      </div>
    `;
  }
  if (item.type === "death") {
    return `
      <div style="padding: 4px; font-family: var(--font-mono);">
        <h4 style="color: #ff5555; margin: 0 0 4px; font-size:13px;">💀 Muerte de ${item.playerName}</h4>
        <p style="margin: 0; font-size:11px; color:var(--stone-4);">${new Date(item.data.timestamp).toLocaleString()}</p>
        <p style="margin: 4px 0 0; font-size:11px; color:var(--stone-4);">Pos: ${Math.round(item.data.x)}, ${Math.round(item.data.z)}</p>
      </div>
    `;
  }
  if (item.type === "path_point") {
    return `
      <div style="padding: 4px; font-family: var(--font-mono);">
        <h4 style="color: var(--gold-1); margin: 0 0 4px; font-size:13px;">🗺 Trazo de ${item.playerName}</h4>
        <p style="margin: 0; font-size:11px; color:var(--stone-4);">${new Date(item.data.timestamp).toLocaleString()}</p>
        <p style="margin: 4px 0 0; font-size:11px; color:var(--stone-3);">Mapeado en exploración</p>
      </div>
    `;
  }
  return "";
}

function renderMapSidebar() {
  const sidebarContainer = document.querySelector("#recordDetail");
  if (!sidebarContainer || state.filter !== "MAP") return;

  const activeDimName = mapActiveDimension.includes(":") ? mapActiveDimension.split(":")[1] : mapActiveDimension;
  const filteredStructs = (mapData.structures || []).filter(s => s.dimension === mapActiveDimension);

  let structuresListHtml = "";
  if (filteredStructs.length === 0) {
    structuresListHtml = `<div class="empty-small" style="font-size:12px; color:var(--stone-4); padding: 8px 12px; text-align:center; background:rgba(0,0,0,0.15); border:2px dashed rgba(255,255,255,0.05);">Ninguna estructura descubierta.</div>`;
  } else {
    structuresListHtml = `
      <div class="sidebar-struct-list" style="display:flex; flex-direction:column; gap:6px; max-height: 180px; overflow-y:auto; padding-right:4px;">
        ${filteredStructs.map(struct => {
          const isSelected = mapSelected && mapSelected.type === "structure" && mapSelected.data.coords === struct.coords;
          return `
            <button class="mc-button struct-list-item ${isSelected ? 'is-active' : ''}" 
                    data-coords="${struct.coords}"
                    style="display:flex; align-items:center; justify-content:space-between; text-align:left; padding:6px 10px; font-size:12px; width:100%;">
              <span>◈ ${struct.name}</span>
              <span style="font-size:10px; opacity:0.8; font-family:var(--font-mono);">${Math.round(struct.x)}, ${Math.round(struct.z)}</span>
            </button>
          `;
        }).join("")}
      </div>
    `;
  }

  sidebarContainer.innerHTML = `
    <div class="map-sidebar" style="display:flex; flex-direction:column; gap:16px;">
      
      <!-- Section 1: Estructuras -->
      <div class="map-sidebar-section">
        <div class="panel-section-title" style="color: var(--gold-1); text-shadow: 1px 1px 0 #000; margin-bottom:8px; font-size:14px; font-weight:bold; text-transform:uppercase;">
          Estructuras (${activeDimName})
        </div>
        ${structuresListHtml}
      </div>
      
      <!-- Section 2: Selección -->
      <div class="map-sidebar-section last-section" style="border-top: 2px solid rgba(255, 255, 255, 0.05); padding-top:12px;">
        <div class="panel-section-title" id="mapSelectionTitle" style="color: var(--gold-1); text-shadow: 1px 1px 0 #000; margin-bottom:8px; font-size:14px; font-weight:bold; text-transform:uppercase;">
          Selección
        </div>
        <div id="mapSelectionContent" class="map-selection-content">
          <!-- Selection details populated dynamically -->
        </div>
      </div>
      
    </div>
  `;

  // Bind clicks to structure list buttons
  sidebarContainer.querySelectorAll(".struct-list-item").forEach(btn => {
    btn.addEventListener("click", () => {
      const coords = btn.dataset.coords;
      const struct = filteredStructs.find(s => s.coords === coords);
      if (struct) {
        centerOn(struct.x, struct.z);
        mapSelected = { type: "structure", data: struct };
        sidebarContainer.querySelectorAll(".struct-list-item").forEach(b => b.classList.remove("is-active"));
        btn.classList.add("is-active");
        renderSelectionDetail(mapSelected);
        requestRender();
      }
    });
  });

  // Restore current selection view if any
  if (mapSelected) {
    renderSelectionDetail(mapSelected);
  } else {
    const content = sidebarContainer.querySelector("#mapSelectionContent");
    if (content) {
      content.innerHTML = `<div class="empty-selection">Haz clic en un trazo, estructura, calavera o avatar en el mapa para ver sus detalles.</div>`;
    }
  }
}

async function renderDynamicDimensions() {
  let dims = ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"];
  try {
    dims = await loadJson("/api/dimensions");
  } catch (e) {
    console.warn("Failed to load dimensions list:", e);
  }

  const dimSelector = document.querySelector("#mapDimSelector");
  if (!dimSelector) return;

  dimSelector.innerHTML = dims.map(dim => {
    const isActive = dim === mapActiveDimension;
    let label = dim.includes(":") ? dim.split(":")[1] : dim;
    label = label.replace("the_", "").replace("_", " ");
    label = label.charAt(0).toUpperCase() + label.slice(1);
    
    let btnClass = "dim-btn";
    if (dim.includes("overworld")) btnClass += " is-overworld";
    else if (dim.includes("nether")) btnClass += " is-nether";
    else if (dim.includes("end")) btnClass += " is-end";
    else btnClass += " is-custom";
    
    if (isActive) btnClass += " active";
    
    return `<button class="mc-button ${btnClass}" data-dim="${dim}" style="padding: 4px; font-size: 13px;">${label}</button>`;
  }).join("");

  dimSelector.querySelectorAll(".dim-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      dimSelector.querySelectorAll(".dim-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      mapActiveDimension = btn.dataset.dim;
      mapHovered = null;
      mapSelected = null;
      renderMapSidebar();
      centerOnSelectedPlayer();
      requestRender();
    });
  });
}

function renderSelectionDetail(item) {
  const content = document.querySelector("#mapSelectionContent");
  if (!content) return;
  
  let html = "";
  if (item.type === "online_player") {
    html = `
      <div class="map-selection-card">
        <h4>👤 ${item.data.name} <span class="dim-badge overworld" style="background:#55ff55; color:#000;">Live</span></h4>
        <p><strong>Posición:</strong> ${Math.round(item.data.x)}, ${Math.round(item.data.y)}, ${Math.round(item.data.z)}</p>
        <p><strong>Dimensión:</strong> <span class="highlight">${item.data.dimension.split(":")[1] || item.data.dimension}</span></p>
        <p style="color:#85c43f; font-weight:bold; margin-top:10px;">🟢 Conectado actualmente al mundo de Permaworld.</p>
      </div>
    `;
  } else if (item.type === "structure") {
    const dim = item.data.dimension.includes("nether") ? "nether" : (item.data.dimension.includes("end") ? "end" : "overworld");
    html = `
      <div class="map-selection-card">
        <h4>◈ ${item.data.name} <span class="dim-badge ${dim}">${dim}</span></h4>
        <p><strong>ID Oficial:</strong> <code>${item.data.structureId}</code></p>
        <p><strong>Coordenadas Chunks:</strong> ${item.data.coords}</p>
        <p><strong>Ubicación Bloques:</strong> X: ${Math.round(item.data.x)}, Y: ${Math.round(item.data.y)}, Z: ${Math.round(item.data.z)}</p>
        <p><strong>Descubierto por:</strong> <span class="highlight">${item.data.discoveredBy}</span></p>
        <p><strong>Fecha de Visita:</strong> ${new Date(item.data.timestamp).toLocaleString()}</p>
      </div>
    `;
  } else if (item.type === "death") {
    html = `
      <div class="map-selection-card">
        <h4 style="color:#ff5555;">💀 Registro de Muerte</h4>
        <p><strong>Jugador:</strong> <span class="highlight">${item.playerName}</span></p>
        <p><strong>Ubicación:</strong> X: ${Math.round(item.data.x)}, Y: ${Math.round(item.data.y)}, Z: ${Math.round(item.data.z)}</p>
        <p><strong>Fecha y hora:</strong> ${new Date(item.data.timestamp).toLocaleString()}</p>
        <p style="color:#ff7777; font-size:9px; margin-top:8px;">⚠️ El jugador perdió su equipamiento en esta coordenada de muerte.</p>
      </div>
    `;
  } else if (item.type === "path_point") {
    html = `
      <div class="map-selection-card">
        <h4>🗺 Exploración Registrada</h4>
        <p><strong>Jugador:</strong> <span class="highlight">${item.playerName}</span></p>
        <p><strong>Coordenadas:</strong> X: ${Math.round(item.data.x)}, Z: ${Math.round(item.data.z)}</p>
        <p><strong>Altitud (Y):</strong> ${Math.round(item.data.y)}</p>
        <p><strong>Fecha de exploración:</strong> ${new Date(item.data.timestamp).toLocaleString()}</p>
      </div>
    `;
  }
  
  content.innerHTML = html;
}

function centerOnSelectedPlayer() {
  if (!state.selectedPlayer || !mapData || !mapData.paths) return;
  const uuid = state.selectedPlayer.uuid;
  const pts = mapData.paths[uuid];
  
  if (pts && pts.length > 0) {
    const activePts = pts.filter((p) => p.dimension === mapActiveDimension);
    if (activePts.length > 0) {
      const latest = activePts[activePts.length - 1];
      centerOn(latest.x, latest.z);
    } else {
      const latest = pts[pts.length - 1];
      mapActiveDimension = latest.dimension;
      
      const dimButtons = document.querySelectorAll(".dim-btn");
      if (dimButtons.length > 0) {
        dimButtons.forEach((b) => b.classList.toggle("active", b.dataset.dim === mapActiveDimension));
      }
      centerOn(latest.x, latest.z);
    }
  } else {
    const p = mapData.players[uuid];
    if (p && p.x !== undefined) {
      mapActiveDimension = p.dimension;
      const dimButtons = document.querySelectorAll(".dim-btn");
      if (dimButtons.length > 0) {
        dimButtons.forEach((b) => b.classList.toggle("active", b.dataset.dim === mapActiveDimension));
      }
      centerOn(p.x, p.z);
    } else {
      centerOn(0, 0);
    }
  }
  renderMapSidebar();
}

function resizeCanvas() {
  if (!mapCanvas) return;
  const parent = mapCanvas.parentElement;
  const w = parent.clientWidth || 800;
  const h = parent.clientHeight || 520;
  
  if (mapCanvas.width !== w || mapCanvas.height !== h) {
    mapCanvas.width = w;
    mapCanvas.height = h;
    requestRender();
  }
}

function changeZoom(factor, cursorX, cursorY) {
  const mcBefore = canvasToMc(cursorX, cursorY);
  mapZoom = Math.max(0.04, Math.min(25, mapZoom * factor));
  const mcAfter = mcToCanvas(mcBefore.x, mcBefore.z);
  mapPanX += cursorX - mcAfter.x;
  mapPanY += cursorY - mcAfter.y;
  requestRender();
}

function centerOn(mcX, mcZ) {
  if (!mapCanvas) return;
  mapPanX = 0;
  mapPanY = 0;
  const cPos = mcToCanvas(mcX, mcZ);
  mapPanX = mapCanvas.width / 2 - cPos.x;
  mapPanY = mapCanvas.height / 2 - cPos.y;
}

function mcToCanvas(mcX, mcZ) {
  if (!mapCanvas) return { x: 0, y: 0 };
  const canvasX = mapCanvas.width / 2 + (mcX * mapZoom) + mapPanX;
  const canvasY = mapCanvas.height / 2 + (mcZ * mapZoom) + mapPanY;
  return { x: canvasX, y: canvasY };
}

function canvasToMc(canvasX, canvasY) {
  if (!mapCanvas) return { x: 0, z: 0 };
  const mcX = (canvasX - mapCanvas.width / 2 - mapPanX) / mapZoom;
  const mcZ = (canvasY - mapCanvas.height / 2 - mapPanY) / mapZoom;
  return { x: mcX, z: mcZ };
}

function updateCoordsHUD(mcX, mcZ) {
  const coordsDiv = document.querySelector("#mapCoords");
  if (coordsDiv) {
    coordsDiv.textContent = `X: ${Math.round(mcX)}, Z: ${Math.round(mcZ)} · Zoom: ${(mapZoom * 100).toFixed(0)}%`;
  }
}

let renderRequested = false;
function requestRender() {
  if (!renderRequested) {
    renderRequested = true;
    requestAnimationFrame(renderLoop);
  }
}

function renderLoop() {
  renderRequested = false;
  drawMap();
}

function drawMap() {
  if (!mapCanvas || !mapCtx) return;
  const ctx = mapCtx;
  const canvas = mapCanvas;
  
  let voidColor = "#0b0d10";
  let gridColor = "#19281a";
  let textCol = "#557f57";
  
  if (mapActiveDimension === "minecraft:the_nether") {
    voidColor = "#120808";
    gridColor = "#3c1212";
    textCol = "#994c4c";
  } else if (mapActiveDimension === "minecraft:the_end") {
    voidColor = "#0b0812";
    gridColor = "#2c1544";
    textCol = "#774ca3";
  }
  
  // Clear canvas completely using physical buffer dimensions in identity transform space
  ctx.save();
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = voidColor;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.restore();

  // Terrain Region Tiles Rendering (Aerial Photo)
  const showTerrain = document.querySelector("#chkTerrain")?.checked;
  if (showTerrain) {
    const minMc = canvasToMc(0, 0);
    const maxMc = canvasToMc(canvas.width, canvas.height);
    
    const minRegionX = Math.floor(minMc.x / 128);
    const maxRegionX = Math.ceil(maxMc.x / 128);
    const minRegionZ = Math.floor(minMc.z / 128);
    const maxRegionZ = Math.ceil(maxMc.z / 128);
    
    const totalVisibleRegions = (maxRegionX - minRegionX + 1) * (maxRegionZ - minRegionZ + 1);
    
    // Prevent fetching too many tiles if zoomed out excessively (increased limit for low-zoom rendering)
    if (totalVisibleRegions < 220) {
      for (let rx = minRegionX; rx <= maxRegionX; rx++) {
        for (let rz = minRegionZ; rz <= maxRegionZ; rz++) {
          const key = `${rx},${rz},${mapActiveDimension}`;
          if (!regionImages[key]) {
            const img = new Image();
            img.src = `/api/map/region?rx=${rx}&rz=${rz}&dim=${encodeURIComponent(mapActiveDimension)}`;
            regionImages[key] = { loaded: false, failed: false, image: img };
            img.onload = () => {
              regionImages[key].loaded = true;
              requestRender();
            };
            img.onerror = () => {
              regionImages[key].loaded = true;
              regionImages[key].failed = true;
            };
          } else if (regionImages[key].loaded && !regionImages[key].failed) {
            const cPos = mcToCanvas(rx * 128, rz * 128);
            const size = 128 * mapZoom;
            
            ctx.save();
            ctx.imageSmoothingEnabled = false; // Keep it crisp and pixelated!
            ctx.drawImage(regionImages[key].image, cPos.x, cPos.y, size, size);
            ctx.restore();
          }
        }
      }
    }
  }
  
  // Spacing scales dynamically based on zoom
  const spacing = mapZoom > 3.0 ? 50 : (mapZoom > 0.8 ? 100 : (mapZoom > 0.18 ? 500 : 2000));
  const startX = Math.floor(canvasToMc(0, 0).x / spacing) * spacing;
  const endX = Math.ceil(canvasToMc(canvas.width, 0).x / spacing) * spacing;
  const startZ = Math.floor(canvasToMc(0, 0).z / spacing) * spacing;
  const endZ = Math.ceil(canvasToMc(0, canvas.height).z / spacing) * spacing;
  
  ctx.strokeStyle = gridColor;
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let x = startX; x <= endX; x += spacing) {
    const cX = mcToCanvas(x, 0).x;
    ctx.moveTo(cX, 0);
    ctx.lineTo(cX, canvas.height);
  }
  for (let z = startZ; z <= endZ; z += spacing) {
    const cY = mcToCanvas(0, z).y;
    ctx.moveTo(0, cY);
    ctx.lineTo(canvas.width, cY);
  }
  ctx.stroke();
  
  if (mapZoom > 0.06) {
    // Scale grid label font up when zoomed out so coordinates stay readable
    const gridFontSize = Math.round(Math.max(9, Math.min(16, 9 / mapZoom * 0.18)));
    ctx.fillStyle = textCol;
    ctx.font = `${gridFontSize}px monospace`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";
    for (let x = startX; x <= endX; x += spacing) {
      if (x % (spacing * 2) === 0) {
        const cX = mcToCanvas(x, 0).x;
        ctx.fillText(x, cX + 4, 4);
      }
    }
    ctx.textAlign = "left";
    ctx.textBaseline = "bottom";
    for (let z = startZ; z <= endZ; z += spacing) {
      if (z % (spacing * 2) === 0) {
        const cY = mcToCanvas(0, z).y;
        ctx.fillText(`Z: ${z}`, 4, cY - 2);
      }
    }
  }

  // Draw Origin (0, 0) as crosshair
  const origin = mcToCanvas(0, 0);
  ctx.strokeStyle = "rgba(228, 206, 87, 0.45)";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.arc(origin.x, origin.y, 4, 0, Math.PI * 2);
  ctx.moveTo(origin.x - 10, origin.y);
  ctx.lineTo(origin.x + 10, origin.y);
  ctx.moveTo(origin.x, origin.y - 10);
  ctx.lineTo(origin.x, origin.y + 10);
  ctx.stroke();
  
  const showPaths = document.querySelector("#chkPaths")?.checked;
  const showStructures = document.querySelector("#chkStructures")?.checked;
  const showDeaths = document.querySelector("#chkDeaths")?.checked;
  const showOnline = document.querySelector("#chkOnline")?.checked;

  // Paths rendering
  if (showPaths && mapData.paths) {
    for (const uuid in mapData.paths) {
      if (state.mapVisiblePlayers && !state.mapVisiblePlayers.has(uuid)) continue;
      const pts = mapData.paths[uuid];
      if (!pts || pts.length === 0) continue;
      
      const isSelectedPlayer = state.selectedPlayer && state.selectedPlayer.uuid === uuid;
      const color = getPlayerColor(uuid);
      
      ctx.save();
      ctx.strokeStyle = color;
      
      if (isSelectedPlayer) {
        ctx.lineWidth = 3.5;
        ctx.shadowBlur = 8;
        ctx.shadowColor = color;
        ctx.globalAlpha = 1.0;
      } else {
        ctx.lineWidth = 1.8;
        ctx.shadowBlur = 0;
        ctx.globalAlpha = 0.55;
      }
      
      let drawing = false;
      for (let i = 0; i < pts.length; i++) {
        const pt = pts[i];
        if (pt.dimension === mapActiveDimension) {
          const cPos = mcToCanvas(pt.x, pt.z);
          if (!drawing) {
            ctx.beginPath();
            ctx.moveTo(cPos.x, cPos.y);
            drawing = true;
          } else {
            ctx.lineTo(cPos.x, cPos.y);
          }
        } else {
          if (drawing) {
            ctx.stroke();
            drawing = false;
          }
        }
      }
      if (drawing) {
        ctx.stroke();
      }
      ctx.restore();
      
      if (mapZoom > 0.15) {
        // Scale dot radius inversely so dots stay visible when zoomed out
        const dotR = Math.max(1.5, Math.min(4, 2 / mapZoom * 0.5));
        ctx.fillStyle = color;
        ctx.globalAlpha = isSelectedPlayer ? 1.0 : 0.6;
        for (const pt of pts) {
          if (pt.dimension === mapActiveDimension && pt.type === "PATH_SAMPLE") {
            const cPos = mcToCanvas(pt.x, pt.z);
            ctx.beginPath();
            ctx.arc(cPos.x, cPos.y, dotR, 0, Math.PI * 2);
            ctx.fill();
          }
        }
      }
    }
  }

  // Structures rendering
  if (showStructures && mapData.structures) {
    for (const struct of mapData.structures) {
      if (struct.dimension !== mapActiveDimension) continue;
      if (struct.x === undefined) continue;
      
      const cPos = mcToCanvas(struct.x, struct.z);
      const isHovered = mapHovered && mapHovered.type === "structure" && mapHovered.data.coords === struct.coords;
      const isSelected = mapSelected && mapSelected.type === "structure" && mapSelected.data.coords === struct.coords;
      
      // Scale diamond size so structures are always visible regardless of zoom
      const sDiam = Math.max(5, Math.min(9, 6 / mapZoom * 0.4));
      
      ctx.save();
      
      if (isHovered || isSelected) {
        ctx.strokeStyle = "rgba(228, 206, 87, 0.8)";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(cPos.x, cPos.y, sDiam + 4, 0, Math.PI*2);
        ctx.stroke();
      }
      
      ctx.fillStyle = struct.dimension.includes("nether") ? "#e74c3c" : (struct.dimension.includes("end") ? "#9b59b6" : "#2ecc71");
      ctx.strokeStyle = "#050505";
      ctx.lineWidth = 1.5;
      
      ctx.beginPath();
      ctx.moveTo(cPos.x, cPos.y - sDiam);
      ctx.lineTo(cPos.x + sDiam, cPos.y);
      ctx.lineTo(cPos.x, cPos.y + sDiam);
      ctx.lineTo(cPos.x - sDiam, cPos.y);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
      
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(cPos.x - 1, cPos.y - 1, 2, 2);
      
      if (mapZoom > 0.25) {
        const labelFontSize = Math.round(Math.max(8, Math.min(13, 8 / mapZoom * 0.35)));
        ctx.font = `${labelFontSize}px monospace`;
        const nameWidth = ctx.measureText(struct.name).width + 6;
        
        ctx.fillStyle = "rgba(10, 12, 15, 0.85)";
        ctx.strokeStyle = "#050505";
        ctx.lineWidth = 1;
        
        ctx.beginPath();
        ctx.rect(cPos.x - nameWidth / 2, cPos.y + sDiam + 2, nameWidth, labelFontSize + 2);
        ctx.fill();
        ctx.stroke();
        
        ctx.fillStyle = "#ffffff";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(struct.name, cPos.x, cPos.y + sDiam + labelFontSize / 2 + 3);
      }
      ctx.restore();
    }
  }

  // Deaths rendering
  if (showDeaths && mapData.paths) {
    for (const uuid in mapData.paths) {
      if (state.mapVisiblePlayers && !state.mapVisiblePlayers.has(uuid)) continue;
      const pts = mapData.paths[uuid];
      for (const pt of pts) {
        if (pt.type === "DEATH" && pt.dimension === mapActiveDimension) {
          const cPos = mcToCanvas(pt.x, pt.z);
          const isHovered = mapHovered && mapHovered.type === "death" && mapHovered.data.id === pt.id;
          
          ctx.save();
          if (isHovered) {
            ctx.shadowBlur = 8;
            ctx.shadowColor = "#ff3333";
          }
          
          // Scale death skull inversely so it stays visible when zoomed out
          const skullSize = Math.round(Math.max(12, Math.min(22, 14 / mapZoom * 0.5)));
          ctx.font = `${skullSize}px monospace`;
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText("💀", cPos.x, cPos.y);
          ctx.restore();
        }
      }
    }
  }

  // Online Players live rendering
  if (showOnline && mapData.players) {
    for (const uuid in mapData.players) {
      if (state.mapVisiblePlayers && !state.mapVisiblePlayers.has(uuid)) continue;
      const p = mapData.players[uuid];
      if (!p.online || p.dimension !== mapActiveDimension || p.x === undefined) continue;
      
      const cPos = mcToCanvas(p.x, p.z);
      const color = getPlayerColor(uuid);
      const isHovered = mapHovered && mapHovered.type === "online_player" && mapHovered.uuid === uuid;
      
      // Scale player avatar inversely so it stays visible when zoomed out
      const pRadius = Math.max(8, Math.min(14, 10 / mapZoom * 0.45));
      const pAvatar = pRadius * 1.6;
      
      ctx.save();
      
      ctx.strokeStyle = isHovered ? "#ffffff" : color;
      ctx.lineWidth = isHovered ? 2.5 : 1.5;
      ctx.beginPath();
      ctx.arc(cPos.x, cPos.y, pRadius + 3, 0, Math.PI * 2);
      ctx.stroke();
      
      getPlayerFace(p.name, (img) => {
        ctx.drawImage(img, cPos.x - pAvatar / 2, cPos.y - pAvatar / 2, pAvatar, pAvatar);
      });
      
      if (!skinImages[p.name] || !skinImages[p.name].loaded) {
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(cPos.x, cPos.y, pRadius, 0, Math.PI * 2);
        ctx.fill();
        
        ctx.fillStyle = "#ffffff";
        const initFontSize = Math.round(Math.max(9, Math.min(14, 9 / mapZoom * 0.35)));
        ctx.font = `bold ${initFontSize}px monospace`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(p.name.charAt(0).toUpperCase(), cPos.x, cPos.y);
      }
      
      const nameFontSize = Math.round(Math.max(8, Math.min(13, 8 / mapZoom * 0.35)));
      ctx.font = `${nameFontSize}px monospace`;
      const nameWidth = ctx.measureText(p.name).width + 6;
      const nameY = cPos.y - pRadius - nameFontSize - 4;
      
      ctx.fillStyle = "rgba(10, 12, 15, 0.85)";
      ctx.strokeStyle = "#050505";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.rect(cPos.x - nameWidth / 2, nameY, nameWidth, nameFontSize + 2);
      ctx.fill();
      ctx.stroke();
      
      ctx.fillStyle = "#ffffff";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(p.name, cPos.x, nameY + nameFontSize / 2 + 1);
      
      ctx.restore();
    }
  }
}
