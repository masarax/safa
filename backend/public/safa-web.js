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
  let copy = {};
  try { copy = JSON.parse(app.dataset.webCopy || '{}'); } catch (_) { copy = {}; }
  const t = key => String(copy[key] ?? key);
  const tf = (key, vars = {}) => Object.entries(vars).reduce((value, [name, replacement]) => value.replaceAll(`{${name}}`, String(replacement)), t(key));
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
    if (!url) throw new Error(t('runtime_this_action_is_not_available_for_your_role'));
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
      if (!quiet) toast(t('runtime_data_refreshed'));
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
      [t('runtime_customers'), state.customers.length, ''], [t('runtime_suppliers'), state.suppliers.length, ''],
      [t('runtime_customer_due'), `${money(customerDue)} ${foreign()}`, customerDue>.05?'danger':''],
      [t('runtime_wallet_stock'), `${money(wallet)} ${local()}`, '']
    ].map(([label,value,klass])=>`<article class="metric-card ${klass}"><small>${esc(label)}</small><strong>${esc(value)}</strong></article>`).join('');
    const overview = $('#dashboard-overview'); if (!overview) return;
    overview.innerHTML = `
      <div class="overview-item"><small>${t('runtime_transactions')}</small><strong>${state.transactions.length}</strong></div>
      <div class="overview-item"><small>${t('runtime_delivered')}</small><strong>${delivered}</strong></div>
      <div class="overview-item"><small>${t('runtime_supplier_ledger_net')}</small><strong>${money(Math.abs(supplierNet))} ${local()}</strong></div>`;
  }

  function customerCard(c) {
    const txs = customerTransactions(c.id), due = customerBalance(c.id), spent = txs.reduce((s,t)=>s+num(t.amount_sar),0), bdt = txs.reduce((s,t)=>s+num(t.amount_bdt),0);
    const balanceClass = due>.05?'due':due<-.05?'advance':'';
    const label = due>.05?t('runtime_total_due'):due<-.05?t('runtime_customer_owed'):t('runtime_settled');
    return `<article class="entity-card clickable" data-open-customer="${c.id}"><div class="entity-head"><div class="entity-identity"><span class="avatar">${esc((c.name||'?').slice(0,1).toUpperCase())}</span><div><strong>${esc(c.name)}</strong><small>${esc(c.phone || '')}</small></div></div><div class="balance-box ${balanceClass}"><small>${label}</small><strong>${money(Math.abs(due))} ${foreign()}</strong></div></div><div class="entity-stats"><div><small>${t('runtime_total_volume')}</small><strong>${money(spent)} ${foreign()}</strong></div><div><small>${t('runtime_paid_out')}</small><strong>${money(bdt)} ${local()}</strong></div><div><small>${t('runtime_tx_count')}</small><strong>${txs.length}</strong></div></div></article>`;
  }
  function renderCustomers() {
    const list = $('#customers-list'); if (!list) return;
    const q = ($('#customer-search')?.value || '').trim().toLowerCase(), filter = $('#customer-filter')?.value || 'all', sort = $('#customer-sort')?.value || 'newest';
    let rows = state.customers.filter(c => !q || [c.name,c.phone,c.address].some(v=>String(v||'').toLowerCase().includes(q)));
    if (filter==='due') rows=rows.filter(c=>customerBalance(c.id)>.05); if(filter==='advance') rows=rows.filter(c=>customerBalance(c.id)<-.05);
    rows.sort((a,b)=>sort==='oldest'?Number(a.timestamp)-Number(b.timestamp):sort==='name'?String(a.name).localeCompare(String(b.name)):sort==='due'?customerBalance(b.id)-customerBalance(a.id):Number(b.timestamp)-Number(a.timestamp));
    list.innerHTML = rows.length ? rows.map(customerCard).join('') : `<div class="empty-state"><strong>${t('runtime_no_customers_found')}</strong>${t('runtime_add_a_customer_or_change_the_current_filter')}</div>`;
  }

  function supplierCard(s) {
    const deps=supplierDeposits(s.id), balance=supplierBalance(s.id), sar=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_sar),0), acquired=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_bdt),0);
    const klass=balance>.05?'advance':balance<-.05?'due':''; const label=balance>.05?t('runtime_receivable'):balance<-.05?t('runtime_payable'):t('runtime_no_due');
    return `<article class="entity-card clickable" data-open-supplier="${s.id}"><div class="entity-head"><div class="entity-identity"><span class="avatar">${esc((s.name||'?').slice(0,1).toUpperCase())}</span><div><strong>${esc(s.name)}</strong><small>${esc(s.phone||'')}</small></div></div><div class="balance-box ${klass}"><small>${label}</small><strong>${money(Math.abs(balance))} ${local()}</strong></div></div><div class="entity-stats"><div><small>${t('runtime_riyal_deposited')}</small><strong>${money(sar)} ${foreign()}</strong></div><div><small>${t('runtime_taka_buy')}</small><strong>${money(acquired)} ${local()}</strong></div><div><small>${t('runtime_records')}</small><strong>${deps.length}</strong></div></div></article>`;
  }
  function renderSuppliers() {
    const list=$('#suppliers-list'); if(!list)return; const q=($('#supplier-search')?.value||'').trim().toLowerCase(), filter=$('#supplier-filter')?.value||'all', sort=$('#supplier-sort')?.value||'newest';
    let rows=state.suppliers.filter(s=>!q||[s.name,s.phone,s.address].some(v=>String(v||'').toLowerCase().includes(q)));
    if(filter==='receivable')rows=rows.filter(s=>supplierBalance(s.id)>.05);if(filter==='payable')rows=rows.filter(s=>supplierBalance(s.id)<-.05);
    rows.sort((a,b)=>sort==='oldest'?Number(a.timestamp)-Number(b.timestamp):sort==='name'?String(a.name).localeCompare(String(b.name)):sort==='balance'?Math.abs(supplierBalance(b.id))-Math.abs(supplierBalance(a.id)):Number(b.timestamp)-Number(a.timestamp));
    list.innerHTML=rows.length?rows.map(supplierCard).join(''):`<div class="empty-state"><strong>${t('runtime_no_suppliers_found')}</strong></div>`;
  }

  function renderWallet() {
    const list=$('#wallet-ledgers-list'), summary=$('#wallet-summary'); if(!list||!summary)return;
    const total=state.wallet_batches.reduce((s,b)=>s+num(b.remaining_bdt),0), initial=state.wallet_batches.reduce((s,b)=>s+num(b.initial_bdt),0), active=state.wallet_batches.filter(b=>num(b.remaining_bdt)>.05).length;
    summary.innerHTML=`<article class="metric-card"><small>${t('runtime_total_stock')}</small><strong>${money(total)} ${local()}</strong></article><article class="metric-card"><small>${t('runtime_total_deposited')}</small><strong>${money(initial)} ${local()}</strong></article><article class="metric-card"><small>${t('runtime_active_batches')}</small><strong>${active}</strong></article>`;
    list.innerHTML=state.wallet_ledgers.length?state.wallet_ledgers.map(ledger=>{
      const batches=walletBatches(ledger.id), balance=walletBalance(ledger.id), init=batches.reduce((s,b)=>s+num(b.initial_bdt),0), spent=init-balance, activeB=batches.filter(b=>num(b.remaining_bdt)>.05), weighted=activeB.reduce((s,b)=>s+num(b.remaining_bdt)*num(b.rate),0)/(balance||1), expanded=state.walletExpanded.has(Number(ledger.id));
      return `<article class="entity-card"><div class="entity-head"><div class="entity-identity"><span class="avatar">▰</span><div><strong>${esc(ledger.name)}</strong><small>${activeB.length} ${t('runtime_active_subaccounts')}</small></div></div><div class="balance-box"><small>${t('runtime_available')}</small><strong>${money(balance)} ${local()}</strong></div></div><div class="entity-stats"><div><small>${t('runtime_initial')}</small><strong>${money(init)}</strong></div><div><small>${t('runtime_spent')}</small><strong>${money(spent)}</strong></div><div><small>${t('runtime_weighted_rate')}</small><strong>${money(weighted,4)}</strong></div></div><div class="entity-actions"><button class="mini-button" data-toggle-wallet="${ledger.id}">${expanded?t('runtime_hide_stock'):t('runtime_show_stock')}</button><button class="mini-button primary" data-wallet-deposit="${ledger.id}">＋ ${t('runtime_deposit')}</button><button class="mini-button blue" data-wallet-withdraw="${ledger.id}">− ${t('runtime_withdraw')}</button><button class="mini-button" data-wallet-rename="${ledger.id}">${t('runtime_rename')}</button><button class="mini-button danger" data-wallet-delete="${ledger.id}" ${balance>.05?'disabled':''}>${t('runtime_delete')}</button></div>${expanded?`<div class="section-label"><h3>${t('runtime_stock_batches')}</h3></div>${activeB.length?activeB.map(b=>`<div class="ledger-row"><div class="ledger-summary"><div class="ledger-main"><strong>${money(b.remaining_bdt)} ${local()}</strong><small>${dateText(b.timestamp)} · ${esc(b.notes||'')}</small></div><div class="ledger-amount"><strong>${t('runtime_rate')} ${money(b.rate,4)}</strong><small>${t('runtime_initial')} ${money(b.initial_bdt)}</small></div></div></div>`).join(''):`<div class="empty-state">${t('runtime_no_active_stock')}</div>`}`:''}</article>`;
    }).join(''):`<div class="empty-state"><strong>${t('runtime_no_wallet_ledgers')}</strong></div>`;
  }

  function renderExpenses() {
    const list=$('#expenses-list'), summary=$('#expenses-summary'); if(!list||!summary)return;
    const expense=state.expenses.filter(x=>Boolean(Number(x.is_expense))).reduce((s,x)=>s+num(x.amount),0), income=state.expenses.filter(x=>!Boolean(Number(x.is_expense))).reduce((s,x)=>s+num(x.amount),0);
    summary.innerHTML=`<article class="metric-card danger"><small>${t('runtime_expense')}</small><strong>${money(expense)}</strong></article><article class="metric-card"><small>${t('runtime_income')}</small><strong>${money(income)}</strong></article><article class="metric-card"><small>${t('runtime_net')}</small><strong>${money(income-expense)}</strong></article>`;
    list.innerHTML=state.expenses.length?state.expenses.map(x=>`<article class="entity-card"><div class="entity-head"><div class="entity-identity"><span class="avatar">${Number(x.is_expense)?'−':'+'}</span><div><strong>${esc(x.title)}</strong><small>${esc(x.category||'General')} · ${dateText(x.timestamp)}</small></div></div><div class="balance-box ${Number(x.is_expense)?'due':'advance'}"><small>${Number(x.is_expense)?t('runtime_expense'):t('runtime_income')}</small><strong>${money(x.amount)} ${esc(x.currency||local())}</strong></div></div><div class="entity-actions"><button class="mini-button" data-expense-edit="${x.id}">${t('runtime_edit')}</button><button class="mini-button danger" data-expense-delete="${x.id}">${t('runtime_delete')}</button></div></article>`).join(''):`<div class="empty-state"><strong>${t('runtime_no_daily_entries')}</strong></div>`;
  }

  function transactionRow(tx, customerDue) {
    const status=statusOf(tx), due=num(tx.amount_sar)-num(tx.sar_collected), expanded=state.expanded.has(`tx-${tx.id}`), dueOnly=num(tx.amount_sar)<=.05 && num(tx.sar_collected)>.05, advance=num(tx.amount_sar)<=.05 && num(tx.sar_collected)<-.05;
    const title=advance?t('runtime_advance_return'):dueOnly?t('runtime_due_collection'):(tx.receiver_name||t('runtime_recipient'));
    const amount=advance?`${money(Math.abs(num(tx.sar_collected)))} ${foreign()}`:dueOnly?`${money(tx.sar_collected)} ${foreign()}`:`${money(tx.amount_sar)} ${foreign()}`;
    return `<div class="ledger-row"><div class="ledger-summary" data-expand="tx-${tx.id}"><div class="ledger-main"><strong>${esc(title)} <span class="status-pill ${status.toLowerCase()}">${esc(status)}</span></strong><small>${timeText(tx.timestamp)} · ${esc(tx.receiver_account_type||'')}</small>${Math.abs(due)>.05&&!dueOnly&&!advance?`<small>${due>0?t('runtime_uncollected'):t('runtime_overpaid')}: ${money(Math.abs(due))} ${foreign()}</small>`:''}</div><div class="ledger-amount"><strong>${amount}</strong><small>${!dueOnly&&!advance?`${t('runtime_rate')} ${money(tx.customer_rate,4)}`:''}</small></div></div>${expanded?`<div class="ledger-detail"><div class="detail-line"><span>${t('runtime_wallet_pool')}</span><span>${esc(walletNameForTx(tx))}</span></div><div class="detail-line"><span>${t('runtime_payout')}</span><span>${money(tx.amount_bdt)} ${local()}</span></div><div class="detail-line"><span>${t('runtime_collected')}</span><span>${money(tx.sar_collected)} / ${money(tx.amount_sar)} ${foreign()}</span></div><div class="detail-line"><span>${t('runtime_account')}</span><span>${esc(tx.receiver_account_no||'')}</span></div>${tx.notes?`<div class="detail-line"><span>${t('runtime_notes')}</span><span>${esc(tx.notes)}</span></div>`:''}<div class="row-buttons"><button class="mini-button blue" data-share-transaction="${tx.id}">${t('runtime_share')}</button>${ability.editTx?`<button class="mini-button" data-edit-transaction="${tx.id}">${t('runtime_edit')}</button>`:''}${ability.deleteTx?`<button class="mini-button danger" data-delete-transaction="${tx.id}">${t('runtime_delete')}</button>`:''}${ability.editTx&&status!=='Delivered'&&status!=='Cancelled'?`<button class="mini-button primary" data-status-transaction="${tx.id}" data-status="Delivered">${t('runtime_deliver')}</button>`:''}${ability.editTx&&status==='Pending'?`<button class="mini-button danger" data-status-transaction="${tx.id}" data-status="Cancelled">${t('runtime_cancel')}</button>`:''}${ability.editTx&&status==='Cancelled'?`<button class="mini-button" data-status-transaction="${tx.id}" data-status="Pending">${t('runtime_pending')}</button>`:''}</div></div>`:''}</div>`;
  }
  function walletNameForTx(tx) { const batch=state.wallet_batches.find(b=>Number(b.id)===Number(tx.wallet_batch_id)); const ledger=batch&&state.wallet_ledgers.find(l=>Number(l.id)===Number(batch.ledger_id)); return num(tx.amount_sar)<=.05?t('runtime_n_a_due_advance'):ledger?.name||t('runtime_unknown_wallet'); }

  function renderCustomerProfile(id, tab='transactions') {
    const c=state.customers.find(x=>Number(x.id)===Number(id)); if(!c){closeSubpage();return;} state.profile={type:'customer',id:Number(id),tab};
    const txs=customerTransactions(id), due=customerBalance(id), totalSar=txs.reduce((s,x)=>s+num(x.amount_sar),0), totalBdt=txs.reduce((s,x)=>s+num(x.amount_bdt),0), groups=groupedByDate(txs);
    const groupsHtml=[...groups.entries()].map(([date,items])=>`<div class="date-group"><div class="date-heading"><span>${esc(date)}</span><span>${money(items.reduce((s,x)=>s+num(x.amount_bdt),0))} ${local()}</span></div>${items.map(x=>transactionRow(x,due)).join('')}</div>`).join('');
    openSubpage(`<div class="subpage-toolbar"><button class="icon-button" data-close-subpage>←</button><div><p class="eyebrow">${t('runtime_customer_profile')}</p><h2>${esc(c.name)}</h2></div></div><div class="profile-hero"><div class="profile-person"><span class="avatar">${esc((c.name||'?')[0])}</span><div><h2>${esc(c.name)}</h2><p>${esc(c.phone||'')} · ${esc(c.address||'')}</p></div></div><div class="profile-balance"><small>${due>.05?t('runtime_total_due'):due<-.05?t('runtime_customer_owed'):t('runtime_settled_32f1fb2a')}</small><strong>${money(Math.abs(due))} ${foreign()}</strong></div></div>${ability.addTx?`<button class="primary-button" data-new-customer-transaction="${c.id}">＋ ${t('runtime_new_transaction')}</button>`:''}<div class="metric-grid"><article class="metric-card"><small>${t('runtime_total_volume_e77c6152')}</small><strong>${money(totalSar)} ${foreign()}</strong></article><article class="metric-card"><small>${t('runtime_paid_out_49665b36')}</small><strong>${money(totalBdt)} ${local()}</strong></article><article class="metric-card"><small>${t('runtime_transactions')}</small><strong>${txs.length}</strong></article></div><div class="profile-tabs"><button class="profile-tab ${tab==='transactions'?'active':''}" data-profile-tab="transactions">${t('runtime_transactions')}</button><button class="profile-tab ${tab==='info'?'active':''}" data-profile-tab="info">${t('runtime_customer_info')}</button></div><div class="profile-panel ${tab==='transactions'?'active':''}">${txs.length?groupsHtml:`<div class="empty-state">${t('runtime_no_transactions_recorded_for_this_customer')}</div>`}</div><div class="profile-panel ${tab==='info'?'active':''}"><form id="customer-profile-form" class="surface-card stack-form" data-customer-id="${c.id}"><label><span>${t('runtime_customer_name')}</span><input name="name" value="${esc(c.name)}" required></label><label><span>${t('runtime_phone')}</span><input name="phone" value="${esc(c.phone||'')}" inputmode="tel"></label><label><span>${t('runtime_address')}</span><input name="address" value="${esc(c.address||'')}"></label><button class="primary-button" type="submit">${t('runtime_save_profile')}</button><button class="danger-button" type="button" data-delete-customer="${c.id}">${t('runtime_delete_customer_profile')}</button></form></div>`);
  }

  function supplierFundRow(dep) {
    const expanded=state.expanded.has(`dep-${dep.id}`), purchase=['SAR_GIVEN','SAR_DEPOSIT'].includes(dep.transaction_type), settlement=['SAR_RECEIVED','SAR_SETTLEMENT'].includes(dep.transaction_type), due=num(dep.amount_bdt)-num(dep.paid_bdt), batch=state.wallet_batches.find(b=>Number(b.supplier_deposit_id)===Number(dep.id)), ledger=batch&&state.wallet_ledgers.find(l=>Number(l.id)===Number(batch.ledger_id));
    const label=settlement?t('runtime_dues_receivable_settlement'):t('runtime_riyal_purchase');
    return `<div class="ledger-row"><div class="ledger-summary" data-expand="dep-${dep.id}"><div class="ledger-main"><strong>${label}</strong><small>${timeText(dep.timestamp)}${ledger?` · ${esc(ledger.name)}`:''}</small>${dep.notes?`<small>${esc(dep.notes)}</small>`:''}</div><div class="ledger-amount"><strong>${purchase?'+':''}${money(dep.amount_bdt)} ${local()}</strong><small>${money(dep.amount_sar)} ${foreign()} · ${t('runtime_rate')} ${money(dep.rate,4)}</small></div></div>${expanded?`<div class="ledger-detail"><div class="detail-line"><span>${t('runtime_total_bdt')}</span><span>${money(dep.amount_bdt)} ${local()}</span></div><div class="detail-line"><span>${t('runtime_paid_received_bdt')}</span><span>${money(dep.paid_bdt)} ${local()}</span></div><div class="detail-line"><span>${t('runtime_difference')}</span><span>${money(Math.abs(due))} ${local()}</span></div>${ability.wallet?`<div class="row-buttons"><button class="mini-button" data-edit-supplier-fund="${dep.id}">${t('runtime_edit')}</button><button class="mini-button danger" data-delete-supplier-fund="${dep.id}">${t('runtime_delete')}</button></div>`:''}</div>`:''}</div>`;
  }

  function renderSupplierProfile(id, tab='transactions') {
    const s=state.suppliers.find(x=>Number(x.id)===Number(id)); if(!s){closeSubpage();return;} state.profile={type:'supplier',id:Number(id),tab};
    const deps=supplierDeposits(id), balance=supplierBalance(id), groups=groupedByDate(deps), totalSar=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_sar),0), acquired=deps.filter(d=>['SAR_GIVEN','SAR_DEPOSIT'].includes(d.transaction_type)).reduce((x,d)=>x+num(d.amount_bdt),0), paid=deps.reduce((x,d)=>x+num(d.paid_bdt),0);
    const groupsHtml=[...groups.entries()].map(([date,items])=>`<div class="date-group"><div class="date-heading"><span>${esc(date)}</span><span>${t('runtime_records')} ${items.length}</span></div>${items.map(supplierFundRow).join('')}</div>`).join('');
    openSubpage(`<div class="subpage-toolbar"><button class="icon-button" data-close-subpage>←</button><div><p class="eyebrow">${t('runtime_supplier_pool_profile')}</p><h2>${esc(s.name)}</h2></div></div><div class="profile-hero"><div class="profile-person"><span class="avatar">${esc((s.name||'?')[0])}</span><div><h2>${esc(s.name)}</h2><p>${esc(s.phone||'')} · ${esc(s.address||'')}</p></div></div><div class="profile-balance"><small>${balance>.05?t('runtime_receivable_bdt'):balance<-.05?t('runtime_payable_bdt'):t('runtime_no_due')}</small><strong>${money(Math.abs(balance))} ${local()}</strong></div></div>${ability.wallet?`<button class="primary-button" data-new-supplier-fund="${s.id}">＋ ${t('runtime_new_transaction')}</button>`:''}<div class="metric-grid"><article class="metric-card"><small>${t('runtime_riyal_deposited_c61b0962')}</small><strong>${money(totalSar)} ${foreign()}</strong></article><article class="metric-card"><small>${t('runtime_taka_buy_caecf524')}</small><strong>${money(acquired)} ${local()}</strong></article><article class="metric-card"><small>${t('runtime_paid_received')}</small><strong>${money(paid)} ${local()}</strong></article></div><div class="profile-tabs"><button class="profile-tab ${tab==='transactions'?'active':''}" data-profile-tab="transactions">${t('runtime_transactions_6288df1c')}</button><button class="profile-tab ${tab==='info'?'active':''}" data-profile-tab="info">${t('runtime_profile_info')}</button></div><div class="profile-panel ${tab==='transactions'?'active':''}"><div class="section-label"><h3>${t('runtime_supplier_fund_registry_audit')}</h3><small>${deps.length} ${t('runtime_records_7b66fcb9')}</small></div>${deps.length?groupsHtml:`<div class="empty-state">${t('runtime_no_fund_records_found')}</div>`}</div><div class="profile-panel ${tab==='info'?'active':''}"><form id="supplier-profile-form" class="surface-card stack-form" data-supplier-id="${s.id}"><label><span>${t('runtime_supplier_name')}</span><input name="name" value="${esc(s.name)}" required></label><label><span>${t('runtime_phone')}</span><input name="phone" value="${esc(s.phone||'')}"></label><label><span>${t('runtime_office_address')}</span><input name="address" value="${esc(s.address||'')}"></label><button class="primary-button" type="submit">${t('runtime_save_profile')}</button><button class="danger-button" type="button" data-delete-supplier="${s.id}">${t('runtime_delete_supplier_profile')}</button></form></div>`);
  }

  function openCustomerTransactionChoice(customerId) {
    const due=customerBalance(customerId); if(Math.abs(due)<=.05){startCustomerFlow(customerId,'sale');return;}
    openModal(`<div class="subpage-toolbar"><button class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${t('runtime_customer_transaction')}</p><h2>${t('runtime_select_transaction_type')}</h2></div></div><div class="choice-grid"><button class="choice-card primary" data-customer-flow="sale" data-customer-id="${customerId}"><strong>${t('runtime_new_sale')}</strong><small>${t('runtime_sell_new_currency_previous_dues_can_also_be_settled_in_s')}</small></button>${due>.05?`<button class="choice-card due" data-customer-flow="due" data-customer-id="${customerId}"><strong>${t('runtime_due_collection')}</strong><small>${tf('runtime_collect_previous_outstanding_dues_p0_p1', {p0: money(due), p1: foreign()})}</small></button>`:''}${due<-.05?`<button class="choice-card advance" data-customer-flow="advance" data-customer-id="${customerId}"><strong>${t('runtime_advance_return')}</strong><small>${tf('runtime_return_customer_s_advance_p0_p1', {p0: money(Math.abs(due)), p1: foreign()})}</small></button>`:''}</div>`);
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
    const header=`<div class="subpage-toolbar"><button class="icon-button" data-flow-cancel>←</button><div><p class="eyebrow">${esc(c.name)}</p><h2>${sale?(step===1?t('runtime_step_1_details'):t('runtime_step_2_wallet_rates')):f.mode==='due'?t('runtime_due_collection'):t('runtime_advance_return')}</h2></div></div><div class="stepper"><span class="step-segment active"></span><span class="step-segment ${step===2?'active':''}"></span></div>`;
    if(step===1){
      openSubpage(`${header}<form id="customer-flow-step1" class="flow-card stack-form"><label><span>${tf('runtime_amount_p0', {p0: foreign()})}</span><input name="amount" value="${esc(f.amount)}" inputmode="decimal" placeholder="0.00" required autofocus></label><label><span>${t('runtime_transaction_date')}</span><input name="date" type="date" max="${todayInput()}" value="${esc(f.date)}" required></label><label><span>${t('runtime_notes_description')}</span><textarea name="notes">${esc(f.notes)}</textarea></label><button class="primary-button" type="submit">${t('runtime_next_step')} →</button></form>`); return;
    }
    const active=activeBatches(); const adjustmentLabel=f.due>.05?t('runtime_previous_due_collection'):f.due<-.05?t('runtime_return_advanced_balance'):'';
    const adjustmentKind=f.due<-.05?'advance':'due';
    openSubpage(`${header}<form id="customer-flow-step2" class="stack-form"><div class="flow-card"><h3>${t('runtime_select_payout_method')}</h3><div class="payment-methods">${paymentMethods.map(m=>`<button type="button" class="choice-chip ${f.payment===m?'active':''}" data-payment-method="${esc(m)}">${esc(m)}</button>`).join('')}</div>${f.payment!=='Cash'?`<label><span>${f.payment==='Bank Transfer'?t('runtime_bank_account_number'):esc(f.payment)+' '+t('runtime_number')}</span><input name="account" value="${esc(f.account)}" required></label>`:''}</div>${sale?`<div class="flow-card"><h3>${t('runtime_select_wallet_account')}</h3><label><span>${t('runtime_wallet_stock')}</span><select name="wallet_batch_id" required><option value="">${t('runtime_select_wallet_stock')}</option>${active.map(b=>{const l=state.wallet_ledgers.find(x=>Number(x.id)===Number(b.ledger_id));return `<option value="${b.id}" ${Number(f.walletBatchId)===Number(b.id)?'selected':''}>${esc(l?.name||'Wallet')} · ${money(b.remaining_bdt)} ${local()} · ${t('runtime_rate')} ${money(b.rate,4)}</option>`}).join('')}</select></label>${selected?`<div class="info-box">${t('runtime_wallet_cost_rate_auto')}: ${money(selected.rate,4)} · ${t('runtime_stock')}: ${money(selected.remaining_bdt)} ${local()}</div>`:''}</div>${Math.abs(f.due)>.05?`<div class="flow-card"><h3>${adjustmentLabel}</h3><div class="${f.due>.05?'warning-box':'info-box'}">${t('runtime_current_balance')}: ${money(Math.abs(f.due))} ${foreign()}</div><label><span>${f.due>.05?t('runtime_due_collected_amount'):t('runtime_advance_returned_amount')} (${foreign()})</span><input name="due_adjustment" value="${esc(f.dueAdjustment)}" inputmode="decimal" placeholder="0.00"></label></div>`:''}<div class="flow-card"><h3>${t('runtime_sales_price_riyal_summary')}</h3><div class="inline-edit"><label><span>${t('runtime_selling_rate')} ${local()}</span><input name="customer_rate" value="${esc(f.customerRate)}" inputmode="decimal" ${state.settings.rate_based_mode===false?'readonly':''} required></label><label><span>${t('runtime_amount')} ${foreign()}</span><input value="${esc(f.amount)}" readonly></label><label><span>${t('runtime_received')} ${foreign()}</span><input name="sar_collected" value="${esc(f.sarCollected||f.amount)}" inputmode="decimal" required></label><label><span>${t('runtime_disbursed')} ${local()}</span><input value="${money(payout)}" readonly></label></div></div><div class="flow-card"><h3>${t('runtime_review_details')}</h3><div class="summary-box"><div class="summary-line"><span>${t('runtime_sar_amount')}</span><strong>${money(f.amount)} ${foreign()}</strong></div><div class="summary-line"><span>${t('runtime_beneficiary_payout')}</span><strong>${money(payout)} ${local()}</strong></div><div class="summary-line"><span>${t('runtime_payout_channel')}</span><strong>${esc(f.payment)}</strong></div><div class="summary-line"><span>${t('runtime_customer_rate')}</span><strong>${money(f.customerRate,4)}</strong></div></div></div>`:`<div class="flow-card"><h3>${f.mode==='due'?t('runtime_collect_previous_outstanding_dues'):t('runtime_return_advanced_balance')}</h3><div class="${f.mode==='due'?'warning-box':'info-box'}">${t('runtime_available_balance')}: ${money(Math.abs(f.due))} ${foreign()}</div><label><span>${t('runtime_amount')} ${foreign()}</span><input name="adjustment_amount" value="${esc(f.dueAdjustment)}" inputmode="decimal" required autofocus></label><label><span>${t('runtime_transaction_date')}</span><input name="date" type="date" max="${todayInput()}" value="${esc(f.date)}" required></label></div>`}<div class="flow-actions"><button type="button" class="secondary-button" data-flow-back>${sale?t('runtime_back'):t('runtime_cancel')}</button><button class="primary-button" type="submit">✓ ${t('runtime_create_transaction')}</button></div></form>`);
  }

  function openTransactionEdit(id) {
    const tx=state.transactions.find(x=>Number(x.id)===Number(id)); if(!tx)return; const active=activeBatches();
    openModal(`<form id="transaction-edit-form" class="stack-form" data-id="${tx.id}"><div class="subpage-toolbar"><button class="icon-button" type="button" data-close-modal>×</button><div><p class="eyebrow">${t('runtime_transaction')}</p><h2>${t('runtime_edit_transaction')}</h2></div></div><div class="inline-edit"><label><span>${t('runtime_amount')} ${foreign()}</span><input name="amount_sar" value="${esc(tx.amount_sar)}" inputmode="decimal" required></label><label><span>${t('runtime_customer_rate_403f7109')}</span><input name="customer_rate" value="${esc(tx.customer_rate)}" inputmode="decimal" required></label><label><span>${t('runtime_supplier_rate')}</span><input name="supplier_rate" value="${esc(tx.supplier_rate)}" inputmode="decimal"></label><label><span>${t('runtime_collected_f80fdf98')} ${foreign()}</span><input name="sar_collected" value="${esc(tx.sar_collected)}" inputmode="decimal" required></label><label><span>${t('runtime_disbursed')} ${local()}</span><input name="bdt_disbursed" value="${esc(tx.bdt_disbursed)}" inputmode="decimal"></label><label><span>${t('runtime_status')}</span><select name="status">${['Pending','Delivered','Cancelled'].map(s=>`<option ${statusOf(tx)===s?'selected':''}>${s}</option>`).join('')}</select></label></div><label><span>${t('runtime_wallet_stock_b8fb6787')}</span><select name="wallet_batch_id"><option value="">N/A</option>${active.map(b=>`<option value="${b.id}" ${Number(tx.wallet_batch_id)===Number(b.id)?'selected':''}>${esc(state.wallet_ledgers.find(l=>Number(l.id)===Number(b.ledger_id))?.name||'Wallet')} · ${money(b.remaining_bdt)} · ${money(b.rate,4)}</option>`).join('')}</select></label><label><span>${t('runtime_payout_method')}</span><input name="receiver_account_type" value="${esc(tx.receiver_account_type||'Cash')}" required></label><label><span>${t('runtime_account_number')}</span><input name="receiver_account_no" value="${esc(tx.receiver_account_no||'')}"></label><label><span>${t('runtime_receiver')}</span><input name="receiver_name" value="${esc(tx.receiver_name||'')}"></label><label><span>${t('runtime_phone_c6099f45')}</span><input name="receiver_phone" value="${esc(tx.receiver_phone||'')}"></label><label><span>${t('runtime_notes')}</span><textarea name="notes">${esc(tx.notes||'')}</textarea></label><button class="primary-button" type="submit">${t('runtime_save_transaction')}</button></form>`);
  }

  function openReceipt(id) {
    const tx=state.transactions.find(x=>Number(x.id)===Number(id)); if(!tx)return; const c=state.customers.find(x=>Number(x.id)===Number(tx.customer_id));
    openModal(`<div class="subpage-toolbar"><button class="icon-button" data-close-modal>×</button><div><p class="eyebrow">SAFA</p><h2>${t('runtime_transaction_confirmation')}</h2></div></div><div class="summary-box"><div class="summary-line"><span>${t('runtime_customer')}</span><strong>${esc(c?.name||'')}</strong></div><div class="summary-line"><span>${t('runtime_amount')}</span><strong>${money(tx.amount_sar)} ${foreign()}</strong></div><div class="summary-line"><span>${t('runtime_rate')}</span><strong>${money(tx.customer_rate,4)}</strong></div><div class="summary-line"><span>${t('runtime_collected_f80fdf98')}</span><strong>${money(tx.sar_collected)} ${foreign()}</strong></div><div class="summary-line"><span>${t('runtime_payout_a9934617')}</span><strong>${money(tx.amount_bdt)} ${local()}</strong></div><div class="summary-line"><span>${t('runtime_method')}</span><strong>${esc(tx.receiver_account_type||'')}</strong></div><div class="summary-line"><span>${t('runtime_date')}</span><strong>${dateText(tx.timestamp)} ${timeText(tx.timestamp)}</strong></div></div><button class="secondary-button" type="button" data-print-receipt>${t('runtime_print_share')}</button>`);
  }

  function openSupplierFundChoice(id) {
    const balance=supplierBalance(id); openModal(`<div class="subpage-toolbar"><button class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${t('runtime_supplier_transaction')}</p><h2>${t('runtime_select_transaction_type')}</h2></div></div><div class="choice-grid"><button class="choice-card primary" data-supplier-flow="purchase" data-supplier-id="${id}"><strong>${tf('runtime_new_p0_purchase', {p0: foreign()})}</strong><small>${t('runtime_purchase_new_funds_from_this_supplier_and_add_the_result')}</small></button>${Math.abs(balance)>.05?`<button class="choice-card ${balance<0?'due':'advance'}" data-supplier-flow="settlement" data-supplier-id="${id}"><strong>${balance<0?t('runtime_clear_supplier_due'):t('runtime_collect_receivable')}</strong><small>${tf('runtime_settle_the_current_p0_p1_balance_without_creating_wallet', {p0: money(Math.abs(balance)), p1: local()})}</small></button>`:''}</div>`);
  }
  function startSupplierFlow(id, mode, existing=null) {
    closeModal(); const dep=existing||{}; state.flow={kind:'supplier',supplierId:Number(id),fundId:existing?.id||null,mode,step:1,amount:dep.amount_sar||'',rate:dep.rate||'32.50',paid:dep.paid_bdt||'',date:existing?new Date(Number(dep.timestamp)*1000).toISOString().slice(0,10):todayInput(),notes:dep.notes||'',ledgerId:existing?String(state.wallet_batches.find(b=>Number(b.supplier_deposit_id)===Number(dep.id))?.ledger_id||''):''}; renderSupplierFlow();
  }
  function renderSupplierFlow() {
    const f=state.flow,s=state.suppliers.find(x=>Number(x.id)===f.supplierId);if(!s)return; const purchase=f.mode==='purchase', calculated=num(f.amount)*num(f.rate), step=f.step;
    const header=`<div class="subpage-toolbar"><button class="icon-button" data-flow-cancel>←</button><div><p class="eyebrow">${esc(s.name)}</p><h2>${step===1?t('runtime_step_1_details'):t('runtime_step_2_payment')}</h2></div></div><div class="stepper"><span class="step-segment active"></span><span class="step-segment ${step===2?'active':''}"></span></div>`;
    if(step===1){openSubpage(`${header}<form id="supplier-flow-step1" class="flow-card stack-form"><div class="${purchase?'success-box':'info-box'}">${purchase?t('runtime_new_fund_purchase_saving_this_record_will_create_linked'):t('runtime_settlement_this_record_changes_the_supplier_ledger_only')}</div><label><span>${tf('runtime_amount_p0_542a5d94', {p0: foreign()})}</span><input name="amount" value="${esc(f.amount)}" inputmode="decimal" required autofocus></label><label><span>${t('runtime_date')}</span><input name="date" type="date" max="${todayInput()}" value="${esc(f.date)}" required></label><label><span>${t('runtime_notes_description')}</span><textarea name="notes">${esc(f.notes)}</textarea></label><button class="primary-button" type="submit">${t('runtime_next_step_5b33385e')} →</button></form>`);return;}
    openSubpage(`${header}<form id="supplier-flow-step2" class="stack-form"><div class="flow-card"><label><span>${t('runtime_exchange_rate')}</span><input name="rate" value="${esc(f.rate)}" inputmode="decimal" ${state.settings.supplier_rate_enabled===false?'readonly':''} required></label><label><span>${purchase?tf('runtime_paid_received_p0', {p0: local()}):tf('runtime_settlement_value_p0', {p0: local()})}</span><input name="paid" value="${esc(f.paid||money(calculated))}" inputmode="decimal" required></label><div class="summary-box"><div class="summary-line"><span>${t('runtime_calculated_bdt')}</span><strong>${money(calculated)} ${local()}</strong></div><div class="summary-line"><span>${t('runtime_difference')}</span><strong>${money(Math.abs(calculated-num(f.paid||calculated)))} ${local()}</strong></div></div>${purchase?`<label><span>${t('runtime_wallet')}</span><select name="ledger_id" required><option value="">${t('runtime_select_wallet')}</option>${state.wallet_ledgers.map(l=>`<option value="${l.id}" ${Number(f.ledgerId)===Number(l.id)?'selected':''}>${esc(l.name)}</option>`).join('')}</select></label>`:''}</div><div class="flow-actions"><button type="button" class="secondary-button" data-flow-back>${t('runtime_back')}</button><button class="primary-button" type="submit">✓ ${f.fundId?t('runtime_save_record'):t('runtime_save_record_da02d282')}</button></div></form>`);
  }

  async function loadUsers() { if(!ability.users||!urls.users)return; try{const data=await api(urls.users);state.users=data.users||[];renderUsers();}catch(e){toast(e.message,true);} }
  function renderUsers(){const list=$('#users-list');if(!list)return;list.innerHTML=state.users.length?state.users.map(u=>`<article class="entity-card"><div class="entity-head"><div class="entity-identity"><span class="avatar">${esc((u.name||'?')[0])}</span><div><strong>${esc(u.name)}</strong><small>${esc(u.mobile||'')} · ${esc(u.role_label||u.role)}</small></div></div><span class="status-pill ${u.is_activated?'delivered':'cancelled'}">${u.is_activated?t('runtime_active'):t('runtime_inactive')}</span></div><div class="entity-actions"><button class="mini-button" data-user-edit="${u.id}">${t('runtime_edit')}</button><button class="mini-button ${u.is_activated?'danger':'primary'}" data-user-toggle="${u.id}">${u.is_activated?t('runtime_deactivate'):t('runtime_activate')}</button><button class="mini-button danger" data-user-delete="${u.id}">${t('runtime_delete')}</button></div></article>`).join(''):`<div class="empty-state">${t('runtime_no_manageable_users')}</div>`;}

  function openExpenseForm(existing=null){openModal(`<form id="expense-form" class="stack-form" data-id="${existing?.id||''}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${t('runtime_daily_entry')}</p><h2>${existing?t('runtime_edit_entry'):t('runtime_add_income_expense')}</h2></div></div><label><span>${t('runtime_title')}</span><input name="title" value="${esc(existing?.title||'')}" required></label><div class="inline-edit"><label><span>${t('runtime_amount')}</span><input name="amount" value="${esc(existing?.amount||'')}" inputmode="decimal" required></label><label><span>${t('runtime_currency')}</span><input name="currency" value="${esc(existing?.currency||local())}" required></label><label><span>${t('runtime_type')}</span><select name="is_expense"><option value="1" ${existing==null||Number(existing?.is_expense)?'selected':''}>${t('runtime_expense')}</option><option value="0" ${existing!=null&&!Number(existing?.is_expense)?'selected':''}>${t('runtime_income')}</option></select></label><label><span>${t('runtime_category')}</span><input name="category" value="${esc(existing?.category||'General')}"></label></div><button class="primary-button" type="submit">${t('runtime_save')}</button></form>`);}
  function openWalletLedgerForm(){openModal(`<form id="wallet-ledger-form" class="stack-form"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${t('runtime_wallet')}</p><h2>${t('runtime_new_ledger_register')}</h2></div></div><label><span>${t('runtime_ledger_name')}</span><input name="name" required autofocus></label><button class="primary-button" type="submit">${t('runtime_create_ledger')}</button></form>`);}
  function openWalletAction(id,type){const l=state.wallet_ledgers.find(x=>Number(x.id)===Number(id));if(!l)return;if(type==='deposit')openModal(`<form id="wallet-deposit-form" class="stack-form" data-ledger-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${esc(l.name)}</p><h2>${t('runtime_deposit_money')}</h2></div></div><label><span>${tf('runtime_amount_p0_542a5d94', {p0: local()})}</span><input name="amount_bdt" inputmode="decimal" required></label><label><span>${t('runtime_rate')}</span><input name="rate" value="32.0000" inputmode="decimal" required></label><label><span>${t('runtime_notes')}</span><input name="notes" value="Manual Capital Deposit"></label><button class="primary-button" type="submit">${t('runtime_deposit')}</button></form>`);else if(type==='withdraw')openModal(`<form id="wallet-withdraw-form" class="stack-form" data-ledger-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><p class="eyebrow">${esc(l.name)}</p><h2>${t('runtime_withdraw_money')}</h2></div></div><div class="info-box">${t('runtime_available')}: ${money(walletBalance(id))} ${local()} · ${t('runtime_oldest_active_stock_is_deducted_first')}</div><label><span>${tf('runtime_amount_p0_542a5d94', {p0: local()})}</span><input name="amount_bdt" inputmode="decimal" required></label><button class="primary-button" type="submit">${t('runtime_withdraw')}</button></form>`);else openModal(`<form id="wallet-rename-form" class="stack-form" data-ledger-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><h2>${t('runtime_rename_ledger')}</h2></div></div><label><span>${t('runtime_ledger_name_972f49ec')}</span><input name="name" value="${esc(l.name)}" required></label><button class="primary-button" type="submit">${t('runtime_save')}</button></form>`);}
  function openUserEdit(id){const u=state.users.find(x=>Number(x.id)===Number(id));if(!u)return;openModal(`<form id="user-edit-form" class="stack-form" data-id="${id}"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal>×</button><div><h2>${t('runtime_edit_user')}</h2></div></div><label><span>${t('runtime_name')}</span><input name="name" value="${esc(u.name)}" required></label><label><span>${t('runtime_mobile')}</span><input name="mobile" value="${esc(u.mobile||'')}" required></label><label><span>${t('runtime_email')}</span><input name="email" type="email" value="${esc(u.email||'')}"></label><label><span>${t('runtime_new_pin_optional')}</span><input name="pin" minlength="6" maxlength="6" inputmode="numeric"></label><button class="primary-button" type="submit">${t('runtime_save_user')}</button></form>`);}

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
      if(target.dataset.statusTransaction){await api(`${baseWithId(urls.transactions,target.dataset.statusTransaction)}/status`,{method:'PATCH',body:{status:target.dataset.status}});await loadWorkspace(true);toast(t('runtime_transaction_status_updated'));return;}
      if(target.dataset.deleteTransaction){if(!confirm(t('runtime_delete_this_transaction_wallet_stock_will_be_reconciled')))return;await api(baseWithId(urls.transactions,target.dataset.deleteTransaction),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);toast(t('runtime_transaction_deleted'));return;}
      if(target.dataset.newSupplierFund){openSupplierFundChoice(Number(target.dataset.newSupplierFund));return;} if(target.dataset.supplierFlow){startSupplierFlow(Number(target.dataset.supplierId),target.dataset.supplierFlow);return;}
      if(target.dataset.editSupplierFund){const dep=state.supplier_deposits.find(x=>Number(x.id)===Number(target.dataset.editSupplierFund));if(dep)startSupplierFlow(dep.supplier_id,['SAR_GIVEN','SAR_DEPOSIT'].includes(dep.transaction_type)?'purchase':'settlement',dep);return;}
      if(target.dataset.deleteSupplierFund){if(!confirm(t('runtime_delete_this_supplier_fund_record_and_its_linked_wallet_s')))return;await api(baseWithId(urls.supplierFunds,target.dataset.deleteSupplierFund),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);toast(t('runtime_fund_record_deleted'));return;}
      if(target.dataset.toggleWallet){const id=Number(target.dataset.toggleWallet);state.walletExpanded.has(id)?state.walletExpanded.delete(id):state.walletExpanded.add(id);renderWallet();return;}
      if(target.dataset.walletDeposit){openWalletAction(Number(target.dataset.walletDeposit),'deposit');return;}if(target.dataset.walletWithdraw){openWalletAction(Number(target.dataset.walletWithdraw),'withdraw');return;}if(target.dataset.walletRename){openWalletAction(Number(target.dataset.walletRename),'rename');return;}
      if(target.dataset.walletDelete){if(!confirm(t('runtime_delete_this_empty_wallet_ledger')))return;await api(baseWithId(urls.walletLedgers,target.dataset.walletDelete),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);toast(t('runtime_wallet_ledger_deleted'));return;}
      if(target.dataset.expenseEdit){openExpenseForm(state.expenses.find(x=>Number(x.id)===Number(target.dataset.expenseEdit)));return;} if(target.dataset.expenseDelete){if(!confirm(t('runtime_delete_this_entry')))return;await api(baseWithId(urls.expenses,target.dataset.expenseDelete),{method:'DELETE',body:{confirmed:true}});await loadWorkspace(true);return;}
      if(target.dataset.deleteCustomer){if(!confirm(t('runtime_delete_this_customer_profile')))return;await api(baseWithId(urls.customers,target.dataset.deleteCustomer),{method:'DELETE',body:{confirmed:true}});closeSubpage();await loadWorkspace(true);return;}
      if(target.dataset.deleteSupplier){if(!confirm(t('runtime_delete_this_supplier_profile')))return;await api(baseWithId(urls.suppliers,target.dataset.deleteSupplier),{method:'DELETE',body:{confirmed:true}});closeSubpage();await loadWorkspace(true);return;}
      if(target.dataset.userEdit){openUserEdit(Number(target.dataset.userEdit));return;}if(target.dataset.userToggle){const u=state.users.find(x=>Number(x.id)===Number(target.dataset.userToggle));if(!u)return;await api(baseWithId(urls.users,u.id),{method:'PATCH',body:{is_activated:!u.is_activated}});await loadUsers();return;}if(target.dataset.userDelete){if(!confirm(t('runtime_delete_this_user')))return;await api(baseWithId(urls.users,target.dataset.userDelete),{method:'DELETE',body:{confirmed:true}});await loadUsers();return;}
    } catch(error){toast(error.message,true);}
  });

  document.addEventListener('submit', async e=>{
    const form=e.target; e.preventDefault();
    try {
      if(form.dataset.createEntity){const base=form.dataset.createEntity==='customers'?urls.customers:urls.suppliers;await api(base,{method:'POST',body:formObject(form)});closeModal();await loadWorkspace(true);toast(t('runtime_profile_saved'));return;}
      if(form.id==='customer-profile-form'){await api(baseWithId(urls.customers,form.dataset.customerId),{method:'PUT',body:formObject(form)});await loadWorkspace(true);toast(t('runtime_customer_updated'));return;}
      if(form.id==='supplier-profile-form'){await api(baseWithId(urls.suppliers,form.dataset.supplierId),{method:'PUT',body:formObject(form)});await loadWorkspace(true);toast(t('runtime_supplier_updated'));return;}
      if(form.id==='customer-flow-step1'){const d=formObject(form);state.flow.amount=d.amount;state.flow.date=d.date;state.flow.notes=d.notes||'';state.flow.sarCollected=d.amount;state.flow.step=2;renderCustomerFlow();return;}
      if(form.id==='customer-flow-step2'){
        const d=formObject(form),f=state.flow; if(!f)return; f.account=d.account||''; f.date=d.date||f.date; f.dueAdjustment=d.due_adjustment||d.adjustment_amount||'';
        if(f.mode==='sale'){
          f.walletBatchId=d.wallet_batch_id||f.walletBatchId;f.customerRate=d.customer_rate||f.customerRate;f.sarCollected=d.sar_collected||f.amount;
          const selected=state.wallet_batches.find(b=>Number(b.id)===Number(f.walletBatchId));const payout=num(f.amount)*num(f.customerRate);if(!selected)throw new Error(t('runtime_select_wallet_stock_0fb77b5e'));if(payout>num(selected.remaining_bdt)+.005)throw new Error(t('runtime_selected_wallet_stock_is_insufficient'));
          await api(urls.customerSale,{method:'POST',body:{customer_id:f.customerId,wallet_batch_id:Number(f.walletBatchId),amount_sar:f.amount,customer_rate:f.customerRate,sar_collected:f.sarCollected,bdt_disbursed:payout.toFixed(2),receiver_name:'Recipient',receiver_phone:f.payment==='Cash'?'Cash':f.account,receiver_account_type:f.payment,receiver_account_no:f.payment==='Cash'?'Cash':f.account,notes:f.notes,timestamp:selectedUnix(f.date),due_adjustment_type:num(f.dueAdjustment)>.05?(f.due<-.05?'advance':'due'):null,due_adjustment_amount:num(f.dueAdjustment)>.05?f.dueAdjustment:null}});
        } else {
          if(num(f.dueAdjustment)<=.05)throw new Error(t('runtime_enter_an_adjustment_amount'));
          await api(urls.customerAdjustment,{method:'POST',body:{customer_id:f.customerId,kind:f.mode==='advance'?'advance':'due',amount_sar:f.dueAdjustment,timestamp:selectedUnix(f.date)}});
        }
        const id=f.customerId;state.flow=null;await loadWorkspace(true);renderCustomerProfile(id);toast(t('runtime_transaction_created'));return;
      }
      if(form.id==='transaction-edit-form'){const d=formObject(form),id=form.dataset.id;d.wallet_batch_id=d.wallet_batch_id||null;await api(baseWithId(urls.transactions,id),{method:'PATCH',body:d});closeModal();await loadWorkspace(true);toast(t('runtime_transaction_updated'));return;}
      if(form.id==='supplier-flow-step1'){const d=formObject(form);state.flow.amount=d.amount;state.flow.date=d.date;state.flow.notes=d.notes||'';state.flow.step=2;renderSupplierFlow();return;}
      if(form.id==='supplier-flow-step2'){const d=formObject(form),f=state.flow;if(!f)return;f.rate=d.rate;f.paid=d.paid;f.ledgerId=d.ledger_id||'';const payload={supplier_id:f.supplierId,transaction_type:f.mode==='purchase'?'SAR_GIVEN':'SAR_RECEIVED',amount_sar:f.amount,rate:f.rate,paid_bdt:f.paid|| (num(f.amount)*num(f.rate)).toFixed(2),ledger_id:f.mode==='purchase'?Number(f.ledgerId):null,notes:f.notes,timestamp:selectedUnix(f.date)};if(f.mode==='purchase'&&!f.ledgerId)throw new Error(t('runtime_select_a_wallet_ledger'));if(f.fundId)await api(baseWithId(urls.supplierFunds,f.fundId),{method:'PATCH',body:payload});else await api(urls.supplierFunds,{method:'POST',body:payload});const id=f.supplierId;state.flow=null;await loadWorkspace(true);renderSupplierProfile(id);toast(t('runtime_supplier_record_saved'));return;}
      if(form.id==='wallet-ledger-form'){await api(urls.walletLedgers,{method:'POST',body:formObject(form)});closeModal();await loadWorkspace(true);return;}
      if(form.id==='wallet-deposit-form'){const d=formObject(form);d.ledger_id=Number(form.dataset.ledgerId);await api(urls.walletDeposit,{method:'POST',body:d});closeModal();await loadWorkspace(true);toast(t('runtime_wallet_deposit_saved'));return;}
      if(form.id==='wallet-withdraw-form'){const d=formObject(form);d.ledger_id=Number(form.dataset.ledgerId);await api(urls.walletWithdraw,{method:'POST',body:d});closeModal();await loadWorkspace(true);toast(t('runtime_wallet_withdrawal_completed_fifo'));return;}
      if(form.id==='wallet-rename-form'){await api(baseWithId(urls.walletLedgers,form.dataset.ledgerId),{method:'PATCH',body:formObject(form)});closeModal();await loadWorkspace(true);return;}
      if(form.id==='expense-form'){const d=formObject(form),id=form.dataset.id;d.is_expense=d.is_expense==='1';if(id)await api(baseWithId(urls.expenses,id),{method:'PUT',body:d});else await api(urls.expenses,{method:'POST',body:d});closeModal();await loadWorkspace(true);return;}
      if(form.id==='profile-settings-form'){const data=await api(urls.profileSettings,{method:'POST',body:formObject(form)});state.user=data.user||state.user;$('#signed-user-name').textContent=state.user.name||'';toast(data.message||t('runtime_profile_updated'));return;}
      if(form.id==='personal-settings-form'){const d=formObject(form);setTheme($('#appearance-select')?.value||'system');const data=await api(urls.personalSettings,{method:'POST',body:{language:d.language}});toast(data.message||t('runtime_preferences_saved'));if(d.language!==app.dataset.language)location.reload();return;}
      if(form.id==='pin-form'){const data=await api(urls.pinSettings,{method:'POST',body:formObject(form)});form.reset();toast(data.message||t('runtime_pin_changed'));return;}
      if(form.id==='config-form'){const d=formObject(form);['rate_based_mode','supplier_rate_enabled','wallet_rate_enabled'].forEach(k=>d[k]=form.elements[k]?.checked?1:0);const data=await api(urls.config,{method:'POST',body:d});state.settings={...state.settings,...(data.settings||{})};$$('.brand-name').forEach(el=>el.textContent=state.settings.app_name||'SAFA');toast(data.message||t('runtime_configuration_saved'));return;}
      if(form.id==='logo-form'){const data=await api(urls.logo,{method:'POST',body:new FormData(form)});const src=data.app_logo_path||data.app_logo_url||data.url;if(src){$$('.brand-logo').forEach(el=>el.src=src);if($('#settings-logo-preview'))$('#settings-logo-preview').src=src;}form.reset();toast(data.message||t('runtime_logo_uploaded'));return;}
      if(form.id==='user-form'){const d=formObject(form);d.is_activated=form.elements.is_activated.checked;await api(urls.users,{method:'POST',body:d});form.reset();form.elements.is_activated.checked=true;await loadUsers();toast(t('runtime_user_created'));return;}
      if(form.id==='user-edit-form'){const d=formObject(form);if(!d.pin)delete d.pin;await api(baseWithId(urls.users,form.dataset.id),{method:'PATCH',body:d});closeModal();await loadUsers();return;}
    } catch(error){toast(error.message,true);}
  });

  $('#account-select')?.addEventListener('change',async e=>{if(!e.target.value)return;try{await api(urls.accountSwitch,{method:'POST',body:{account_id:Number(e.target.value)}});app.dataset.activeAccount=e.target.value;$('#global-message')?.classList.add('hidden');closeSubpage();await loadWorkspace(true);}catch(error){toast(error.message,true);}});

  if (app.dataset.activeAccount) loadWorkspace(true); else renderAll();
})();
