(() => {
  'use strict';

  const app = document.getElementById('safa-app');
  if (!app) return;

  const csrf = document.querySelector('meta[name="csrf-token"]')?.content || '';
  const urls = {
    workspace: app.dataset.workspaceUrl || '', accountSwitch: app.dataset.accountSwitchUrl || '',
    customers: app.dataset.customersUrl || '', suppliers: app.dataset.suppliersUrl || '', expenses: app.dataset.expensesUrl || '',
    customerSale: app.dataset.customerSaleUrl || '', customerAdjustment: app.dataset.customerAdjustmentUrl || '',
    transactions: app.dataset.mobileTransactionsUrl || '', supplierFunds: app.dataset.supplierFundsUrl || '',
    walletLedgers: app.dataset.walletLedgersActionUrl || '', walletDeposit: app.dataset.walletDepositUrl || '', walletWithdraw: app.dataset.walletWithdrawUrl || '',
    profileSettings: app.dataset.profileSettingsUrl || '', personalSettings: app.dataset.personalSettingsUrl || '', pinSettings: app.dataset.pinSettingsUrl || '',
    config: app.dataset.configUrl || '', logo: app.dataset.logoUrl || '', users: app.dataset.usersUrl || ''
  };
  const ability = {
    addTx: app.dataset.canAddTransactions === '1', editTx: app.dataset.canEditTransactions === '1', deleteTx: app.dataset.canDeleteTransactions === '1',
    wallet: app.dataset.canManageWallet === '1', users: app.dataset.canManageUsers === '1', system: app.dataset.canManageSystem === '1', superadmin: app.dataset.isSuperadmin === '1'
  };
  const bn = app.dataset.language === 'bn';
  const text = (en, bengali) => bn ? bengali : en;
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const esc = (value) => String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  const num = value => Number.parseFloat(value ?? 0) || 0;
  const money = (value, digits = 2) => num(value).toLocaleString(undefined, {minimumFractionDigits: digits, maximumFractionDigits: digits});
  const dateText = value => new Date((Number(value) || 0) * (Number(value) > 2_000_000_000 ? 1 : 1000)).toLocaleDateString(undefined, {year:'numeric', month:'short', day:'numeric'});
  const timeText = value => new Date((Number(value) || 0) * (Number(value) > 2_000_000_000 ? 1 : 1000)).toLocaleTimeString(undefined, {hour:'2-digit', minute:'2-digit'});
  const baseWithId = (base, id) => `${String(base).replace(/\/$/, '')}/${encodeURIComponent(id)}`;
  const foreign = () => state.settings.foreign_currency || 'SAR';
  const local = () => state.settings.local_currency || 'BDT';
  const statusOf = tx => tx.type || tx.status || 'Pending';
  const todayInput = () => new Date().toISOString().slice(0, 10);
  const selectedUnix = date => Math.floor(new Date(`${date || todayInput()}T12:00:00`).getTime() / 1000);

  const state = {
    activeScreen: 'dashboard', previousMain: 'dashboard', workspaceLoaded: false,
    user: {}, permissions: {}, settings: {}, customers: [], suppliers: [], transactions: [], supplier_deposits: [], wallet_ledgers: [], wallet_batches: [], expenses: [], users: [],
    profile: null, flow: null, expanded: new Set(), walletExpanded: new Set()
  };

  async function api(url, options = {}) {
    if (!url) throw new Error(text('This action is not available for your role.', 'আপনার রোলে এই কাজটি অনুমোদিত নয়।'));
    const headers = {'Accept':'application/json', 'X-CSRF-TOKEN': csrf, ...(options.headers || {})};
    let body = options.body;
    if (body && !(body instanceof FormData) && typeof body !== 'string') {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }
    const response = await fetch(url, {credentials:'same-origin', ...options, headers, body});
    let data = {};
    try { data = await response.json(); } catch (_) { data = {}; }
    if (!response.ok) {
      const errors = data.errors ? Object.values(data.errors).flat().join(' ') : '';
      throw new Error(errors || data.message || `${response.status} ${response.statusText}`);
    }
    return data;
  }

  function toast(message, error = false) {
    const region = $('#toast-region'); if (!region) return;
    const item = document.createElement('div'); item.className = `toast${error ? ' error' : ''}`; item.textContent = message;
    region.appendChild(item); setTimeout(() => item.remove(), 3600);
  }

  function setTheme(value) {
    const theme = ['light','dark'].includes(value) ? value : 'system';
    localStorage.setItem('safa-web-theme', theme);
    if (theme === 'system') document.documentElement.removeAttribute('data-theme'); else document.documentElement.dataset.theme = theme;
    const select = $('#appearance-select'); if (select) select.value = theme;
  }
  setTheme(localStorage.getItem('safa-web-theme') || 'system');

  function setScreen(name) {
    if (name !== 'settings') state.previousMain = name;
    state.activeScreen = name;
    $$('.screen').forEach(el => el.classList.toggle('active', el.dataset.screen === name));
    $$('.bottom-nav-item').forEach(el => el.classList.toggle('active', el.dataset.nav === name));
    window.scrollTo({top:0, behavior:'smooth'});
    if (name === 'settings' && ability.users) loadUsers();
  }

  function openModal(html) { $('#modal-card').innerHTML = html; $('#modal').classList.remove('hidden'); }
  function closeModal() { $('#modal').classList.add('hidden'); $('#modal-card').innerHTML = ''; }
  function openSubpage(html) { $('#subpage-content').innerHTML = html; $('#subpage').classList.remove('hidden'); document.body.style.overflow = 'hidden'; }
  function closeSubpage() { $('#subpage').classList.add('hidden'); $('#subpage-content').innerHTML = ''; document.body.style.overflow = ''; state.profile = null; state.flow = null; }

  async function loadWorkspace(quiet = false) {
    if (!app.dataset.activeAccount) return;
    try {
      const data = await api(urls.workspace);
      Object.assign(state, {
        workspaceLoaded: true, user: data.user || {}, permissions: data.permissions || {}, settings: data.settings || {},
        customers: data.customers || [], suppliers: data.suppliers || [], transactions: data.transactions || [], supplier_deposits: data.supplier_deposits || [],
        wallet_ledgers: data.wallet_ledgers || [], wallet_batches: data.wallet_batches || [], expenses: data.expenses || []
      });
      $('.brand-name') && $$('.brand-name').forEach(el => el.textContent = state.settings.app_name || 'SAFA');
      if ($('#signed-user-name')) $('#signed-user-name').textContent = state.user.name || '';
      renderAll();
      if (state.profile?.type === 'customer') renderCustomerProfile(state.profile.id, state.profile.tab);
      if (state.profile?.type === 'supplier') renderSupplierProfile(state.profile.id, state.profile.tab);
      if (!quiet) toast(text('Data refreshed.', 'ডেটা রিফ্রেশ হয়েছে।'));
    } catch (error) { toast(error.message, true); }
  }

  function customerTransactions(id) { return state.transactions.filter(tx => Number(tx.customer_id) === Number(id)); }
  function supplierDeposits(id) { return state.supplier_deposits.filter(dep => Number(dep.supplier_id) === Number(id)); }
  function customerBalance(id) { return customerTransactions(id).reduce((sum, tx) => sum + num(tx.amount_sar) - num(tx.sar_collected), 0); }
  function supplierBalance(id) {
    const deps = supplierDeposits(id); const acquired = deps.filter(d => ['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((s,d)=>s+num(d.amount_bdt),0);
    const paid = deps.reduce((s,d)=>s+num(d.paid_bdt),0); return acquired - paid;
  }
  function walletBatches(ledgerId) { return state.wallet_batches.filter(b => Number(b.ledger_id) === Number(ledgerId)); }
  function walletBalance(ledgerId) { return walletBatches(ledgerId).reduce((s,b)=>s+num(b.remaining_bdt),0); }
  function activeBatches() { return state.wallet_batches.filter(b => num(b.remaining_bdt) > .05).sort((a,b)=>(Number(a.timestamp)-Number(b.timestamp)) || (Number(a.id)-Number(b.id))); }
  function groupedByDate(items) {
    const groups = new Map();
    [...items].sort((a,b)=>(Number(b.timestamp)-Number(a.timestamp)) || (Number(b.id)-Number(a.id))).forEach(item => {
      const key = dateText(item.timestamp); if (!groups.has(key)) groups.set(key, []); groups.get(key).push(item);
    });
    return groups;
  }

  function renderAll() { renderDashboard(); renderCustomers(); renderSuppliers(); renderWallet(); renderExpenses(); }

  function renderDashboard() {
    const stats = $('#dashboard-stats'); if (!stats) return;
    const customerDue = state.customers.reduce((s,c)=>s+Math.max(customerBalance(c.id),0),0);
    const supplierNet = state.suppliers.reduce((s,x)=>s+supplierBalance(x.id),0);
    const wallet = state.wallet_batches.reduce((s,b)=>s+num(b.remaining_bdt),0);
    const delivered = state.transactions.filter(tx=>statusOf(tx)==='Delivered').length;
    stats.innerHTML = [
      [text('Customers','কাস্টমার'), state.customers.length, ''], [text('Suppliers','সাপ্লায়ার'), state.suppliers.length, ''],
      [text('Customer due','কাস্টমার বকেয়া'), `${money(customerDue)} ${foreign()}`, customerDue>.05?'danger':''],
      [text('Wallet stock','ওয়ালেট স্টক'), `${money(wallet)} ${local()}`, '']
    ].map(([label,value,klass])=>`<article class="metric-card ${klass}"><small>${esc(label)}</small><strong>${esc(value)}</strong></article>`).join('');
    const overview = $('#dashboard-overview'); if (!overview) return;
    overview.innerHTML = `
      <div class="overview-item"><small>${text('Transactions','লেনদেন')}</small><strong>${state.transactions.length}</strong></div>
      <div class="overview-item"><small>${text('Delivered','ডেলিভার্ড')}</small><strong>${delivered}</strong></div>
      <div class="overview-item"><small>${text('Supplier ledger net','সাপ্লায়ার নেট')}</small><strong>${money(Math.abs(supplierNet))} ${local()}</strong></div>`;
  }

  function customerCard(c) {
    const txs = customerTransactions(c.id), due = customerBalance(c.id), spent = txs.reduce((s,t)=>s+num(t.amount_sar),0), bdt = txs.reduce((s,t)=>s+num(t.amount_bdt),0);
    const balanceClass = due>.05?'due':due<-.05?'advance':'';
    const label = due>.05?text('Total Due','মোট বকেয়া'):due<-.05?text('Customer Owed','কাস্টমার পাবে'):text('Settled','হিসাব সমান');
    return `<article class="entity-card clickable" data-open-customer="${c.id}"><div class="entity-head"><div class="entity-identity"><span class="avatar">${esc((c.name||'?').slice(0,1).toUpperCase())}</span><div><strong>${esc(c.name)}</strong><small>${esc(c.phone || '')}</small></div></div><div class="balance-box ${balanceClass}"><small>${label}</small><strong>${money(Math.abs(due))} ${foreign()}</strong></div></div><div class="entity-stats"><div><small>${text('Total volume','মোট লেনদেন')}</small><strong>${money(spent)} ${foreign()}</strong></div><div><small>${text('Paid out','মোট পাঠানো')}</small><strong>${money(bdt)} ${local()}</strong></div><div><small>${text('Tx count','লেনদেন সংখ্যা')}</small><strong>${txs.length}</strong></div></div></article>`;
  }
  function renderCustomers() {
    const list = $('#customers-list'); if (!list) return;
    const q = ($('#customer-search')?.value || '').trim().toLowerCase(), filter = $('#customer-filter')?.value || 'all', sort = $('#customer-sort')?.value || 'newest';
    let rows = state.customers.filter(c => !q || [c.name,c.phone,c.address].some(v=>String(v||'').toLowerCase().includes(q)));
    if (filter==='due') rows=rows.filter(c=>customerBalance(c.id)>.05); if(filter==='advance') rows=rows.filter(c=>customerBalance(c.id)<-.05);
    rows.sort((a,b)=>sort==='oldest'?Number(a.timestamp)-Number(b.timestamp):sort==='name'?String(a.name).localeCompare(String(b.name)):sort==='due'?customerBalance(b.id)-customerBalance(a.id):Number(b.timestamp)-Number(a.timestamp));
    list.innerHTML = rows.length ? rows.map(customerCard).join('') : `<div class="empty-state"><strong>${text('No customers found','কোনো কাস্টমার পাওয়া যায়নি')}</strong>${text('Add a customer or change the current filter.','নতুন কাস্টমার যোগ করুন বা ফিল্টার পরিবর্তন করুন।')}</div>`;
  }

  function supplierCard(s) {
    const deps=supplierDeposits(s.id), balance=supplierBalance(s.id), sar=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_sar),0), acquired=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_bdt),0);
    const klass=balance>.05?'advance':balance<-.05?'due':''; const label=balance>.05?text('Receivable','পাওনা'):balance<-.05?text('Payable','বকেয়া'):text('No Due','বকেয়া নেই');
    return `<article class="entity-card clickable" data-open-supplier="${s.id}"><div class="entity-head"><div class="entity-identity"><span class="avatar">${esc((s.name||'?').slice(0,1).toUpperCase())}</span><div><strong>${esc(s.name)}</strong><small>${esc(s.phone||'')}</small></div></div><div class="balance-box ${klass}"><small>${label}</small><strong>${money(Math.abs(balance))} ${local()}</strong></div></div><div class="entity-stats"><div><small>${text('Riyal deposited','রিয়াল জমা')}</small><strong>${money(sar)} ${foreign()}</strong></div><div><small>${text('Taka buy','টাকা ক্রয়')}</small><strong>${money(acquired)} ${local()}</strong></div><div><small>${text('Records','রেকর্ড')}</small><strong>${deps.length}</strong></div></div></article>`;
  }
  function renderSuppliers() {
    const list=$('#suppliers-list'); if(!list)return; const q=($('#supplier-search')?.value||'').trim().toLowerCase(), filter=$('#supplier-filter')?.value||'all', sort=$('#supplier-sort')?.value||'newest';
    let rows=state.suppliers.filter(s=>!q||[s.name,s.phone,s.address].some(v=>String(v||'').toLowerCase().includes(q)));
    if(filter==='receivable')rows=rows.filter(s=>supplierBalance(s.id)>.05);if(filter==='payable')rows=rows.filter(s=>supplierBalance(s.id)<-.05);
    rows.sort((a,b)=>sort==='oldest'?Number(a.timestamp)-Number(b.timestamp):sort==='name'?String(a.name).localeCompare(String(b.name)):sort==='balance'?Math.abs(supplierBalance(b.id))-Math.abs(supplierBalance(a.id)):Number(b.timestamp)-Number(a.timestamp));
    list.innerHTML=rows.length?rows.map(supplierCard).join(''):`<div class="empty-state"><strong>${text('No suppliers found','কোনো সাপ্লায়ার পাওয়া যায়নি')}</strong></div>`;
  }

  function renderWallet() {
    const list=$('#wallet-ledgers-list'), summary=$('#wallet-summary'); if(!list||!summary)return;
    const total=state.wallet_batches.reduce((s,b)=>s+num(b.remaining_bdt),0), initial=state.wallet_batches.reduce((s,b)=>s+num(b.initial_bdt),0), active=state.wallet_batches.filter(b=>num(b.remaining_bdt)>.05).length;
    summary.innerHTML=`<article class="metric-card"><small>${text('Total stock','মোট স্টক')}</small><strong>${money(total)} ${local()}</strong></article><article class="metric-card"><small>${text('Total deposited','মোট জমা')}</small><strong>${money(initial)} ${local()}</strong></article><article class="metric-card"><small>${text('Active batches','সক্রিয় ব্যাচ')}</small><strong>${active}</strong></article>`;
    list.innerHTML=state.wallet_ledgers.length?state.wallet_ledgers.map(ledger=>{
      const batches=walletBatches(ledger.id), balance=walletBalance(ledger.id), init=batches.reduce((s,b)=>s+num(b.initial_bdt),0), spent=init-balance, activeB=batches.filter(b=>num(b.remaining_bdt)>.05), weighted=activeB.reduce((s,b)=>s+num(b.remaining_bdt)*num(b.rate),0)/(balance||1), expanded=state.walletExpanded.has(Number(ledger.id));
      return `<article class="entity-card"><div class="entity-head"><div class="entity-identity"><span class="avatar">▰</span><div><strong>${esc(ledger.name)}</strong><small>${activeB.length} ${text('active subaccounts','সক্রিয় সাব-অ্যাকাউন্ট')}</small></div></div><div class="balance-box"><small>${text('Available','ব্যালেন্স')}</small><strong>${money(balance)} ${local()}</strong></div></div><div class="entity-stats"><div><small>${text('Initial','প্রাথমিক')}</small><strong>${money(init)}</strong></div><div><small>${text('Spent','ব্যবহৃত')}</small><strong>${money(spent)}</strong></div><div><small>${text('Weighted rate','গড় রেট')}</small><strong>${money(weighted,4)}</strong></div></div><div class="entity-actions"><button class="mini-button" data-toggle-wallet="${ledger.id}">${expanded?text('Hide stock','স্টক লুকান'):text('Show stock','স্টক দেখুন')}</button><button class="mini-button primary" data-wallet-deposit="${ledger.id}">＋ ${text('Deposit','জমা')}</button><button class="mini-button blue" data-wallet-withdraw="${ledger.id}">− ${text('Withdraw','উত্তোলন')}</button><button class="mini-button" data-wallet-rename="${ledger.id}">${text('Rename','নাম')}</button><button class="mini-button danger" data-wallet-delete="${ledger.id}" ${balance>.05?'disabled':''}>${text('Delete','ডিলিট')}</button></div>${expanded?`<div class="section-label"><h3>${text('Stock batches','স্টক ব্যাচ')}</h3></div>${activeB.length?activeB.map(b=>`<div class="ledger-row"><div class="ledger-summary"><div class="ledger-main"><strong>${money(b.remaining_bdt)} ${local()}</strong><small>${dateText(b.timestamp)} · ${esc(b.notes||'')}</small></div><div class="ledger-amount"><strong>${text('Rate','রেট')} ${money(b.rate,4)}</strong><small>${text('Initial','প্রাথমিক')} ${money(b.initial_bdt)}</small></div></div></div>`).join(''):`<div class="empty-state">${text('No active stock','সক্রিয় স্টক নেই')}</div>`}`:''}</article>`;
    }).join(''):`<div class="empty-state"><strong>${text('No wallet ledgers','কোনো ওয়ালেট লেজার নেই')}</strong></div>`;
  }

  function renderExpenses() {
    const list=$('#expenses-list'), summary=$('#expenses-summary'); if(!list||!summary)return;
    const expense=state.expenses.filter(x=>Boolean(Number(x.is_expense))).reduce((s,x)=>s+num(x.amount),0), income=state.expenses.filter(x=>!Boolean(Number(x.is_expense))).reduce((s,x)=>s+num(x.amount),0);
    summary.innerHTML=`<article class="metric-card danger"><small>${text('Expense','ব্যয়')}</small><strong>${money(expense)}</strong></article><article class="metric-card"><small>${text('Income','আয়')}</small><strong>${money(income)}</strong></article><article class="metric-card"><small>${text('Net','নেট')}</small><strong>${money(income-expense)}</strong></article>`;
    list.innerHTML=state.expenses.length?state.expenses.map(x=>`<article class="entity-card"><div class="entity-head"><div class="entity-identity"><span class="avatar">${Number(x.is_expense)?'−':'+'}</span><div><strong>${esc(x.title)}</strong><small>${esc(x.category||'General')} · ${dateText(x.timestamp)}</small></div></div><div class="balance-box ${Number(x.is_expense)?'due':'advance'}"><small>${Number(x.is_expense)?text('Expense','ব্যয়'):text('Income','আয়')}</small><strong>${money(x.amount)} ${esc(x.currency||local())}</strong></div></div><div class="entity-actions"><button class="mini-button" data-expense-edit="${x.id}">${text('Edit','এডিট')}</button><button class="mini-button danger" data-expense-delete="${x.id}">${text('Delete','ডিলিট')}</button></div></article>`).join(''):`<div class="empty-state"><strong>${text('No daily entries','কোনো দৈনিক এন্ট্রি নেই')}</strong></div>`;
  }

  function transactionRow(tx, customerDue) {
    const status=statusOf(tx), due=num(tx.amount_sar)-num(tx.sar_collected), expanded=state.expanded.has(`tx-${tx.id}`), dueOnly=num(tx.amount_sar)<=.05 && num(tx.sar_collected)>.05, advance=num(tx.amount_sar)<=.05 && num(tx.sar_collected)<-.05;
    const title=advance?text('Advance Return','পাওনা ফেরত'):dueOnly?text('Due Collection','বকেয়া আদায়'):(tx.receiver_name||text('Recipient','প্রাপক'));
    const amount=advance?`${money(Math.abs(num(tx.sar_collected)))} ${foreign()}`:dueOnly?`${money(tx.sar_collected)} ${foreign()}`:`${money(tx.amount_sar)} ${foreign()}`;
    return `<div class="ledger-row"><div class="ledger-summary" data-expand="tx-${tx.id}"><div class="ledger-main"><strong>${esc(title)} <span class="status-pill ${status.toLowerCase()}">${esc(status)}</span></strong><small>${timeText(tx.timestamp)} · ${esc(tx.receiver_account_type||'')}</small>${Math.abs(due)>.05&&!dueOnly&&!advance?`<small>${due>0?text('Uncollected','রিয়াল বাকি'):text('Overpaid','কাস্টমার পাবে')}: ${money(Math.abs(due))} ${foreign()}</small>`:''}</div><div class="ledger-amount"><strong>${amount}</strong><small>${!dueOnly&&!advance?`${text('Rate','রেট')} ${money(tx.customer_rate,4)}`:''}</small></div></div>${expanded?`<div class="ledger-detail"><div class="detail-line"><span>${text('Wallet / Pool','ওয়ালেট')}</span><span>${esc(walletNameForTx(tx))}</span></div><div class="detail-line"><span>${text('Payout','প্রাপক পাবে')}</span><span>${money(tx.amount_bdt)} ${local()}</span></div><div class="detail-line"><span>${text('Collected','রিয়াল গ্রহণ')}</span><span>${money(tx.sar_collected)} / ${money(tx.amount_sar)} ${foreign()}</span></div><div class="detail-line"><span>${text('Account','হিসাব নম্বর')}</span><span>${esc(tx.receiver_account_no||'')}</span></div>${tx.notes?`<div class="detail-line"><span>${text('Notes','মন্তব্য')}</span><span>${esc(tx.notes)}</span></div>`:''}<div class="row-buttons"><button class="mini-button blue" data-share-transaction="${tx.id}">${text('Share','শেয়ার')}</button>${ability.editTx?`<button class="mini-button" data-edit-transaction="${tx.id}">${text('Edit','এডিট')}</button>`:''}${ability.deleteTx?`<button class="mini-button danger" data-delete-transaction="${tx.id}">${text('Delete','ডিলিট')}</button>`:''}${ability.editTx&&status!=='Delivered'&&status!=='Cancelled'?`<button class="mini-button primary" data-status-transaction="${tx.id}" data-status="Delivered">${text('Deliver','ডেলিভারি')}</button>`:''}${ability.editTx&&status==='Pending'?`<button class="mini-button danger" data-status-transaction="${tx.id}" data-status="Cancelled">${text('Cancel','বাতিল')}</button>`:''}${ability.editTx&&status==='Cancelled'?`<button class="mini-button" data-status-transaction="${tx.id}" data-status="Pending">${text('Pending','পেন্ডিং')}</button>`:''}</div></div>`:''}</div>`;
  }
  function walletNameForTx(tx) { const batch=state.wallet_batches.find(b=>Number(b.id)===Number(tx.wallet_batch_id)); const ledger=batch&&state.wallet_ledgers.find(l=>Number(l.id)===Number(batch.ledger_id)); return num(tx.amount_sar)<=.05?text('N/A (due/advance)','প্রযোজ্য নয়'):ledger?.name||text('Unknown wallet','অজানা ওয়ালেট'); }

  function renderCustomerProfile(id, tab='transactions') {
    const c=state.customers.find(x=>Number(x.id)===Number(id)); if(!c){closeSubpage();return;} state.profile={type:'customer',id:Number(id),tab};
    const txs=customerTransactions(id), due=customerBalance(id), totalSar=txs.reduce((s,x)=>s+num(x.amount_sar),0), totalBdt=txs.reduce((s,x)=>s+num(x.amount_bdt),0), groups=groupedByDate(txs);
    const groupsHtml=[...groups.entries()].map(([date,items])=>`<div class="date-group"><div class="date-heading"><span>${esc(date)}</span><span>${money(items.reduce((s,x)=>s+num(x.amount_bdt),0))} ${local()}</span></div>${items.map(x=>transactionRow(x,due)).join('')}</div>`).join('');
    openSubpage(`<div class="subpage-toolbar"><button class="icon-button" data-close-subpage>←</button><div><p class="eyebrow">${text('Customer Profile','কাস্টমার প্রোফাইল')}</p><h2>${esc(c.name)}</h2></div></div><div class="profile-hero"><div class="profile-person"><span class="avatar">${esc((c.name||'?')[0])}</span><div><h2>${esc(c.name)}</h2><p>${esc(c.phone||'')} · ${esc(c.address||'')}</p></div></div><div class="profile-balance"><small>${due>.05?text('Total Due','মোট বকেয়া'):due<-.05?text('Customer Owed','কাস্টমার পাবে'):text('Settled','সমান')}</small><strong>${money(Math.abs(due))} ${foreign()}</strong></div></div>${ability.addTx?`<button class="primary-button" data-new-customer-transaction="${c.id}">＋ ${text('New Transaction','নতুন লেনদেন')}</button>`:''}<div class="metric-grid"><article class="metric-card"><small>${text('Total Volume','মোট ভলিউম')}</small><strong>${money(totalSar)} ${foreign()}</strong></article><article class="metric-card"><small>${text('Paid Out','মোট বিতরণ')}</small><strong>${money(totalBdt)} ${local()}</strong></article><article class="metric-card"><small>${text('Transactions','লেনদেন')}</small><strong>${txs.length}</strong></article></div><div class="profile-tabs"><button class="profile-tab ${tab==='transactions'?'active':''}" data-profile-tab="transactions">${text('Transactions','লেনদেন')}</button><button class="profile-tab ${tab==='info'?'active':''}" data-profile-tab="info">${text('Customer Info','প্রোফাইল তথ্য')}</button></div><div class="profile-panel ${tab==='transactions'?'active':''}">${txs.length?groupsHtml:`<div class="empty-state">${text('No transactions recorded for this customer.','এই কাস্টমারের কোনো লেনদেন নেই।')}</div>`}</div><div class="profile-panel ${tab==='info'?'active':''}"><form id="customer-profile-form" class="surface-card stack-form" data-customer-id="${c.id}"><label><span>${text('Customer name','কাস্টমার নাম')}</span><input name="name" value="${esc(c.name)}" required></label><label><span>${text('Phone','মোবাইল')}</span><input name="phone" value="${esc(c.phone||'')}" inputmode="tel"></label><label><span>${text('Address','ঠিকানা')}</span><input name="address" value="${esc(c.address||'')}"></label><button class="primary-button" type="submit">${text('Save profile','প্রোফাইল সংরক্ষণ')}</button><button class="danger-button" type="button" data-delete-customer="${c.id}">${text('Delete Customer Profile','কাস্টমার ডিলিট')}</button></form></div>`);
  }

  function supplierFundRow(dep) {
    const expanded=state.expanded.has(`dep-${dep.id}`), purchase=['SAR_GIVEN','SAR_DEPOSIT'].includes(dep.transaction_type), settlement=['SAR_RECEIVED','SAR_SETTLEMENT'].includes(dep.transaction_type), due=num(dep.amount_bdt)-num(dep.paid_bdt), batch=state.wallet_batches.find(b=>Number(b.supplier_deposit_id)===Number(dep.id)), ledger=batch&&state.wallet_ledgers.find(l=>Number(l.id)===Number(batch.ledger_id));
    const label=settlement?text('Dues / Receivable Settlement','বকেয়া / পাওনা সমন্বয়'):text('Riyal Purchase','রিয়াল ক্রয়');
    return `<div class="ledger-row"><div class="ledger-summary" data-expand="dep-${dep.id}"><div class="ledger-main"><strong>${label}</strong><small>${timeText(dep.timestamp)}${ledger?` · ${esc(ledger.name)}`:''}</small>${dep.notes?`<small>${esc(dep.notes)}</small>`:''}</div><div class="ledger-amount"><strong>${purchase?'+':''}${money(dep.amount_bdt)} ${local()}</strong><small>${money(dep.amount_sar)} ${foreign()} · ${text('Rate','রেট')} ${money(dep.rate,4)}</small></div></div>${expanded?`<div class="ledger-detail"><div class="detail-line"><span>${text('Total BDT','সর্বমোট')}</span><span>${money(dep.amount_bdt)} ${local()}</span></div><div class="detail-line"><span>${text('Paid / received BDT','পরিশোধিত / গ্রহণ')}</span><span>${money(dep.paid_bdt)} ${local()}</span></div><div class="detail-line"><span>${text('Difference','পার্থক্য')}</span><span>${money(Math.abs(due))} ${local()}</span></div>${ability.wallet?`<div class="row-buttons"><button class="mini-button" data-edit-supplier-fund="${dep.id}">${text('Edit','এডিট')}</button><button class="mini-button danger" data-delete-supplier-fund="${dep.id}">${text('Delete','ডিলিট')}</button></div>`:''}</div>`:''}</div>`;
  }

  function renderSupplierProfile(id, tab='transactions') {
    const s=state.suppliers.find(x=>Number(x.id)===Number(id)); if(!s){closeSubpage();return;} state.profile={type:'supplier',id:Number(id),tab};
    const deps=supplierDeposits(id), balance=supplierBalance(id), groups=groupedByDate(deps), totalSar=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_sar),0), acquired=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_bdt),0), paid=deps.reduce((x,d)=>x+num(d.paid_bdt),0);
    const groupsHtml=[...groups.entries()].map(([date,items])=>`<div class="date-group"><div class="date-heading"><span>${esc(date)}</span><span>${text('Records','রেকর্ড')} ${items.length}</span></div>${items.map(supplierFundRow).join('')}</div>`).join('');
    openSubpage(`<div class="subpage-toolbar"><button class="icon-button" data-close-subpage>←</button><div><p class="eyebrow">${text('Supplier Pool Profile','সাপ্লায়ার খাতা প্রোফাইল')}</p><h2>${esc(s.name)}</h2></div></div><div class="profile-hero"><div class="profile-person"><span class="avatar">${esc((s.name||'?')[0])}</span><div><h2>${esc(s.name)}</h2><p>${esc(s.phone||'')} · ${esc(s.address||'')}</p></div></div><div class="profile-balance"><small>${balance>.05?text('Receivable (BDT)','আমি পাবো'):balance<-.05?text('Payable (BDT)','আমি দেবো'):text('No Due','বকেয়া নেই')}</small><strong>${money(Math.abs(balance))} ${local()}</strong></div></div>${ability.wallet?`<button class="primary-button" data-new-supplier-fund="${s.id}">＋ ${text('New Transaction','নতুন লেনদেন')}</button>`:''}<div class="metric-grid"><article class="metric-card"><small>${text('Riyal Deposited','রিয়াল জমা')}</small><strong>${money(totalSar)} ${foreign()}</strong></article><article class="metric-card"><small>${text('Taka Buy','টাকা ক্রয়')}</small><strong>${money(acquired)} ${local()}</strong></article><article class="metric-card"><small>${text('Paid / Received','পরিশোধিত')}</small><strong>${money(paid)} ${local()}</strong></article></div><div class="profile-tabs"><button class="profile-tab ${tab==='transactions'?'active':''}" data-profile-tab="transactions">${text('Transactions','লেনদেন সমূহ')}</button><button class="profile-tab ${tab==='info'?'active':''}" data-profile-tab="info">${text('Profile Info','প্রোফাইল তথ্য')}</button></div><div class="profile-panel ${tab==='transactions'?'active':''}"><div class="section-label"><h3>${text('Supplier Fund Registry Audit','লেনদেন ও হিসাব খাতা')}</h3><small>${deps.length} ${text('records','রেকর্ড')}</small></div>${deps.length?groupsHtml:`<div class="empty-state">${text('No fund records found.','কোনো ফান্ড রেকর্ড নেই।')}</div>`}</div><div class="profile-panel ${tab==='info'?'active':''}"><form id="supplier-profile-form" class="surface-card stack-form" data-supplier-id="${s.id}"><label><span>${text('Supplier name','সাপ্লায়ার নাম')}</span><input name="name" value="${esc(s.name)}" required></label><label><span>${text('Phone','মোবাইল')}</span><input name="phone" value="${esc(s.phone||'')}"></label><label><span>${text('Office address','অফিস ঠিকানা')}</span><input name="address" value="${esc(s.address||'')}"></label><button class="primary-button" type="submit">${text('Save profile','প্রোফাইল সংরক্ষণ')}</button><button class="danger-button" type="button" data-delete-supplier="${s.id}">${text('Delete Supplier Profile','সাপ্লায়ার ডিলিট')}</button></form></div>`);
  }

  function openCustomerTransactionChoice(customerId) {
    const due=customerBalance(customerId); if(Math.abs(due)<=.05){startCustomerFlow(customerId,'sale');return;}
    openModal(`<div class="subpage-toolbar"><button class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${text('Customer transaction','কাস্টমার লেনদেন')}</p><h2>${text('Select Transaction Type','লেনদেনের ধরণ')}</h2></div></div><div class="choice-grid"><button class="choice-card primary" data-customer-flow="sale" data-customer-id="${customerId}"><strong>${text('New Sale','নতুন বিক্রয়')}</strong><small>${text('Sell new currency. Previous dues can also be settled in Step 2.','নতুন কারেন্সি বিক্রয়; ধাপ ২-এ আগের বকেয়াও সমন্বয় করা যাবে।')}</small></button>${due>.05?`<button class="choice-card due" data-customer-flow="due" data-customer-id="${customerId}"><strong>${text('Due Collection','বকেয়া আদায়')}</strong><small>${text(`Collect previous outstanding dues (${money(due)} ${foreign()}).`,`পূর্বের বকেয়া আদায় করুন (${money(due)} ${foreign()})।`)}</small></button>`:''}${due<-.05?`<button class="choice-card advance" data-customer-flow="advance" data-customer-id="${customerId}"><strong>${text('Advance Return','পাওনা ফেরত')}</strong><small>${text(`Return customer's advance (${money(Math.abs(due))} ${foreign()}).`,`কাস্টমারের পাওনা ফেরত দিন (${money(Math.abs(due))} ${foreign()})।`)}</small></button>`:''}</div>`);
  }

  function startCustomerFlow(customerId, mode) {
    closeModal(); const due=customerBalance(customerId);
    state.flow={kind:'customer', customerId:Number(customerId), mode, step:mode==='sale'?1:2, amount:'', date:todayInput(), notes:'', payment:'Cash', account:'', walletBatchId:'', customerRate:state.settings.rate_based_mode===false?'1.0':'32.10', sarCollected:'', dueAdjustment:'', due, receiverName:'Recipient'};
    renderCustomerFlow();
  }
  function renderCustomerFlow() {
    const f=state.flow, c=state.customers.find(x=>Number(x.id)===f.customerId); if(!f||!c)return;
    const sale=f.mode==='sale', step=f.step, amount=num(f.amount), customerRate=num(f.customerRate), payout=amount*customerRate, selected=state.wallet_batches.find(b=>Number(b.id)===Number(f.walletBatchId));
    const paymentMethods=['Cash','Bkash','Nagad','Rocket','Bank Transfer'];
    const header=`<div class="subpage-toolbar"><button class="icon-button" data-flow-cancel>←</button><div><p class="eyebrow">${esc(c.name)}</p><h2>${sale?(step===1?text('Step 1: Details','ধাপ ১: বিবরণ'):text('Step 2: Wallet & Rates','ধাপ ২: ওয়ালেট ও পেমেন্ট')):f.mode==='due'?text('Due Collection','বকেয়া আদায়'):text('Advance Return','পাওনা ফেরত')}</h2></div></div><div class="stepper"><span class="step-segment active"></span><span class="step-segment ${step===2?'active':''}"></span></div>`;
    if(step===1){
      openSubpage(`${header}<form id="customer-flow-step1" class="flow-card stack-form"><label><span>${text(`Amount ${foreign()}`,`পরিমাণ ${foreign()}`)}</span><input name="amount" value="${esc(f.amount)}" inputmode="decimal" placeholder="0.00" required autofocus></label><label><span>${text('Transaction date','তারিখ')}</span><input name="date" type="date" max="${todayInput()}" value="${esc(f.date)}" required></label><label><span>${text('Notes / Description','মন্তব্য')}</span><textarea name="notes">${esc(f.notes)}</textarea></label><button class="primary-button" type="submit">${text('Next Step','পরের ধাপ')} →</button></form>`); return;
    }
    const active=activeBatches(); const adjustmentLabel=f.due>.05?text('Previous Due Collection','পূর্বের বকেয়া আদায়'):f.due<-.05?text('Return Advanced Balance','পাওনা ফেরত'):'';
    const adjustmentKind=f.due<-.05?'advance':'due';
    openSubpage(`${header}<form id="customer-flow-step2" class="stack-form"><div class="flow-card"><h3>${text('Select Payout Method','পেমেন্ট মেথড নির্বাচন')}</h3><div class="payment-methods">${paymentMethods.map(m=>`<button type="button" class="choice-chip ${f.payment===m?'active':''}" data-payment-method="${esc(m)}">${esc(m)}</button>`).join('')}</div>${f.payment!=='Cash'?`<label><span>${f.payment==='Bank Transfer'?text('Bank Account Number','ব্যাংক হিসাব নম্বর'):esc(f.payment)+' '+text('Number','নম্বর')}</span><input name="account" value="${esc(f.account)}" required></label>`:''}</div>${sale?`<div class="flow-card"><h3>${text('Select Wallet Account','ওয়ালেট নির্বাচন')}</h3><label><span>${text('Wallet stock','ওয়ালেট স্টক')}</span><select name="wallet_batch_id" required><option value="">${text('Select wallet stock','ওয়ালেট স্টক নির্বাচন')}</option>${active.map(b=>{const l=state.wallet_ledgers.find(x=>Number(x.id)===Number(b.ledger_id));return `<option value="${b.id}" ${Number(f.walletBatchId)===Number(b.id)?'selected':''}>${esc(l?.name||'Wallet')} · ${money(b.remaining_bdt)} ${local()} · ${text('Rate','রেট')} ${money(b.rate,4)}</option>`}).join('')}</select></label>${selected?`<div class="info-box">${text('Wallet Cost Rate (Auto)','ওয়ালেট ক্রয় রেট')}: ${money(selected.rate,4)} · ${text('Stock','স্টক')}: ${money(selected.remaining_bdt)} ${local()}</div>`:''}</div>${Math.abs(f.due)>.05?`<div class="flow-card"><h3>${adjustmentLabel}</h3><div class="${f.due>.05?'warning-box':'info-box'}">${text('Current balance','বর্তমান ব্যালেন্স')}: ${money(Math.abs(f.due))} ${foreign()}</div><label><span>${f.due>.05?text('Due collected amount','বকেয়া আদায়'):text('Advance returned amount','ফেরত পরিমাণ')} (${foreign()})</span><input name="due_adjustment" value="${esc(f.dueAdjustment)}" inputmode="decimal" placeholder="0.00"></label></div>`:''}<div class="flow-card"><h3>${text('Sales Price & Riyal Summary','বিক্রয় মূল্য ও রিয়াল হিসাব')}</h3><div class="inline-edit"><label><span>${text('Selling Rate','বিক্রয় রেট')} ${local()}</span><input name="customer_rate" value="${esc(f.customerRate)}" inputmode="decimal" ${state.settings.rate_based_mode===false?'readonly':''} required></label><label><span>${text('Amount','পরিমাণ')} ${foreign()}</span><input value="${esc(f.amount)}" readonly></label><label><span>${text('Received','গ্রহণ')} ${foreign()}</span><input name="sar_collected" value="${esc(f.sarCollected||f.amount)}" inputmode="decimal" required></label><label><span>${text('Disbursed','বিতরণ')} ${local()}</span><input value="${money(payout)}" readonly></label></div></div><div class="flow-card"><h3>${text('Review Details','রিভিউ ও সাবমিট')}</h3><div class="summary-box"><div class="summary-line"><span>${text('SAR Amount','রিয়াল পরিমাণ')}</span><strong>${money(f.amount)} ${foreign()}</strong></div><div class="summary-line"><span>${text('Beneficiary payout','প্রাপক পাবে')}</span><strong>${money(payout)} ${local()}</strong></div><div class="summary-line"><span>${text('Payout channel','পেমেন্ট মেথড')}</span><strong>${esc(f.payment)}</strong></div><div class="summary-line"><span>${text('Customer rate','বিক্রয় রেট')}</span><strong>${money(f.customerRate,4)}</strong></div></div></div>`:`<div class="flow-card"><h3>${f.mode==='due'?text('Collect Previous Outstanding Dues','পূর্বের বকেয়া আদায়'):text('Return Advanced Balance','পাওনা ফেরত')}</h3><div class="${f.mode==='due'?'warning-box':'info-box'}">${text('Available balance','বর্তমান ব্যালেন্স')}: ${money(Math.abs(f.due))} ${foreign()}</div><label><span>${text('Amount','পরিমাণ')} ${foreign()}</span><input name="adjustment_amount" value="${esc(f.dueAdjustment)}" inputmode="decimal" required autofocus></label><label><span>${text('Transaction date','তারিখ')}</span><input name="date" type="date" max="${todayInput()}" value="${esc(f.date)}" required></label></div>`}<div class="flow-actions"><button type="button" class="secondary-button" data-flow-back>${sale?text('Back','পিছনে'):text('Cancel','বাতিল')}</button><button class="primary-button" type="submit">✓ ${text('Create Transaction','লেনদেন সম্পন্ন করুন')}</button></div></form>`);
  }

  function openTransactionEdit(id) {
    const tx=state.transactions.find(x=>Number(x.id)===Number(id)); if(!tx)return; const active=activeBatches();
    openModal(`<form id="transaction-edit-form" class="stack-form" data-id="${tx.id}"><div class="subpage-toolbar"><button class="icon-button" type="button" data-close-modal>×</button><div><p class="eyebrow">${text('Transaction','লেনদেন')}</p><h2>${text('Edit Transaction','লেনদেন এডিট')}</h2></div></div><div class="inline-edit"><label><span>${text('Amount','পরিমাণ')} ${foreign()}</span><input name="amount_sar" value="${esc(tx.amount_sar)}" inputmode="decimal" required></label><label><span>${text('Customer Rate','কাস্টমার রেট')}</span><input name="customer_rate" value="${esc(tx.customer_rate)}" inputmode="decimal" required></label><label><span>${text('Supplier Rate','সাপ্লায়ার রেট')}</span><input name="supplier_rate" value="${esc(tx.supplier_rate)}" inputmode="decimal"></label><label><span>${text('Collected','গ্রহণ')} ${foreign()}</span><input name="sar_collected" value="${esc(tx.sar_collected)}" inputmode="decimal" required></label><label><span>${text('Disbursed','বিতরণ')} ${local()}</span><input name="bdt_disbursed" value="${esc(tx.bdt_disbursed)}" inputmode="decimal"></label><label><span>${text('Status','স্ট্যাটাস')}</span><select name="status">${['Pending','Delivered','Cancelled'].map(s=>`<option ${statusOf(tx)===s?'selected':''}>${s}</option>`).join('')}</select></label></div><label><span>${text('Wallet stock','ওয়ালেট')}</span><select name="wallet_batch_id"><option value="">N/A</option>${active.map(b=>`<option value="${b.id}" ${Number(tx.wallet_batch_id)===Number(b.id)?'selected':''}>${esc(state.wallet_ledgers.find(l=>Number(l.id)===Number(b.ledger_id))?.name||'Wallet')} · ${money(b.remaining_bdt)} · ${money(b.rate,4)}</option>`).join('')}</select></label><label><span>${text('Payout method','পেমেন্ট মেথড')}</span><input name="receiver_account_type" value="${esc(tx.receiver_account_type||'Cash')}" required></label><label><span>${text('Account number','হিসাব নম্বর')}</span><input name="receiver_account_no" value="${esc(tx.receiver_account_no||'')}"></label><label><span>${text('Receiver','প্রাপক')}</span><input name="receiver_name" value="${esc(tx.receiver_name||'')}"></label><label><span>${text('Phone','ফোন')}</span><input name="receiver_phone" value="${esc(tx.receiver_phone||'')}"></label><label><span>${text('Notes','মন্তব্য')}</span><textarea name="notes">${esc(tx.notes||'')}</textarea></label><button class="primary-button" type="submit">${text('Save Transaction','সংরক্ষণ')}</button></form>`);
  }

  function openReceipt(id) {
    const tx=state.transactions.find(x=>Number(x.id)===Number(id)); if(!tx)return; const c=state.customers.find(x=>Number(x.id)===Number(tx.customer_id));
    openModal(`<div class="subpage-toolbar"><button class="icon-button" data-close-modal>×</button><div><p class="eyebrow">SAFA</p><h2>${text('Transaction Confirmation','লেনদেন নিশ্চিতকরণ')}</h2></div></div><div class="summary-box"><div class="summary-line"><span>${text('Customer','কাস্টমার')}</span><strong>${esc(c?.name||'')}</strong></div><div class="summary-line"><span>${text('Amount','পরিমাণ')}</span><strong>${money(tx.amount_sar)} ${foreign()}</strong></div><div class="summary-line"><span>${text('Rate','রেট')}</span><strong>${money(tx.customer_rate,4)}</strong></div><div class="summary-line"><span>${text('Collected','গ্রহণ')}</span><strong>${money(tx.sar_collected)} ${foreign()}</strong></div><div class="summary-line"><span>${text('Payout','বিতরণ')}</span><strong>${money(tx.amount_bdt)} ${local()}</strong></div><div class="summary-line"><span>${text('Method','মেথড')}</span><strong>${esc(tx.receiver_account_type||'')}</strong></div><div class="summary-line"><span>${text('Date','তারিখ')}</span><strong>${dateText(tx.timestamp)} ${timeText(tx.timestamp)}</strong></div></div><button class="secondary-button" type="button" data-print-receipt>${text('Print / Share','প্রিন্ট / শেয়ার')}</button>`);
  }

  function openSupplierFundChoice(id) {
    const balance=supplierBalance(id); openModal(`<div class="subpage-toolbar"><button class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${text('Supplier transaction','সাপ্লায়ার লেনদেন')}</p><h2>${text('Select Transaction Type','লেনদেনের ধরণ')}</h2></div></div><div class="choice-grid"><button class="choice-card primary" data-supplier-flow="purchase" data-supplier-id="${id}"><strong>${text(`New ${foreign()} Purchase`,`নতুন ${foreign()} ক্রয়`)}</strong><small>${text('Purchase new funds from this supplier and add the resulting BDT to a wallet ledger.','সাপ্লায়ার থেকে ফান্ড ক্রয় করে নির্বাচিত ওয়ালেটে স্টক যোগ করুন।')}</small></button>${Math.abs(balance)>.05?`<button class="choice-card ${balance<0?'due':'advance'}" data-supplier-flow="settlement" data-supplier-id="${id}"><strong>${balance<0?text('Clear Supplier Due','বকেয়া মূল্য পরিশোধ'):text('Collect Receivable','পাওনা আদায়')}</strong><small>${text(`Settle the current ${money(Math.abs(balance))} ${local()} balance without creating wallet stock.`,`বর্তমান ${money(Math.abs(balance))} ${local()} হিসাব সমন্বয় করুন; ওয়ালেট স্টক তৈরি হবে না।`)}</small></button>`:''}</div>`);
  }
  function startSupplierFlow(id, mode, existing=null) {
    closeModal(); const dep=existing||{}; state.flow={kind:'supplier',supplierId:Number(id),fundId:existing?.id||null,mode,step:1,amount:dep.amount_sar||'',rate:dep.rate||'32.50',paid:dep.paid_bdt||'',date:existing?new Date(Number(dep.timestamp)*1000).toISOString().slice(0,10):todayInput(),notes:dep.notes||'',ledgerId:existing?String(state.wallet_batches.find(b=>Number(b.supplier_deposit_id)===Number(dep.id))?.ledger_id||''):''}; renderSupplierFlow();
  }
  function renderSupplierFlow() {
    const f=state.flow,s=state.suppliers.find(x=>Number(x.id)===f.supplierId);if(!s)return; const purchase=f.mode==='purchase', calculated=num(f.amount)*num(f.rate), step=f.step;
    const header=`<div class="subpage-toolbar"><button class="icon-button" data-flow-cancel>←</button><div><p class="eyebrow">${esc(s.name)}</p><h2>${step===1?text('Step 1: Details','ধাপ ১: বিবরণ'):text('Step 2: Payment','ধাপ ২: পেমেন্ট')}</h2></div></div><div class="stepper"><span class="step-segment active"></span><span class="step-segment ${step===2?'active':''}"></span></div>`;
    if(step===1){openSubpage(`${header}<form id="supplier-flow-step1" class="flow-card stack-form"><div class="${purchase?'success-box':'info-box'}">${purchase?text('New fund purchase: saving this record will create linked wallet stock.','নতুন ফান্ড ক্রয়: সংরক্ষণ করলে লিঙ্কড ওয়ালেট স্টক তৈরি হবে।'):text('Settlement: this record changes the supplier ledger only and will not create wallet stock.','সমন্বয়: শুধু সাপ্লায়ার খাতা পরিবর্তন হবে; ওয়ালেট স্টক তৈরি হবে না।')}</div><label><span>${text(`Amount (${foreign()})`,`পরিমাণ (${foreign()})`)}</span><input name="amount" value="${esc(f.amount)}" inputmode="decimal" required autofocus></label><label><span>${text('Date','তারিখ')}</span><input name="date" type="date" max="${todayInput()}" value="${esc(f.date)}" required></label><label><span>${text('Notes / Description','মন্তব্য')}</span><textarea name="notes">${esc(f.notes)}</textarea></label><button class="primary-button" type="submit">${text('Next Step','পরবর্তী ধাপ')} →</button></form>`);return;}
    openSubpage(`${header}<form id="supplier-flow-step2" class="stack-form"><div class="flow-card"><label><span>${text('Exchange Rate','বিনিময় রেট')}</span><input name="rate" value="${esc(f.rate)}" inputmode="decimal" ${state.settings.supplier_rate_enabled===false?'readonly':''} required></label><label><span>${purchase?text(`Paid / Received (${local()})`,`পরিশোধিত / গ্রহণ (${local()})`):text(`Settlement Value (${local()})`,`সমন্বয় মূল্য (${local()})`)}</span><input name="paid" value="${esc(f.paid||money(calculated))}" inputmode="decimal" required></label><div class="summary-box"><div class="summary-line"><span>${text('Calculated BDT','হিসাবকৃত টাকা')}</span><strong>${money(calculated)} ${local()}</strong></div><div class="summary-line"><span>${text('Difference','পার্থক্য')}</span><strong>${money(Math.abs(calculated-num(f.paid||calculated)))} ${local()}</strong></div></div>${purchase?`<label><span>${text('Wallet','ওয়ালেট')}</span><select name="ledger_id" required><option value="">${text('Select Wallet','ওয়ালেট নির্বাচন')}</option>${state.wallet_ledgers.map(l=>`<option value="${l.id}" ${Number(f.ledgerId)===Number(l.id)?'selected':''}>${esc(l.name)}</option>`).join('')}</select></label>`:''}</div><div class="flow-actions"><button type="button" class="secondary-button" data-flow-back>${text('Back','পিছনে')}</button><button class="primary-button" type="submit">✓ ${f.fundId?text('Save Record','রেকর্ড সংরক্ষণ'):text('Save Record','সংরক্ষণ করুন')}</button></div></form>`);
  }

  async function loadUsers() { if(!ability.users||!urls.users)return; try{const data=await api(urls.users);state.users=data.users||[];renderUsers();}catch(e){toast(e.message,true);} }
  function renderUsers(){const list=$('#users-list');if(!list)return;list.innerHTML=state.users.length?state.users.map(u=>`<article class="entity-card"><div class="entity-head"><div class="entity-identity"><span class="avatar">${esc((u.name||'?')[0])}</span><div><strong>${esc(u.name)}</strong><small>${esc(u.mobile||'')} · ${esc(u.role_label||u.role)}</small></div></div><span class="status-pill ${u.is_activated?'delivered':'cancelled'}">${u.is_activated?text('Active','সক্রিয়'):text('Inactive','নিষ্ক্রিয়')}</span></div><div class="entity-actions"><button class="mini-button" data-user-edit="${u.id}">${text('Edit','এডিট')}</button><button class="mini-button ${u.is_activated?'danger':'primary'}" data-user-toggle="${u.id}">${u.is_activated?text('Deactivate','নিষ্ক্রিয়'):text('Activate','সক্রিয়')}</button><button class="mini-button danger" data-user-delete="${u.id}">${text('Delete','ডিলিট')}</button></div></article>`).join(''):`<div class="empty-state">${text('No manageable users.','পরিচালনাযোগ্য ইউজার নেই।')}</div>`;}

  function openExpenseForm(existing=null){openModal(`<form id="expense-form" class="stack-form" data-id="${existing?.id||''}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${text('Daily entry','দৈনিক এন্ট্রি')}</p><h2>${existing?text('Edit entry','এন্ট্রি এডিট'):text('Add income / expense','আয় / ব্যয় যোগ')}</h2></div></div><label><span>${text('Title','শিরোনাম')}</span><input name="title" value="${esc(existing?.title||'')}" required></label><div class="inline-edit"><label><span>${text('Amount','পরিমাণ')}</span><input name="amount" value="${esc(existing?.amount||'')}" inputmode="decimal" required></label><label><span>${text('Currency','কারেন্সি')}</span><input name="currency" value="${esc(existing?.currency||local())}" required></label><label><span>${text('Type','ধরণ')}</span><select name="is_expense"><option value="1" ${existing==null||Number(existing?.is_expense)?'selected':''}>${text('Expense','ব্যয়')}</option><option value="0" ${existing!=null&&!Number(existing?.is_expense)?'selected':''}>${text('Income','আয়')}</option></select></label><label><span>${text('Category','ক্যাটাগরি')}</span><input name="category" value="${esc(existing?.category||'General')}"></label></div><button class="primary-button" type="submit">${text('Save','সংরক্ষণ')}</button></form>`);}
  function openWalletLedgerForm(){openModal(`<form id="wallet-ledger-form" class="stack-form"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${text('Wallet','ওয়ালেট')}</p><h2>${text('New Ledger Register','নতুন লেজার রেজিস্টার')}</h2></div></div><label><span>${text('Ledger name','লেজারের নাম')}</span><input name="name" required autofocus></label><button class="primary-button" type="submit">${text('Create ledger','লেজার তৈরি')}</button></form>`);}
  function openWalletAction(id,type){const l=state.wallet_ledgers.find(x=>Number(x.id)===Number(id));if(!l)return;if(type==='deposit')openModal(`<form id="wallet-deposit-form" class="stack-form" data-ledger-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${esc(l.name)}</p><h2>${text('Deposit Money','টাকা জমা')}</h2></div></div><label><span>${text(`Amount (${local()})`,`পরিমাণ (${local()})`)}</span><input name="amount_bdt" inputmode="decimal" required></label><label><span>${text('Rate','রেট')}</span><input name="rate" value="32.0000" inputmode="decimal" required></label><label><span>${text('Notes','মন্তব্য')}</span><input name="notes" value="Manual Capital Deposit"></label><button class="primary-button" type="submit">${text('Deposit','জমা')}</button></form>`);else if(type==='withdraw')openModal(`<form id="wallet-withdraw-form" class="stack-form" data-ledger-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${esc(l.name)}</p><h2>${text('Withdraw Money','টাকা উত্তোলন')}</h2></div></div><div class="info-box">${text('Available','ব্যালেন্স')}: ${money(walletBalance(id))} ${local()} · ${text('Oldest active stock is deducted first.','পুরাতন সক্রিয় স্টক আগে কাটা হবে।')}</div><label><span>${text(`Amount (${local()})`,`পরিমাণ (${local()})`)}</span><input name="amount_bdt" inputmode="decimal" required></label><button class="primary-button" type="submit">${text('Withdraw','উত্তোলন')}</button></form>`);else openModal(`<form id="wallet-rename-form" class="stack-form" data-ledger-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><h2>${text('Rename Ledger','লেজারের নাম পরিবর্তন')}</h2></div></div><label><span>${text('Ledger name','নাম')}</span><input name="name" value="${esc(l.name)}" required></label><button class="primary-button" type="submit">${text('Save','সংরক্ষণ')}</button></form>`);}
  function openUserEdit(id){const u=state.users.find(x=>Number(x.id)===Number(id));if(!u)return;openModal(`<form id="user-edit-form" class="stack-form" data-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><h2>${text('Edit User','ইউজার এডিট')}</h2></div></div><label><span>${text('Name','নাম')}</span><input name="name" value="${esc(u.name)}" required></label><label><span>${text('Mobile','মোবাইল')}</span><input name="mobile" value="${esc(u.mobile||'')}" required></label><label><span>${text('Email','ইমেইল')}</span><input name="email" type="email" value="${esc(u.email||'')}"></label><label><span>${text('New PIN (optional)','নতুন পিন (ঐচ্ছিক)')}</span><input name="pin" minlength="6" maxlength="6" inputmode="numeric"></label><button class="primary-button" type="submit">${text('Save user','সংরক্ষণ')}</button></form>`);}

  function formObject(form){return Object.fromEntries(new FormData(form).entries());}

  document.addEventListener('input', e=>{if(['customer-search','supplier-search'].includes(e.target.id)){e.target.id==='customer-search'?renderCustomers():renderSuppliers();}});
  document.addEventListener('change', e=>{
    if(['customer-filter','customer-sort'].includes(e.target.id))renderCustomers(); if(['supplier-filter','supplier-sort'].includes(e.target.id))renderSuppliers();
    if(state.flow?.kind==='customer'&&e.target.name==='wallet_batch_id'){state.flow.walletBatchId=e.target.value;renderCustomerFlow();}
  });

  document.addEventListener('click', async e=>{
    const target=e.target.closest('button,[data-open-customer],[data-open-supplier],[data-expand]'); if(!target)return;
    try {
      if(target.dataset.nav){setScreen(target.dataset.nav);return;} if(target.matches('[data-open-settings]')){setScreen('settings');return;} if(target.matches('[data-settings-back]')){setScreen(state.previousMain||'dashboard');return;}
      if(target.dataset.action==='refresh'){await loadWorkspace();return;} if(target.id==='theme-toggle'){const current=localStorage.getItem('safa-web-theme')||'system';setTheme(current==='dark'?'light':'dark');return;}
      if(target.matches('[data-close-modal]')){closeModal();return;} if(target.matches('[data-close-subpage]')){closeSubpage();return;} if(target.matches('[data-print-receipt]')){window.print();return;}
      if(target.dataset.openCreate){const tpl=$(`#create-${target.dataset.openCreate}-template`);if(tpl)openModal(tpl.innerHTML);return;}
      if(target.hasAttribute('data-open-wallet-ledger')){openWalletLedgerForm();return;} if(target.hasAttribute('data-open-expense')){openExpenseForm();return;}
      if(target.dataset.openCustomer){renderCustomerProfile(Number(target.dataset.openCustomer));return;} if(target.dataset.openSupplier){renderSupplierProfile(Number(target.dataset.openSupplier));return;}
      if(target.dataset.profileTab&&state.profile){state.profile.type==='customer'?renderCustomerProfile(state.profile.id,target.dataset.profileTab):renderSupplierProfile(state.profile.id,target.dataset.profileTab);return;}
      if(target.dataset.newCustomerTransaction){openCustomerTransactionChoice(Number(target.dataset.newCustomerTransaction));return;}
      if(target.dataset.customerFlow){startCustomerFlow(Number(target.dataset.customerId),target.dataset.customerFlow);return;}
      if(target.dataset.paymentMethod&&state.flow?.kind==='customer'){state.flow.payment=target.dataset.paymentMethod;if(state.flow.payment==='Cash')state.flow.account='';renderCustomerFlow();return;}
      if(target.matches('[data-flow-cancel]')){if(state.profile?.type==='customer')renderCustomerProfile(state.profile.id,state.profile.tab);else if(state.profile?.type==='supplier')renderSupplierProfile(state.profile.id,state.profile.tab);else closeSubpage();state.flow=null;return;}
      if(target.matches('[data-flow-back]')&&state.flow){if(state.flow.step===2&&state.flow.mode==='sale'){state.flow.step=1;state.flow.kind==='customer'?renderCustomerFlow():renderSupplierFlow();}else if(state.flow.kind==='supplier'){state.flow.step=1;renderSupplierFlow();}else target.click();return;}
      if(target.dataset.expand){const key=target.dataset.expand;state.expanded.has(key)?state.expanded.delete(key):state.expanded.add(key);if(state.profile?.type==='customer')renderCustomerProfile(state.profile.id,state.profile.tab);else if(state.profile?.type==='supplier')renderSupplierProfile(state.profile.id,state.profile.tab);return;}
      if(target.dataset.editTransaction){openTransactionEdit(Number(target.dataset.editTransaction));return;} if(target.dataset.shareTransaction){openReceipt(Number(target.dataset.shareTransaction));return;}
      if(target.dataset.statusTransaction){await api(`${baseWithId(urls.transactions,target.dataset.statusTransaction)}/status`,{method:'PATCH',body:{status:target.dataset.status}});await loadWorkspace(true);toast(text('Transaction status updated.','স্ট্যাটাস আপডেট হয়েছে।'));return;}
      if(target.dataset.deleteTransaction){if(!confirm(text('Delete this transaction? Wallet stock will be reconciled.','এই লেনদেন ডিলিট করবেন? ওয়ালেট স্টক সমন্বয় হবে।')))return;await api(baseWithId(urls.transactions,target.dataset.deleteTransaction),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);toast(text('Transaction deleted.','লেনদেন ডিলিট হয়েছে।'));return;}
      if(target.dataset.newSupplierFund){openSupplierFundChoice(Number(target.dataset.newSupplierFund));return;} if(target.dataset.supplierFlow){startSupplierFlow(Number(target.dataset.supplierId),target.dataset.supplierFlow);return;}
      if(target.dataset.editSupplierFund){const dep=state.supplier_deposits.find(x=>Number(x.id)===Number(target.dataset.editSupplierFund));if(dep)startSupplierFlow(dep.supplier_id,['SAR_GIVEN','SAR_DEPOSIT'].includes(dep.transaction_type)?'purchase':'settlement',dep);return;}
      if(target.dataset.deleteSupplierFund){if(!confirm(text('Delete this supplier fund record and its linked wallet stock?','এই ফান্ড রেকর্ড ও লিঙ্কড ওয়ালেট স্টক ডিলিট করবেন?')))return;await api(baseWithId(urls.supplierFunds,target.dataset.deleteSupplierFund),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);toast(text('Fund record deleted.','ফান্ড রেকর্ড ডিলিট হয়েছে।'));return;}
      if(target.dataset.toggleWallet){const id=Number(target.dataset.toggleWallet);state.walletExpanded.has(id)?state.walletExpanded.delete(id):state.walletExpanded.add(id);renderWallet();return;}
      if(target.dataset.walletDeposit){openWalletAction(Number(target.dataset.walletDeposit),'deposit');return;}if(target.dataset.walletWithdraw){openWalletAction(Number(target.dataset.walletWithdraw),'withdraw');return;}if(target.dataset.walletRename){openWalletAction(Number(target.dataset.walletRename),'rename');return;}
      if(target.dataset.walletDelete){if(!confirm(text('Delete this empty wallet ledger?','এই খালি ওয়ালেট লেজার ডিলিট করবেন?')))return;await api(baseWithId(urls.walletLedgers,target.dataset.walletDelete),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);toast(text('Wallet ledger deleted.','ওয়ালেট লেজার ডিলিট হয়েছে।'));return;}
      if(target.dataset.expenseEdit){openExpenseForm(state.expenses.find(x=>Number(x.id)===Number(target.dataset.expenseEdit)));return;} if(target.dataset.expenseDelete){if(!confirm(text('Delete this entry?','এই এন্ট্রি ডিলিট করবেন?')))return;await api(baseWithId(urls.expenses,target.dataset.expenseDelete),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);return;}
      if(target.dataset.deleteCustomer){if(!confirm(text('Delete this customer profile?','এই কাস্টমার ডিলিট করবেন?')))return;await api(baseWithId(urls.customers,target.dataset.deleteCustomer),{method:'DELETE',body:{confirmed:true}});closeSubpage();await loadWorkspace(true);return;}
      if(target.dataset.deleteSupplier){if(!confirm(text('Delete this supplier profile?','এই সাপ্লায়ার ডিলিট করবেন?')))return;await api(baseWithId(urls.suppliers,target.dataset.deleteSupplier),{method:'DELETE',body:{confirmed:true}});closeSubpage();await loadWorkspace(true);return;}
      if(target.dataset.userEdit){openUserEdit(Number(target.dataset.userEdit));return;}if(target.dataset.userToggle){const u=state.users.find(x=>Number(x.id)===Number(target.dataset.userToggle));if(!u)return;await api(baseWithId(urls.users,u.id),{method:'PATCH',body:{is_activated:!u.is_activated}});await loadUsers();return;}if(target.dataset.userDelete){if(!confirm(text('Delete this user?','এই ইউজার ডিলিট করবেন?')))return;await api(baseWithId(urls.users,target.dataset.userDelete),{method:'DELETE',body:{confirmed:true}});await loadUsers();return;}
    } catch(error){toast(error.message,true);}
  });

  document.addEventListener('submit', async e=>{
    const form=e.target; e.preventDefault();
    try {
      if(form.dataset.createEntity){const base=form.dataset.createEntity==='customers'?urls.customers:urls.suppliers;await api(base,{method:'POST',body:formObject(form)});closeModal();await loadWorkspace(true);toast(text('Profile saved.','প্রোফাইল সংরক্ষিত।'));return;}
      if(form.id==='customer-profile-form'){await api(baseWithId(urls.customers,form.dataset.customerId),{method:'PUT',body:formObject(form)});await loadWorkspace(true);toast(text('Customer updated.','কাস্টমার আপডেট হয়েছে।'));return;}
      if(form.id==='supplier-profile-form'){await api(baseWithId(urls.suppliers,form.dataset.supplierId),{method:'PUT',body:formObject(form)});await loadWorkspace(true);toast(text('Supplier updated.','সাপ্লায়ার আপডেট হয়েছে।'));return;}
      if(form.id==='customer-flow-step1'){const d=formObject(form);state.flow.amount=d.amount;state.flow.date=d.date;state.flow.notes=d.notes||'';state.flow.sarCollected=d.amount;state.flow.step=2;renderCustomerFlow();return;}
      if(form.id==='customer-flow-step2'){
        const d=formObject(form),f=state.flow; if(!f)return; f.account=d.account||''; f.date=d.date||f.date; f.dueAdjustment=d.due_adjustment||d.adjustment_amount||'';
        if(f.mode==='sale'){
          f.walletBatchId=d.wallet_batch_id||f.walletBatchId;f.customerRate=d.customer_rate||f.customerRate;f.sarCollected=d.sar_collected||f.amount;
          const selected=state.wallet_batches.find(b=>Number(b.id)===Number(f.walletBatchId));const payout=num(f.amount)*num(f.customerRate);if(!selected)throw new Error(text('Select wallet stock.','ওয়ালেট স্টক নির্বাচন করুন।'));if(payout>num(selected.remaining_bdt)+.005)throw new Error(text('Selected wallet stock is insufficient.','নির্বাচিত ওয়ালেট স্টক পর্যাপ্ত নয়।'));
          await api(urls.customerSale,{method:'POST',body:{customer_id:f.customerId,wallet_batch_id:Number(f.walletBatchId),amount_sar:f.amount,customer_rate:f.customerRate,sar_collected:f.sarCollected,bdt_disbursed:payout.toFixed(2),receiver_name:'Recipient',receiver_phone:f.payment==='Cash'?'Cash':f.account,receiver_account_type:f.payment,receiver_account_no:f.payment==='Cash'?'Cash':f.account,notes:f.notes,timestamp:selectedUnix(f.date),due_adjustment_type:num(f.dueAdjustment)>.05?(f.due<-.05?'advance':'due'):null,due_adjustment_amount:num(f.dueAdjustment)>.05?f.dueAdjustment:null}});
        } else {
          if(num(f.dueAdjustment)<=.05)throw new Error(text('Enter an adjustment amount.','সমন্বয়ের পরিমাণ লিখুন।'));
          await api(urls.customerAdjustment,{method:'POST',body:{customer_id:f.customerId,kind:f.mode==='advance'?'advance':'due',amount_sar:f.dueAdjustment,timestamp:selectedUnix(f.date)}});
        }
        const id=f.customerId;state.flow=null;await loadWorkspace(true);renderCustomerProfile(id);toast(text('Transaction created.','লেনদেন সম্পন্ন হয়েছে।'));return;
      }
      if(form.id==='transaction-edit-form'){const d=formObject(form),id=form.dataset.id;d.wallet_batch_id=d.wallet_batch_id||null;await api(baseWithId(urls.transactions,id),{method:'PATCH',body:d});closeModal();await loadWorkspace(true);toast(text('Transaction updated.','লেনদেন আপডেট হয়েছে।'));return;}
      if(form.id==='supplier-flow-step1'){const d=formObject(form);state.flow.amount=d.amount;state.flow.date=d.date;state.flow.notes=d.notes||'';state.flow.step=2;renderSupplierFlow();return;}
      if(form.id==='supplier-flow-step2'){const d=formObject(form),f=state.flow;if(!f)return;f.rate=d.rate;f.paid=d.paid;f.ledgerId=d.ledger_id||'';const payload={supplier_id:f.supplierId,transaction_type:f.mode==='purchase'?'SAR_GIVEN':'SAR_RECEIVED',amount_sar:f.amount,rate:f.rate,paid_bdt:f.paid|| (num(f.amount)*num(f.rate)).toFixed(2),ledger_id:f.mode==='purchase'?Number(f.ledgerId):null,notes:f.notes,timestamp:selectedUnix(f.date)};if(f.mode==='purchase'&&!f.ledgerId)throw new Error(text('Select a wallet ledger.','ওয়ালেট নির্বাচন করুন।'));if(f.fundId)await api(baseWithId(urls.supplierFunds,f.fundId),{method:'PATCH',body:payload});else await api(urls.supplierFunds,{method:'POST',body:payload});const id=f.supplierId;state.flow=null;await loadWorkspace(true);renderSupplierProfile(id);toast(text('Supplier record saved.','সাপ্লায়ার রেকর্ড সংরক্ষিত।'));return;}
      if(form.id==='wallet-ledger-form'){await api(urls.walletLedgers,{method:'POST',body:formObject(form)});closeModal();await loadWorkspace(true);return;}
      if(form.id==='wallet-deposit-form'){const d=formObject(form);d.ledger_id=Number(form.dataset.ledgerId);await api(urls.walletDeposit,{method:'POST',body:d});closeModal();await loadWorkspace(true);toast(text('Wallet deposit saved.','ওয়ালেট জমা সংরক্ষিত।'));return;}
      if(form.id==='wallet-withdraw-form'){const d=formObject(form);d.ledger_id=Number(form.dataset.ledgerId);await api(urls.walletWithdraw,{method:'POST',body:d});closeModal();await loadWorkspace(true);toast(text('Wallet withdrawal completed FIFO.','ওয়ালেট উত্তোলন সম্পন্ন।'));return;}
      if(form.id==='wallet-rename-form'){await api(baseWithId(urls.walletLedgers,form.dataset.ledgerId),{method:'PATCH',body:formObject(form)});closeModal();await loadWorkspace(true);return;}
      if(form.id==='expense-form'){const d=formObject(form),id=form.dataset.id;d.is_expense=d.is_expense==='1';if(id)await api(baseWithId(urls.expenses,id),{method:'PUT',body:d});else await api(urls.expenses,{method:'POST',body:d});closeModal();await loadWorkspace(true);return;}
      if(form.id==='profile-settings-form'){const data=await api(urls.profileSettings,{method:'POST',body:formObject(form)});state.user=data.user||state.user;$('#signed-user-name').textContent=state.user.name||'';toast(data.message||text('Profile updated.','প্রোফাইল আপডেট হয়েছে।'));return;}
      if(form.id==='personal-settings-form'){const d=formObject(form);setTheme($('#appearance-select')?.value||'system');const data=await api(urls.personalSettings,{method:'POST',body:{language:d.language}});toast(data.message||text('Preferences saved.','পছন্দ সংরক্ষিত।'));if(d.language!==app.dataset.language)location.reload();return;}
      if(form.id==='pin-form'){const data=await api(urls.pinSettings,{method:'POST',body:formObject(form)});form.reset();toast(data.message||text('PIN changed.','পিন পরিবর্তন হয়েছে।'));return;}
      if(form.id==='config-form'){const d=formObject(form);['rate_based_mode','supplier_rate_enabled','wallet_rate_enabled'].forEach(k=>d[k]=form.elements[k]?.checked?1:0);const data=await api(urls.config,{method:'POST',body:d});state.settings={...state.settings,...(data.settings||{})};$$('.brand-name').forEach(el=>el.textContent=state.settings.app_name||'SAFA');toast(data.message||text('Configuration saved.','কনফিগারেশন সংরক্ষিত।'));return;}
      if(form.id==='logo-form'){const data=await api(urls.logo,{method:'POST',body:new FormData(form)});const src=data.app_logo_path||data.app_logo_url||data.url;if(src){$$('.brand-logo').forEach(el=>el.src=src);if($('#settings-logo-preview'))$('#settings-logo-preview').src=src;}form.reset();toast(data.message||text('Logo uploaded.','লোগো আপলোড হয়েছে।'));return;}
      if(form.id==='user-form'){const d=formObject(form);d.is_activated=form.elements.is_activated.checked;await api(urls.users,{method:'POST',body:d});form.reset();form.elements.is_activated.checked=true;await loadUsers();toast(text('User created.','ইউজার তৈরি হয়েছে।'));return;}
      if(form.id==='user-edit-form'){const d=formObject(form);if(!d.pin)delete d.pin;await api(baseWithId(urls.users,form.dataset.id),{method:'PATCH',body:d});closeModal();await loadUsers();return;}
    } catch(error){toast(error.message,true);}
  });

  $('#account-select')?.addEventListener('change',async e=>{if(!e.target.value)return;try{await api(urls.accountSwitch,{method:'POST',body:{account_id:Number(e.target.value)}});app.dataset.activeAccount=e.target.value;$('#global-message')?.classList.add('hidden');closeSubpage();await loadWorkspace(true);}catch(error){toast(error.message,true);}});

  if (app.dataset.activeAccount) loadWorkspace(true); else renderAll();
})();
