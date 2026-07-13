const graphData = window.SECOND_BRAIN_GRAPH;
const { groups, nodes, highlights, graphifyRuns, wikiPages, codeCards, sources } = graphData;

const edges = buildEdges(nodes);
const state = {
  group: "all",
  query: "",
  selected: nodes[0],
};

const pinnedPositions = {
  "career-tuner": [640, 390],
  "portfolio-graph": [640, 285],
  "code-map": [640, 500],
  "graphify-extract": [135, 410],
  "spring-api": [960, 190],
  "react-spa": [330, 585],
  "ai-orchestrator": [815, 575],
  "schema": [1090, 390],
  "web-demo": [690, 130],
  "obsidian-wiki": [160, 315],
  "application-case": [250, 190],
  "admin-ui": [560, 665],
};

const graph = document.getElementById("graph");
const filters = document.getElementById("groupFilters");
const searchInput = document.getElementById("searchInput");
const detailGroup = document.getElementById("detailGroup");
const detailTitle = document.getElementById("detailTitle");
const detailSummary = document.getElementById("detailSummary");
const detailPoints = document.getElementById("detailPoints");
const detailPath = document.getElementById("detailPath");
const detailWikiLink = document.getElementById("detailWikiLink");
const neighborList = document.getElementById("neighborList");
const resultCount = document.getElementById("resultCount");
const selectionStatus = document.getElementById("selectionStatus");
const highlightGrid = document.getElementById("highlightGrid");
const runTable = document.getElementById("runTable");
const wikiGrid = document.getElementById("wikiGrid");
const codeGrid = document.getElementById("codeGrid");
const sourceCards = document.getElementById("sourceCards");
const zoomOutButton = document.getElementById("zoomOutButton");
const zoomInButton = document.getElementById("zoomInButton");
const fitButton = document.getElementById("fitButton");
const focusButton = document.getElementById("focusButton");

const graphViewBox = { width: 1280, height: 820 };
const graphPan = {
  minScale: 0.56,
  maxScale: 2.7,
  scale: 0.76,
  x: 0,
  y: 0,
  dragging: false,
  dragStart: null,
  pointerId: null,
};

applyLayout();
const graphBounds = computeGraphBounds();
fitGraphView({ update: false });

document.getElementById("metricNodes").textContent = String(nodes.length);
document.getElementById("metricEdges").textContent = String(edges.length);
document.getElementById("metricGraphify").textContent = "26,886";
document.getElementById("metricScope").textContent = "2,870";

function buildEdges(items) {
  const known = new Set(items.map((item) => item.id));
  const map = new Map();
  items.forEach((item) => {
    (item.links || []).forEach((target) => {
      if (!known.has(target) || item.id === target) return;
      const key = [item.id, target].sort().join("__");
      if (!map.has(key)) map.set(key, { source: item.id, target, kind: "related" });
    });
  });
  return [...map.values()];
}

function applyLayout() {
  Object.entries(pinnedPositions).forEach(([id, [x, y]]) => {
    const item = getNode(id);
    if (!item) return;
    item.x = x;
    item.y = y;
    item.pinned = true;
  });

  Object.keys(groups).forEach((groupKey) => {
    const bucket = nodes.filter((item) => item.group === groupKey && !item.pinned);
    const group = groups[groupKey];
    bucket.forEach((item, index) => {
      const angle = (-Math.PI / 2) + (index / Math.max(bucket.length, 1)) * Math.PI * 2;
      const ring = 78 + (index % 4) * 34 + Math.floor(index / 10) * 16;
      item.x = Math.round(group.cx + Math.cos(angle) * ring);
      item.y = Math.round(group.cy + Math.sin(angle) * ring);
    });
  });
}

function getNode(id) {
  return nodes.find((item) => item.id === id);
}

function getNeighbors(id) {
  const ids = new Set();
  edges.forEach((item) => {
    if (item.source === id) ids.add(item.target);
    if (item.target === id) ids.add(item.source);
  });
  return [...ids].map(getNode).filter(Boolean);
}

function matches(item) {
  const groupMatch = state.group === "all" || item.group === state.group;
  const query = state.query.trim().toLowerCase();
  const text = [
    item.label,
    item.type,
    item.summary,
    item.path,
    item.evidence?.label,
    groups[item.group].label,
    ...(item.points || []),
    ...getNeighbors(item.id).map((neighbor) => neighbor.label),
  ].join(" ").toLowerCase();
  return groupMatch && text.includes(query);
}

