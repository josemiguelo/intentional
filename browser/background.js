// Intentional in Brave: the desktop guard's eyes on the browser. It reports
// the active tab's site of the last focused window (the guard counts it only
// while Hyprland says Brave has focus), and applies the guard's list of
// over-budget sites: their tabs move to blocked.html and new visits are
// redirected there. The key in manifest.json pins this extension's ID, which
// the native host's manifest allows.

const HOST = "dev.josemiguel.intentional";

let port = null;
let blocked = [];

function hostOf(url) {
  try {
    const u = new URL(url);
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    return u.hostname.replace(/^www\./, "");
  } catch {
    return null;
  }
}

const covers = (domain, host) => host === domain || host.endsWith("." + domain);
const isBlocked = (host) => host != null && blocked.some((d) => covers(d, host));
const blockedPage = (host) => chrome.runtime.getURL("blocked.html") + "?site=" + encodeURIComponent(host);

function connect() {
  port = chrome.runtime.connectNative(HOST);
  port.onMessage.addListener((m) => {
    if (Array.isArray(m.blocked)) applyBlocked(m.blocked);
  });
  // No daemon (not running, or restarting): try again shortly.
  port.onDisconnect.addListener(() => {
    port = null;
    setTimeout(connect, 5000);
  });
  report();
}

async function report() {
  if (!port) return;
  const win = await chrome.windows.getLastFocused().catch(() => null);
  const [tab] = win ? await chrome.tabs.query({ active: true, windowId: win.id }) : [];
  port.postMessage({ type: "tab", host: tab ? hostOf(tab.url ?? "") : null, focused: !!win?.focused });
}

// A tab left on a blocked site would be the dead end the browser reopens
// on, so open ones move to the blocked page, and new visits are redirected
// there by rules that last across restarts until the guard clears them.
async function applyBlocked(list) {
  blocked = list;
  const old = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: old.map((r) => r.id),
    addRules: list.map((domain, i) => ({
      id: i + 1,
      priority: 1,
      action: { type: "redirect", redirect: { extensionPath: "/blocked.html" } },
      condition: { requestDomains: [domain], resourceTypes: ["main_frame", "sub_frame"] },
    })),
  });
  for (const tab of await chrome.tabs.query({})) {
    const host = hostOf(tab.url ?? "");
    if (isBlocked(host)) chrome.tabs.update(tab.id, { url: blockedPage(host) });
  }
  report();
}

chrome.tabs.onActivated.addListener(report);
chrome.windows.onFocusChanged.addListener(report);
chrome.tabs.onUpdated.addListener((id, info) => {
  if (!info.url) return;
  // Sites that change pages without a navigation (pushState) skip the rules.
  const host = hostOf(info.url);
  if (isBlocked(host)) chrome.tabs.update(id, { url: blockedPage(host) });
  else report();
});

connect();
