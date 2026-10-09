"use strict";

const VERSION = "1.4.2-web.2";
const STORAGE_KEY = "attendance-web-data-v2";
const SETTINGS_KEY = "attendance-web-settings-v1";
const TARDY = "Tardy";
const CALLED_OUT = "Called out";

const app = document.querySelector("#app");
const fileInput = document.querySelector("#import-file");
let state = loadData();
let settings = loadSettings();
let view = settings.defaultMode === "HOURS" ? "hours" : "tardy";
let settingsCategory = "general";
let notice = "";
let selectedTardyDate = today();
let selectedHoursDate = today();
let tardyFilter = "days30";
let selectedMonth = today().slice(0, 7);
let selectedYear = Number(today().slice(0, 4));
let hoursFilter = "currentWeek";
let historyType = TARDY;
let toastTimer;

function loadData() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return parsed && parsed.attendance && parsed.hours && parsed.deleted ? parsed : emptyData();
  } catch { return emptyData(); }
}
function emptyData() { return { attendance: {}, hours: {}, deleted: {} }; }
function loadSettings() {
  const defaults = { darkMode:false, defaultMode:"TARDY", trackLateMinutes:false, decimalHours:true, payPeriodStart:"", payPeriodEnd:"", attendanceButtonMode:"BOTH", attendanceButtonTarget:"TARDY", attendanceButtonTeal:false };
  try { return { ...defaults, ...JSON.parse(localStorage.getItem(SETTINGS_KEY)) }; }
  catch { return defaults; }
}
function saveData() { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); }
function saveSettings() { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)); applyTheme(); }
function applyTheme() { document.documentElement.classList.toggle("dark", settings.darkMode); }