function isRelatedToSelection(item) {
  if (!state.selected) return false;
  return item.id === state.selected.id || getNeighbors(state.selected.id).some((neighbor) => neighbor.id === item.id);
}

function edgeTouchesSelection(item) {
  return item.source === state.selected.id || item.target === state.selected.id;
}

function edgeTouchesVisible(item) {
  return matches(getNode(item.source)) || matches(getNode(item.target));
}

function selectNode(item) {
  state.selected = item;
  selectionStatus.textContent = `Selected: ${item.label}`;
  detailGroup.textContent = `${groups[item.group].label} · ${item.type}`;
  detailTitle.textContent = item.label;
  detailSummary.textContent = item.summary;
  detailPath.textContent = item.evidence?.visibility === "private"
    ? item.evidence.label
    : item.path || item.evidence?.label || "";
  detailWikiLink.href = `../Wiki/#evidence/${encodeURIComponent(item.id)}`;
  detailWikiLink.setAttribute("aria-label", `${item.label} Wiki evidence 읽기`);
  detailPoints.textContent = "";
  (item.points || []).forEach((point) => {
    const li = document.createElement("li");
    li.textContent = point;
    detailPoints.append(li);
  });
  renderNeighbors();
  render();
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function computeGraphBounds() {
  const padding = 100;
  return nodes.reduce((bounds, item) => ({
    minX: Math.min(bounds.minX, item.x - item.weight - padding),
    maxX: Math.max(bounds.maxX, item.x + item.weight + padding),
    minY: Math.min(bounds.minY, item.y - item.weight - padding),
    maxY: Math.max(bounds.maxY, item.y + item.weight + padding),
  }), {
    minX: Infinity,
    maxX: -Infinity,
    minY: Infinity,
    maxY: -Infinity,
  });
}

function getPanRange(axis) {
  const isX = axis === "x";
  const viewportSize = isX ? graphViewBox.width : graphViewBox.height;
  const minBound = isX ? graphBounds.minX : graphBounds.minY;
  const maxBound = isX ? graphBounds.maxX : graphBounds.maxY;
  const margin = 120;
  let min = viewportSize - maxBound * graphPan.scale - margin;
  let max = margin - minBound * graphPan.scale;

  if (min > max) {
    const center = (viewportSize - (minBound + maxBound) * graphPan.scale) / 2;
    min = center - margin;
    max = center + margin;
  }

  return { min, max };
}

function clampPan() {
  graphPan.scale = clamp(graphPan.scale, graphPan.minScale, graphPan.maxScale);
  const rangeX = getPanRange("x");
  const rangeY = getPanRange("y");
  graphPan.x = clamp(graphPan.x, rangeX.min, rangeX.max);
  graphPan.y = clamp(graphPan.y, rangeY.min, rangeY.max);
}

function getGraphPoint(event) {
  const rect = graph.getBoundingClientRect();
  return {
    x: ((event.clientX - rect.left) / rect.width) * graphViewBox.width,
    y: ((event.clientY - rect.top) / rect.height) * graphViewBox.height,
  };
}

function zoomGraph(factor, origin = { x: graphViewBox.width / 2, y: graphViewBox.height / 2 }) {
  const previousScale = graphPan.scale;
  const nextScale = clamp(previousScale * factor, graphPan.minScale, graphPan.maxScale);
  const ratio = nextScale / previousScale;
  graphPan.x = origin.x - ratio * (origin.x - graphPan.x);
  graphPan.y = origin.y - ratio * (origin.y - graphPan.y);
  graphPan.scale = nextScale;
  clampPan();
  updateGraphTransform();
}

function fitGraphView({ update = true } = {}) {
  const padding = 70;
  const graphWidth = graphBounds.maxX - graphBounds.minX;
  const graphHeight = graphBounds.maxY - graphBounds.minY;
  const scaleX = (graphViewBox.width - padding * 2) / graphWidth;
  const scaleY = (graphViewBox.height - padding * 2) / graphHeight;
  graphPan.scale = clamp(Math.min(scaleX, scaleY), graphPan.minScale, 1.05);
  graphPan.x = (graphViewBox.width - (graphBounds.minX + graphBounds.maxX) * graphPan.scale) / 2;
  graphPan.y = (graphViewBox.height - (graphBounds.minY + graphBounds.maxY) * graphPan.scale) / 2;
  clampPan();
  if (update) updateGraphTransform();
}

function focusSelectedNode() {
  if (!state.selected) return;
  graphPan.scale = Math.max(graphPan.scale, 1.1);
  graphPan.x = (graphViewBox.width / 2 - state.selected.x * graphPan.scale);
  graphPan.y = (graphViewBox.height / 2 - state.selected.y * graphPan.scale);
  clampPan();
  updateGraphTransform();
}

function updateGraphTransform() {
  const viewport = graph.querySelector(".graph-viewport");
  if (!viewport) return;
  viewport.setAttribute("transform", `translate(${graphPan.x} ${graphPan.y}) scale(${graphPan.scale})`);
}

function renderFilters() {
  const all = document.createElement("button");
  all.type = "button";
  all.className = "segment active";
  all.dataset.group = "all";
  all.textContent = "All";
  filters.append(all);

  Object.entries(groups).forEach(([key, group]) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "segment";
    button.dataset.group = key;
    button.textContent = group.label;
    filters.append(button);
  });

  filters.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-group]");
    if (!button) return;
    state.group = button.dataset.group;
    filters.querySelectorAll(".segment").forEach((item) => item.classList.toggle("active", item === button));
    render();
  });
}

function renderGraph() {
  graph.textContent = "";
  const fragment = document.createDocumentFragment();
  const viewport = document.createElementNS("http://www.w3.org/2000/svg", "g");
  viewport.classList.add("graph-viewport");

  edges.forEach((item) => {
    const from = getNode(item.source);
    const to = getNode(item.target);
    const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
    line.setAttribute("x1", from.x);
    line.setAttribute("y1", from.y);
    line.setAttribute("x2", to.x);
    line.setAttribute("y2", to.y);
    line.classList.add("edge");
    if (edgeTouchesSelection(item)) line.classList.add("selected");
    if (!edgeTouchesVisible(item)) line.classList.add("dimmed");
    viewport.append(line);
  });

  nodes.forEach((item) => {
    const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
    group.classList.add("node", `node-${item.type}`);
    if (item.id === state.selected.id) group.classList.add("selected");
    if (isRelatedToSelection(item)) group.classList.add("related");
    if (!matches(item)) group.classList.add("dimmed");
    group.setAttribute("transform", `translate(${item.x}, ${item.y})`);
    group.setAttribute("tabindex", "0");
    group.setAttribute("role", "button");
    group.setAttribute("aria-label", item.label);
    group.setAttribute("aria-pressed", String(item.id === state.selected.id));
    group.addEventListener("pointerdown", (event) => event.stopPropagation());
    group.addEventListener("click", (event) => {
      event.stopPropagation();
      selectNode(item);
    });
    group.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        selectNode(item);
      }
    });

    const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    circle.setAttribute("r", item.weight);
    circle.setAttribute("fill", groups[item.group].color);

    const title = document.createElementNS("http://www.w3.org/2000/svg", "text");
    title.setAttribute("text-anchor", "middle");
    title.setAttribute("y", item.weight + 15);
    title.textContent = item.label;

    const type = document.createElementNS("http://www.w3.org/2000/svg", "text");
    type.setAttribute("text-anchor", "middle");
    type.setAttribute("y", item.weight + 29);
    type.classList.add("sub");
    type.textContent = groups[item.group].label;

    if (shouldShowLabel(item)) group.append(circle, title, type);
    else group.append(circle);
    viewport.append(group);
  });

  fragment.append(viewport);
  graph.append(fragment);
  updateGraphTransform();
}

function shouldShowLabel(item) {
  if (item.id === state.selected.id || isRelatedToSelection(item)) return true;
  if (!matches(item)) return false;
  return item.weight >= 18 || state.query.trim().length > 0 || state.group !== "all";
}

function renderNeighbors() {
  neighborList.textContent = "";
  getNeighbors(state.selected.id)
    .sort((a, b) => groups[a.group].label.localeCompare(groups[b.group].label, "ko"))
    .forEach((neighbor) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "neighbor";
      button.innerHTML = `<span>${escapeHtml(neighbor.label)}</span><small>${escapeHtml(groups[neighbor.group].label)}</small>`;
      button.addEventListener("click", () => selectNode(neighbor));
      neighborList.append(button);
    });
}

function renderHighlights() {
  highlightGrid.textContent = "";
  highlights.forEach((item) => {
    const card = document.createElement("article");
    card.className = "highlight-card";
    card.innerHTML = `
      <strong>${escapeHtml(item.metric)}</strong>
      <h3>${escapeHtml(item.title)}</h3>
      <p>${escapeHtml(item.summary)}</p>
    `;
    highlightGrid.append(card);
  });
}