function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}-${String(now.getDate()).padStart(2,"0")}`;
}
function parseDate(iso) { const [y,m,d] = iso.split("-").map(Number); return new Date(y,m-1,d); }
function isoDate(date) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`; }
function addDays(iso, count) { const date = parseDate(iso); date.setDate(date.getDate()+count); return isoDate(date); }
function daysBetween(a,b) { return Math.round((parseDate(b)-parseDate(a))/86400000); }
function fullDate(iso) { return new Intl.DateTimeFormat("en-US", {weekday:"long",month:"long",day:"numeric",year:"numeric"}).format(parseDate(iso)); }
function shortDate(iso) { return new Intl.DateTimeFormat("en-US", {weekday:"short",month:"short",day:"numeric",year:"numeric"}).format(parseDate(iso)); }
function reportDate(iso) { return new Intl.DateTimeFormat("en-US", {month:"short",day:"numeric",year:"numeric"}).format(parseDate(iso)); }
function escapeHtml(value) { return String(value).replace(/[&<>'"]/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[ch]); }
function duration(minutes) { const h=Math.floor(minutes/60), m=minutes%60; return h===0?`${m}m`:m===0?`${h}h`:`${h}h ${m}m`; }
function clockHours(minutes) { return `${Math.floor(minutes/60)}:${String(minutes%60).padStart(2,"0")}`; }
function decimalHours(minutes, fixed=false) { const value=minutes/60; return fixed ? value.toFixed(2) : String(Math.round(value*100)/100); }
function formatHours(minutes) { return settings.decimalHours ? `${decimalHours(minutes,true)} hours` : clockHours(minutes); }
function byDateDesc(a,b) { return b.date.localeCompare(a.date); }

function shell(title, subtitle, body, back=false) {
  return `<div class="app-shell">
    <header class="toolbar"><button class="icon-button" data-action="${back?"back":"menu"}" aria-label="${back?"Back to summary":"Open navigation"}">${back?"‹":"☰"}</button><h1>${escapeHtml(title)}</h1></header>
    <main class="page"><p class="subtitle">${escapeHtml(subtitle)}</p>${body}</main>
    ${drawer()}
  </div>`;
}
function drawer() {
  return `<div class="drawer-scrim" data-action="close-menu"></div><aside class="drawer" aria-label="Navigation">
    <button class="brand" data-action="default-view" style="border:0;background:transparent;color:inherit;text-align:left;padding:0"><img src="icons/icon-192.png" alt=""><span><span class="brand-name">Attendance</span><span class="brand-tag">Keep your own record.</span></span></button>
    <button class="nav-button" data-view="tardy"><span class="nav-icon">!</span>Tardy &amp; Call-Outs</button>
    <button class="nav-button" data-view="hours"><span class="nav-icon">◷</span>Hours</button>
    <span class="nav-spacer"></span>
    <button class="nav-button" data-view="settings"><span class="nav-icon">⚙</span>Settings</button>
  </aside>`;
}
function section(text) { return `<div class="section-title">${escapeHtml(text)}</div>`; }
function info(text, danger=false) { return `<div class="card info-card${danger?" danger-info":""}">${escapeHtml(text)}</div>`; }
function filterButton(label, filter, active) { return `<button class="filter-button${active?" active":""}" data-filter="${filter}">${escapeHtml(label)}</button>`; }
function historyRow(entry, kind) {
  const detail = kind === "hours" ? formatHours(entry.minutes) : entry.type === TARDY ? (entry.lateMinutes ? `Tardy • ${duration(entry.lateMinutes)} late` : "Tardy") : "Called Out";
  return `<div class="card history-row"><div class="history-copy"><div class="history-title">${escapeHtml(shortDate(entry.date))}</div><div class="history-detail">${escapeHtml(detail)}</div></div><div class="history-actions"><button class="outline" data-edit-${kind}="${entry.date}">Edit</button><button class="outline danger" data-delete-${kind}="${entry.date}">Delete</button></div></div>`;
}

function render() {
  applyTheme();
  if (view === "tardy") renderTardy();
  else if (view === "hours") renderHours();
  else if (view === "history") renderHistory();
  else renderSettings();
  bindCommon();
}

function renderTardy() {
  const existing = state.attendance[selectedTardyDate];
  const [from,to] = tardyRange();
  const entries = attendanceBetween(from,to);
  const tardies = entries.filter(e=>e.type===TARDY);
  const calls = entries.filter(e=>e.type===CALLED_OUT);
  const late = tardies.reduce((sum,e)=>sum+e.lateMinutes,0);
  const lateInputs = settings.trackLateMinutes ? `<div style="font-size:13px;color:var(--muted);font-weight:700;margin-bottom:8px">How late? (optional)</div><div class="button-row"><input id="late-hours" class="field" inputmode="numeric" maxlength="2" placeholder="Hours" value="${existing?.type===TARDY?Math.floor(existing.lateMinutes/60):""}"><input id="late-minutes" class="field" inputmode="numeric" maxlength="2" placeholder="Minutes" value="${existing?.type===TARDY?existing.lateMinutes%60:""}"></div><div class="spacer-10"></div>` : "";
  const body = `${section("DATE")}<div class="date-row"><button class="date-field" data-action="pick-tardy-date">${escapeHtml(fullDate(selectedTardyDate))} &nbsp; ▾</button><button class="outline" data-action="today" ${selectedTardyDate===today()?"disabled style=\"opacity:.45\"":""}>Today</button></div><input id="tardy-date" type="date" value="${selectedTardyDate}" max="${today()}" hidden>
    <div class="spacer-18"></div>${existing?`<p class="saved">Saved: ${escapeHtml(existing.type)}${existing.type===TARDY&&existing.lateMinutes?` • ${duration(existing.lateMinutes)} late`:""}. Saving again will update it.</p>`:""}
    ${section("SUMMARY")}<div class="filter-scroll">${tardyFilters()}</div>
    <div class="stats"><button class="card stat clickable" data-history="${CALLED_OUT}"><span class="stat-value">${calls.length}</span><span class="stat-caption">Call-Outs</span></button><button class="card stat clickable" data-history="${TARDY}"><span class="stat-value">${tardies.length}</span><span class="stat-caption">Tardies</span></button></div><div class="spacer-10"></div>
    ${settings.trackLateMinutes&&late?info(`${duration(late)} total late in this filter`):""}
    ${section("MARK THIS DATE")}${lateInputs}<div class="button-row"><button class="${attendanceButtonClass("CALLED_OUT")}" data-action="called-out">Called Out</button><button class="${attendanceButtonClass("TARDY")}" data-action="tardy">Tardy</button></div><div class="spacer-10"></div><button class="outline full" data-export="attendance">Export this view as PDF</button>`;
  app.innerHTML = shell("Tardy & Call-Outs","Record a tardy or call-out for today—or choose an earlier date.",body);
}
function tardyFilters() {
  const monthLabel = tardyFilter==="month" ? new Intl.DateTimeFormat("en-US",{month:"long"}).format(parseDate(`${selectedMonth}-01`)) : "Month";
  const yearLabel = tardyFilter==="year" ? String(selectedYear) : "Year";
  return filterButton("Last 30 days","days30",tardyFilter==="days30")+filterButton(`${monthLabel} ▾`,"month",tardyFilter==="month")+filterButton(`${yearLabel} ▾`,"year",tardyFilter==="year")+filterButton("All time","all",tardyFilter==="all");
}
function tardyRange() {
  const now=today();
  if (tardyFilter==="days30") return [addDays(now,-29),now];
  if (tardyFilter==="month") { const start=`${selectedMonth}-01`; const d=parseDate(start); d.setMonth(d.getMonth()+1,0); return [start, selectedMonth===now.slice(0,7)?now:isoDate(d)]; }
  if (tardyFilter==="year") return [`${selectedYear}-01-01`,selectedYear===Number(now.slice(0,4))?now:`${selectedYear}-12-31`];
  const dates=Object.keys(state.attendance).sort(); return [dates[0]||now,now];
}
function attendanceBetween(from,to) { return Object.values(state.attendance).filter(e=>e.date>=from&&e.date<=to).sort(byDateDesc); }
function attendanceButtonClass(target) {
  const colored=settings.attendanceButtonMode==="BOTH"||settings.attendanceButtonTarget===target;
  return colored ? `primary ${settings.attendanceButtonTeal?"":"coral"}`.trim() : "primary navy";
}

function renderHours() {
  const existing=state.hours[selectedHoursDate];
  const range=hoursRange(hoursFilter);
  if (!range) { view="settings"; settingsCategory="time"; notice="Set your current pay period start and end dates before using pay-period totals."; renderSettings(); return; }
  const entries=hoursBetween(...range);
  const total=entries.reduce((sum,e)=>sum+e.minutes,0);
  const input=settings.decimalHours?`<input id="hours-value" class="field" inputmode="decimal" maxlength="5" placeholder="Decimal hours (example: 8.5)" value="${existing?decimalHours(existing.minutes):""}"><p class="hint">Example: 8.5 = 8 hours 30 minutes</p>`:`<input id="hours-value" class="field" inputmode="text" maxlength="5" placeholder="Clock hours (example: 8:30)" value="${existing?clockHours(existing.minutes):""}"><p class="hint">Use H:MM clock form, from 0:01 through 24:00</p>`;
  const history=entries.length?entries.map(e=>historyRow(e,"hours")).join(""):`<div class="card empty">No hours saved in this range.</div>`;
  const body=`${section("DATE")}<button class="date-field" data-action="pick-hours-date">${escapeHtml(fullDate(selectedHoursDate))} &nbsp; ▾</button><input id="hours-date" type="date" value="${selectedHoursDate}" max="${today()}" hidden><div class="spacer-18"></div>
    ${section("HOURS WORKED")}${input}<button class="primary full" data-action="save-hours">${existing?"Update hours":"Save hours"}</button><div class="spacer-18"></div>
    ${section("TOTAL")}<div class="filter-scroll">${hoursFilters()}</div><div class="card stat"><span class="stat-value teal">${escapeHtml(formatHours(total))}</span><span class="stat-caption">${escapeHtml(hoursFilterLabel(hoursFilter))}</span></div><div class="spacer-18"></div>
    ${section("HISTORY")}${history}<div class="spacer-10"></div><button class="outline full" data-export="hours">Export this view as PDF</button>`;
  app.innerHTML=shell("Hours","Add your hours worked and keep a running total.",body);
}
function hoursFilters() { return [["Current Week","currentWeek"],["Last Week","lastWeek"],["Current Pay Period","currentPay"],["Last Pay Period","lastPay"]].map(([label,key])=>filterButton(label,key,hoursFilter===key)).join(""); }
function hoursFilterLabel(key) { return ({currentWeek:"Current Week",lastWeek:"Last Week",currentPay:"Current Pay Period",lastPay:"Last Pay Period"})[key]; }
function hoursRange(key) {
  const now=parseDate(today()); const sunday=new Date(now); sunday.setDate(now.getDate()-now.getDay()); const thisSunday=isoDate(sunday);
  if (key==="currentWeek") return [thisSunday,addDays(thisSunday,6)];
  if (key==="lastWeek") return [addDays(thisSunday,-7),addDays(thisSunday,-1)];
  if (!validPayPeriod()) return null;
  if (key==="currentPay") return [settings.payPeriodStart,settings.payPeriodEnd];
  const length=daysBetween(settings.payPeriodStart,settings.payPeriodEnd)+1;
  return [addDays(settings.payPeriodStart,-length),addDays(settings.payPeriodStart,-1)];
}
function validPayPeriod() { return settings.payPeriodStart&&settings.payPeriodEnd&&settings.payPeriodEnd>=settings.payPeriodStart; }
function hoursBetween(from,to) { return Object.values(state.hours).filter(e=>e.date>=from&&e.date<=to).sort(byDateDesc); }

function renderHistory() {
  const [from,to]=tardyRange();
  const entries=attendanceBetween(from,to).filter(e=>e.type===historyType);
  const rows=entries.length?entries.map(e=>historyRow(e,"attendance")).join(""):`<div class="card empty">No ${historyType===TARDY?"tardies":"call-outs"} in this range.</div>`;
  const body=`${section("VIEW")}<div class="setting-tabs"><button class="choice${historyType===TARDY?" active":""}" data-history-tab="${TARDY}">Tardies</button><button class="choice${historyType===CALLED_OUT?" active":""}" data-history-tab="${CALLED_OUT}">Call-Outs</button></div><div class="spacer-18"></div>
    ${section("FILTER")}<div class="filter-scroll">${tardyFilters()}</div>${section(historyType===TARDY?"TARDIES":"CALL-OUTS")}${rows}<div class="spacer-10"></div><button class="outline full" data-export="history">Export this history as PDF</button>`;
  app.innerHTML=shell("History","Review, edit, or delete your attendance records.",body,true);
}

function renderSettings() {
  const tabs=`<div class="setting-tabs"><button class="choice${settingsCategory==="general"?" active":""}" data-settings-tab="general">General</button><button class="choice${settingsCategory==="time"?" active":""}" data-settings-tab="time">Time</button><button class="choice${settingsCategory==="data"?" active":""}" data-settings-tab="data">Data</button></div><div class="spacer-18"></div>`;
  let content;
  if (settingsCategory==="general") content=`${section("APPEARANCE")}${toggle("Dark mode","Use a dark color scheme throughout the app.","darkMode",settings.darkMode)}<div class="spacer-18"></div>${section("ATTENDANCE BUTTONS")}${attendanceButtonSettings()}<div class="spacer-18"></div>${section("DEFAULT OPENING SCREEN")}<div class="card"><div class="card-title">Choose where Attendance opens</div><div class="spacer-10"></div><div class="button-row"><button class="choice${settings.defaultMode==="TARDY"?" active":""}" data-default="TARDY">Tardy</button><button class="choice${settings.defaultMode==="HOURS"?" active":""}" data-default="HOURS">Hours</button></div></div>`;
  else if (settingsCategory==="time") content=`${section("TARDY DETAILS")}${toggle("Track exact late time","Show hours and minutes when marking a tardy.","trackLateMinutes",settings.trackLateMinutes)}<div class="spacer-18"></div>${section("HOURS FORMAT")}${toggle("Use decimal hours","On: decimal form (8.5). Off: 12/24-hour clock form (8:30).","decimalHours",settings.decimalHours)}<div class="spacer-18"></div>${section("PAY PERIODS")}${payPeriodCard()}`;
  else content=`${section("BACKUP & TRANSFER")}<div class="card"><div class="card-title">Move all data between devices</div><div class="card-detail">Export one backup file containing tardies, call-outs, hours, and deletions. Android and web use the same backup format, and importing merges the newest records.</div><div class="spacer-10"></div><div class="button-row"><button class="outline" data-action="export-data">Export data</button><button class="outline" data-action="import-data">Import data</button></div></div><div class="spacer-18"></div>${section("ABOUT YOUR DATA")}${info("Your records stay private in this browser. Data leaves the device only when you export a backup or print a report.")}<div class="version">Attendance ${VERSION}</div>`;
  app.innerHTML=shell("Settings","Personalize Attendance and protect your records.",`${notice?info(notice):""}${tabs}${content}`); notice="";
}
function toggle(title,detail,key,checked) { return `<div class="card toggle-card"><div class="toggle-copy"><div class="toggle-title">${escapeHtml(title)}</div><div class="toggle-detail">${escapeHtml(detail)}</div></div><label class="switch"><input type="checkbox" data-setting="${key}" ${checked?"checked":""} aria-label="${escapeHtml(title)}"><span></span></label></div>`; }
function attendanceButtonSettings() {
  const target=settings.attendanceButtonTarget;
  const targetChoice=settings.attendanceButtonMode==="ONE"?`<div class="setting-label">Colored button</div><div class="button-row"><button class="choice${target==="CALLED_OUT"?" active":""}" data-attendance-target="CALLED_OUT">Called Out</button><button class="choice${target==="TARDY"?" active":""}" data-attendance-target="TARDY">Tardy</button></div><div class="spacer-10"></div>`:"";
  return `<div class="card"><div class="card-title">Choose which buttons stand out</div><div class="card-detail">Color both attendance buttons, or emphasize only one.</div><div class="spacer-10"></div><div class="setting-label">Button emphasis</div><div class="button-row"><button class="choice${settings.attendanceButtonMode==="BOTH"?" active":""}" data-attendance-mode="BOTH">Both</button><button class="choice${settings.attendanceButtonMode==="ONE"?" active":""}" data-attendance-mode="ONE">Only one</button></div><div class="spacer-10"></div>${targetChoice}<div class="inline-toggle"><div><div class="toggle-title">Use teal buttons</div><div class="toggle-detail">Off: red. On: teal.</div></div><label class="switch"><input type="checkbox" data-setting="attendanceButtonTeal" ${settings.attendanceButtonTeal?"checked":""} aria-label="Use teal buttons"><span></span></label></div></div>`;
}
function payPeriodCard() {
  let summary="";
  if (settings.payPeriodStart&&settings.payPeriodEnd) {
    if (!validPayPeriod()) summary=`<p class="hint" style="color:var(--coral);font-weight:700">The end date must be on or after the start date.</p>`;
    else { const last=hoursRange("lastPay"); summary=`<p class="hint">Current: ${escapeHtml(shortDate(settings.payPeriodStart))} – ${escapeHtml(shortDate(settings.payPeriodEnd))}<br>Last: ${escapeHtml(shortDate(last[0]))} – ${escapeHtml(shortDate(last[1]))}</p>`; }
  }
  return `<div class="card"><div class="card-title">Set your current pay period</div><div class="card-detail">Choose its first and last date. Attendance automatically calculates the previous pay period using the same number of days.</div><div class="spacer-10"></div><div class="button-row"><label style="flex:1"><span class="hint">Start date</span><input class="field" type="date" data-pay-date="payPeriodStart" value="${settings.payPeriodStart}"></label><label style="flex:1"><span class="hint">End date</span><input class="field" type="date" data-pay-date="payPeriodEnd" value="${settings.payPeriodEnd}"></label></div>${summary}</div>`;
}

function bindCommon() {
  document.querySelector('[data-action="menu"]')?.addEventListener("click",openDrawer);
  document.querySelector('[data-action="back"]')?.addEventListener("click",()=>{view="tardy";render();});
  document.querySelector('[data-action="close-menu"]')?.addEventListener("click",closeDrawer);
  document.querySelector('[data-action="default-view"]')?.addEventListener("click",()=>{view=settings.defaultMode==="HOURS"?"hours":"tardy";render();});
  document.querySelectorAll("[data-view]").forEach(el=>el.addEventListener("click",()=>{view=el.dataset.view;render();}));
  bindDates(); bindFilters(); bindActions();
}
function openDrawer(){document.querySelector(".drawer")?.classList.add("open");document.querySelector(".drawer-scrim")?.classList.add("open");}
function closeDrawer(){document.querySelector(".drawer")?.classList.remove("open");document.querySelector(".drawer-scrim")?.classList.remove("open");}
function bindDates() {
  const tardyPicker=document.querySelector("#tardy-date");
  document.querySelector('[data-action="pick-tardy-date"]')?.addEventListener("click",()=>tardyPicker.showPicker?tardyPicker.showPicker():tardyPicker.click());
  tardyPicker?.addEventListener("change",()=>{selectedTardyDate=tardyPicker.value;render();});
  document.querySelector('[data-action="today"]')?.addEventListener("click",()=>{selectedTardyDate=today();render();});
  const hoursPicker=document.querySelector("#hours-date");
  document.querySelector('[data-action="pick-hours-date"]')?.addEventListener("click",()=>hoursPicker.showPicker?hoursPicker.showPicker():hoursPicker.click());
  hoursPicker?.addEventListener("change",()=>{selectedHoursDate=hoursPicker.value;render();});
  document.querySelectorAll("[data-pay-date]").forEach(el=>el.addEventListener("change",()=>{settings[el.dataset.payDate]=el.value;saveSettings();render();}));
}
function bindFilters() {
  document.querySelectorAll("[data-filter]").forEach(el=>el.addEventListener("click",()=>{
    const filter=el.dataset.filter;
    if (["currentWeek","lastWeek","currentPay","lastPay"].includes(filter)) {
      if (["currentPay","lastPay"].includes(filter)&&!validPayPeriod()){view="settings";settingsCategory="time";notice="Set your current pay period start and end dates before using pay-period totals.";render();return;}
      hoursFilter=filter;render();return;
    }
    if(filter==="month") chooseMonth(); else if(filter==="year") chooseYear(); else {tardyFilter=filter;render();}
  }));
}
function chooseMonth() {
  const input=document.createElement("input"); input.type="month"; input.value=selectedMonth; input.max=today().slice(0,7); input.style.position="fixed"; input.style.opacity="0"; document.body.append(input);
  input.addEventListener("change",()=>{if(input.value){selectedMonth=input.value;selectedYear=Number(input.value.slice(0,4));tardyFilter="month";}input.remove();render();},{once:true});
  input.addEventListener("blur",()=>setTimeout(()=>input.remove(),1000),{once:true}); input.showPicker?input.showPicker():input.click();
}
function chooseYear() {
  const first=Math.min(2000,...Object.keys(state.attendance).map(d=>Number(d.slice(0,4))),Number(today().slice(0,4)));
  const years=[]; for(let y=Number(today().slice(0,4));y>=first;y--) years.push(`<option value="${y}" ${y===selectedYear?"selected":""}>${y}</option>`);
  showDialog("Choose year",`<select id="year-choice">${years.join("")}</select>`,"Use year",()=>{selectedYear=Number(document.querySelector("#year-choice").value);tardyFilter="year";render();});
}

function bindActions() {
  document.querySelector('[data-action="called-out"]')?.addEventListener("click",()=>showConfirm("Mark called out?",`Save a call-out for ${shortDate(selectedTardyDate)}?`,()=>saveAttendance(CALLED_OUT,0)));
  document.querySelector('[data-action="tardy"]')?.addEventListener("click",()=>{const mins=readLateMinutes();if(mins!==null)saveAttendance(TARDY,mins);});
  document.querySelector('[data-action="save-hours"]')?.addEventListener("click",saveHours);
  document.querySelectorAll("[data-history]").forEach(el=>el.addEventListener("click",()=>{historyType=el.dataset.history;view="history";render();}));
  document.querySelectorAll("[data-history-tab]").forEach(el=>el.addEventListener("click",()=>{historyType=el.dataset.historyTab;render();}));
  document.querySelectorAll("[data-edit-attendance]").forEach(el=>el.addEventListener("click",()=>editAttendance(el.dataset.editAttendance)));
  document.querySelectorAll("[data-delete-attendance]").forEach(el=>el.addEventListener("click",()=>deleteEntry("attendance",el.dataset.deleteAttendance)));
  document.querySelectorAll("[data-edit-hours]").forEach(el=>el.addEventListener("click",()=>{selectedHoursDate=el.dataset.editHours;renderHours();bindCommon();}));
  document.querySelectorAll("[data-delete-hours]").forEach(el=>el.addEventListener("click",()=>deleteEntry("hours",el.dataset.deleteHours)));
  document.querySelectorAll("[data-settings-tab]").forEach(el=>el.addEventListener("click",()=>{settingsCategory=el.dataset.settingsTab;render();}));
  document.querySelectorAll("[data-setting]").forEach(el=>el.addEventListener("change",()=>{settings[el.dataset.setting]=el.checked;saveSettings();render();}));
  document.querySelectorAll("[data-default]").forEach(el=>el.addEventListener("click",()=>{settings.defaultMode=el.dataset.default;saveSettings();render();}));
  document.querySelectorAll("[data-attendance-mode]").forEach(el=>el.addEventListener("click",()=>{settings.attendanceButtonMode=el.dataset.attendanceMode;saveSettings();render();}));
  document.querySelectorAll("[data-attendance-target]").forEach(el=>el.addEventListener("click",()=>{settings.attendanceButtonTarget=el.dataset.attendanceTarget;saveSettings();render();}));
  document.querySelector('[data-action="export-data"]')?.addEventListener("click",exportData);
  document.querySelector('[data-action="import-data"]')?.addEventListener("click",()=>fileInput.click());
  document.querySelectorAll("[data-export]").forEach(el=>el.addEventListener("click",()=>printReport(el.dataset.export)));
}
function readLateMinutes() {
  if(!settings.trackLateMinutes)return 0;
  const h=Number(document.querySelector("#late-hours").value||0), m=Number(document.querySelector("#late-minutes").value||0);
  if(!Number.isInteger(h)||!Number.isInteger(m)||h<0||m<0||m>59){toast("Minutes must be between 0 and 59");return null;} return h*60+m;
}
function saveAttendance(type,lateMinutes) { const modifiedAt=Date.now(); state.attendance[selectedTardyDate]={date:selectedTardyDate,type,lateMinutes:type===TARDY?lateMinutes:0,modifiedAt}; delete state.deleted[`attendance:${selectedTardyDate}`]; saveData(); toast(type===TARDY?`Tardy saved for ${shortDate(selectedTardyDate)}`:"Call-out saved");render(); }
function saveHours() {
  const value=document.querySelector("#hours-value").value.trim(); let minutes;
  if(settings.decimalHours){const hours=Number(value);if(!value||!Number.isFinite(hours)||hours<0){toast("Enter valid decimal hours, such as 8.5");return;}minutes=Math.round(hours*60);}
  else {const match=value.match(/^(\d{1,2}):(\d{2})$/);if(!match){toast("Enter clock hours as H:MM, such as 8:30");return;}const h=Number(match[1]),m=Number(match[2]);if(h>24||m>59||(h===24&&m!==0)){toast("Enter a time from 0:01 through 24:00");return;}minutes=h*60+m;}
  if(minutes<=0){toast("Enter the time you worked");return;} if(minutes>1440){toast("Hours for one date cannot exceed 24");return;}
  state.hours[selectedHoursDate]={date:selectedHoursDate,minutes,modifiedAt:Date.now()};delete state.deleted[`hours:${selectedHoursDate}`];saveData();toast(`Hours saved for ${shortDate(selectedHoursDate)}`);render();
}
function editAttendance(date) {
  const entry=state.attendance[date];
  const lateFields=settings.trackLateMinutes?`<div class="button-row"><input id="edit-late-hours" class="field" inputmode="numeric" placeholder="Hours late" value="${Math.floor(entry.lateMinutes/60)}"><input id="edit-late-minutes" class="field" inputmode="numeric" placeholder="Minutes late" value="${entry.lateMinutes%60}"></div>`:"";
  showDialog("Edit attendance entry",`<p>${escapeHtml(shortDate(date))}</p><select id="edit-type"><option value="${CALLED_OUT}" ${entry.type===CALLED_OUT?"selected":""}>Called Out</option><option value="${TARDY}" ${entry.type===TARDY?"selected":""}>Tardy</option></select><div class="spacer-10"></div>${lateFields}`,"Save changes",()=>{
    const type=document.querySelector("#edit-type").value;let minutes=entry.lateMinutes;
    if(settings.trackLateMinutes&&type===TARDY){const h=Number(document.querySelector("#edit-late-hours").value||0),m=Number(document.querySelector("#edit-late-minutes").value||0);if(m<0||m>59||h<0){toast("Minutes must be between 0 and 59");return false;}minutes=h*60+m;}
    state.attendance[date]={date,type,lateMinutes:type===TARDY?minutes:0,modifiedAt:Date.now()};saveData();toast("Attendance entry updated");render();
  });
}
function deleteEntry(table,date) { showConfirm("Delete this entry?","This cannot be undone.",()=>{delete state[table][date];state.deleted[`${table}:${date}`]={table,date,deletedAt:Date.now()};saveData();toast("Entry deleted");render();}); }

function backupJson() {
  return {format:"attendance-backup",version:2,exportedAt:Date.now(),attendance:Object.values(state.attendance).sort(byDateDesc),hours:Object.values(state.hours).sort(byDateDesc),deleted:Object.values(state.deleted)};
}
function exportData() { download(`Attendance-backup-${today()}.json`,JSON.stringify(backupJson(),null,2),"application/json");toast("Backup exported"); }
function download(filename,text,type) { const blob=new Blob([text],{type});const url=URL.createObjectURL(blob);const a=document.createElement("a");a.href=url;a.download=filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000); }
fileInput.addEventListener("change",async()=>{const file=fileInput.files[0];fileInput.value="";if(!file)return;try{const incoming=JSON.parse(await file.text());if(incoming.format!=="attendance-backup")throw new Error();let checked=0;for(const entry of incoming.attendance||[]){mergeEntry("attendance",entry);checked++;}for(const entry of incoming.hours||[]){mergeEntry("hours",entry);checked++;}for(const tomb of incoming.deleted||[])mergeTombstone(tomb);saveData();toast(`Backup imported • ${checked} records checked`);render();}catch{toast("That file is not a valid Attendance backup");}});
function latestTimestamp(table,date){return Math.max(state[table][date]?.modifiedAt||0,state.deleted[`${table}:${date}`]?.deletedAt||0);}
function mergeEntry(table,entry){if(!entry.date||(entry.modifiedAt||1)<=latestTimestamp(table,entry.date))return;state[table][entry.date]=table==="attendance"?{date:entry.date,type:entry.type,lateMinutes:entry.lateMinutes||0,modifiedAt:entry.modifiedAt||1}:{date:entry.date,minutes:entry.minutes||0,modifiedAt:entry.modifiedAt||1};delete state.deleted[`${table}:${entry.date}`];}
function mergeTombstone(tomb){if(!["attendance","hours"].includes(tomb.table)||!tomb.date||tomb.deletedAt<=latestTimestamp(tomb.table,tomb.date))return;delete state[tomb.table][tomb.date];state.deleted[`${tomb.table}:${tomb.date}`]={table:tomb.table,date:tomb.date,deletedAt:tomb.deletedAt};}

function printReport(kind) {
  let title,filter,range,entries,headers,rows,summary;
  if(kind==="hours"){range=hoursRange(hoursFilter);entries=hoursBetween(...range);title="Hours worked report";filter=hoursFilterLabel(hoursFilter);const total=entries.reduce((s,e)=>s+e.minutes,0);summary=`${formatHours(total)} total`;headers=["Date","Hours worked"];rows=entries.map(e=>[reportDate(e.date),formatHours(e.minutes)]);}
  else {range=tardyRange();entries=attendanceBetween(...range);if(kind==="history")entries=entries.filter(e=>e.type===historyType);title="Tardy & call-out report";filter=(kind==="history"?(historyType===TARDY?"Tardies • ":"Call-Outs • "):"")+tardyFilterLabel();const tardies=entries.filter(e=>e.type===TARDY),calls=entries.filter(e=>e.type===CALLED_OUT),late=tardies.reduce((s,e)=>s+e.lateMinutes,0);summary=`${tardies.length} tardies • ${calls.length} call-outs${late?` • ${duration(late)} total late`:""}`;headers=["Date","Status","Late by"];rows=entries.map(e=>[reportDate(e.date),e.type,e.type===TARDY&&e.lateMinutes?`${duration(e.lateMinutes)} late`:"—"]);}
  document.querySelector(".print-report")?.remove();
  const report=document.createElement("section");
  report.className="print-report";
  report.innerHTML=`<header class="print-header"><h1>${escapeHtml(title)}</h1><div class="print-meta">${escapeHtml(filter)} • ${escapeHtml(reportDate(range[0]))} – ${escapeHtml(reportDate(range[1]))}</div><div class="print-summary">${escapeHtml(summary)}</div></header><table class="print-table"><thead><tr>${headers.map(h=>`<th>${escapeHtml(h)}</th>`).join("")}</tr></thead><tbody>${rows.length?rows.map(row=>`<tr>${row.map(v=>`<td>${escapeHtml(v)}</td>`).join("")}</tr>`).join(""):`<tr><td class="print-empty" colspan="${headers.length}">No records in this date range.</td></tr>`}</tbody></table><footer class="print-footer">Created by Attendance on ${escapeHtml(reportDate(today()))}</footer>`;
  document.body.append(report);
  document.body.classList.add("printing-report");
  window.addEventListener("afterprint",()=>{report.remove();document.body.classList.remove("printing-report");},{once:true});
  window.print();
}
function tardyFilterLabel(){if(tardyFilter==="days30")return "Last 30 days";if(tardyFilter==="month")return new Intl.DateTimeFormat("en-US",{month:"long"}).format(parseDate(`${selectedMonth}-01`));if(tardyFilter==="year")return String(selectedYear);return "All time";}

function showDialog(title,body,confirmLabel,onConfirm) {
  const dialog=document.createElement("dialog");dialog.innerHTML=`<form method="dialog" class="dialog-body"><h2>${escapeHtml(title)}</h2>${body}<div class="dialog-actions"><button class="outline" value="cancel">Cancel</button><button class="primary" id="dialog-confirm" value="confirm">${escapeHtml(confirmLabel)}</button></div></form>`;document.body.append(dialog);dialog.addEventListener("close",()=>dialog.remove());dialog.querySelector("#dialog-confirm").addEventListener("click",event=>{event.preventDefault();const result=onConfirm();if(result!==false)dialog.close();});dialog.showModal();
}
function showConfirm(title,message,onConfirm){showDialog(title,`<p>${escapeHtml(message)}</p>`,"Confirm",onConfirm);}
function toast(message){const el=document.querySelector("#toast");el.textContent=message;el.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),2600);}

applyTheme();
render();
if("serviceWorker" in navigator) window.addEventListener("load",()=>navigator.serviceWorker.register("sw.js").catch(()=>{}));