function renderRunTable() {
  runTable.textContent = "";
  graphifyRuns.forEach((item) => {
    const row = document.createElement("article");
    row.className = "run-row";
    row.innerHTML = `
      <div>
        <strong>${escapeHtml(item.label)}</strong>
        <small>${escapeHtml(item.scope)}</small>
      </div>
      <span>${item.files.toLocaleString()} files</span>
      <span>${item.nodes.toLocaleString()} nodes</span>
      <span>${item.edges.toLocaleString()} edges</span>
      <p>${escapeHtml(item.note)}</p>
    `;
    runTable.append(row);
  });
}

function renderWiki() {
  wikiGrid.textContent = "";
  wikiPages.forEach((item) => {
    wikiGrid.append(createInfoCard(item.title, item.summary, item.path, ["Wiki"], `../Wiki/#${item.wikiId}`));
  });
}

function renderCodeCards() {
  codeGrid.textContent = "";
  codeCards.forEach((item) => {
    const evidenceLabel = item.evidence?.visibility === "private" ? item.evidence.label : item.path;
    codeGrid.append(createInfoCard(item.title, item.summary, evidenceLabel, item.tags));
  });
}

function renderSources() {
  sourceCards.textContent = "";
  sources.forEach((item) => {
    const card = document.createElement("article");
    card.className = "source-card";
    const isExternal = /^https?:\/\//.test(item.href);
    card.innerHTML = `
      <h3>${escapeHtml(item.title)}</h3>
      <p>${escapeHtml(item.summary)}</p>
      <a href="${escapeAttribute(item.href)}"${isExternal ? ' target="_blank" rel="noreferrer"' : ""}>Source</a>
    `;
    sourceCards.append(card);
  });
}

function createInfoCard(title, summary, path, tags, href = "") {
  const card = document.createElement("article");
  card.className = "info-card";
  card.innerHTML = `
    <h3>${escapeHtml(title)}</h3>
    <p>${escapeHtml(summary)}</p>
    <code>${escapeHtml(path)}</code>
    <div class="tag-row">${(tags || []).map((tag) => `<span>${escapeHtml(tag)}</span>`).join("")}</div>
    ${href ? `<a class="card-link" href="${escapeAttribute(href)}">Wiki에서 읽기</a>` : ""}
  `;
  return card;
}

function render() {
  const visible = nodes.filter(matches);
  resultCount.textContent = `${visible.length} visible`;
  renderGraph();
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll("`", "&#096;");
}

searchInput.addEventListener("input", (event) => {
  state.query = event.target.value;
  render();
});

graph.addEventListener("pointerdown", (event) => {
  if (event.button !== 0 || event.target.closest?.(".node")) return;
  graphPan.dragging = true;
  graphPan.pointerId = event.pointerId;
  graphPan.dragStart = {
    x: event.clientX,
    y: event.clientY,
    panX: graphPan.x,
    panY: graphPan.y,
  };
  graph.classList.add("panning");
  graph.setPointerCapture(event.pointerId);
});

graph.addEventListener("pointermove", (event) => {
  if (!graphPan.dragging || graphPan.pointerId !== event.pointerId) return;
  const rect = graph.getBoundingClientRect();
  const dx = ((event.clientX - graphPan.dragStart.x) / rect.width) * graphViewBox.width;
  const dy = ((event.clientY - graphPan.dragStart.y) / rect.height) * graphViewBox.height;
  graphPan.x = graphPan.dragStart.panX + dx;
  graphPan.y = graphPan.dragStart.panY + dy;
  clampPan();
  updateGraphTransform();
});

function stopPanning(event) {
  if (graphPan.pointerId !== event.pointerId) return;
  graphPan.dragging = false;
  graphPan.pointerId = null;
  graphPan.dragStart = null;
  graph.classList.remove("panning");
  if (graph.hasPointerCapture(event.pointerId)) graph.releasePointerCapture(event.pointerId);
}

graph.addEventListener("pointerup", stopPanning);
graph.addEventListener("pointercancel", stopPanning);

graph.addEventListener("wheel", (event) => {
  event.preventDefault();
  const factor = event.deltaY < 0 ? 1.12 : 0.89;
  zoomGraph(factor, getGraphPoint(event));
}, { passive: false });

zoomOutButton.addEventListener("click", () => zoomGraph(0.86));
zoomInButton.addEventListener("click", () => zoomGraph(1.16));
fitButton.addEventListener("click", () => fitGraphView());
focusButton.addEventListener("click", focusSelectedNode);

renderFilters();
renderHighlights();
renderRunTable();
renderWiki();
renderCodeCards();
renderSources();
selectNode(nodes[0]);
