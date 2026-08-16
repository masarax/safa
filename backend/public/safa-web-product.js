(() => {
  'use strict';

  const app = document.getElementById('safa-app');
  const dashboard = document.querySelector('[data-screen="dashboard"]');
  if (!app || !dashboard) return;

  const bn = app.dataset.language === 'bn';
  const text = (en, bengali) => bn ? bengali : en;
  const esc = value => String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
  const num = value => Number.parseFloat(value ?? 0) || 0;
  const money = (value, digits = 2) => num(value).toLocaleString(undefined, {minimumFractionDigits: digits, maximumFractionDigits: digits});
  const timestampMs = value => {
    const raw = Number(value) || 0;
    return raw > 2_000_000_000 ? raw : raw * 1000;
  };
  const dateText = value => new Date(timestampMs(value)).toLocaleDateString(undefined, {year:'numeric', month:'long', day:'numeric'});
  const timeText = value => new Date(timestampMs(value)).toLocaleTimeString(undefined, {hour:'2-digit', minute:'2-digit'});

  function ensureDashboardSections() {
    if (document.getElementById('dashboard-recent-card')) return;
    const overview = document.getElementById('dashboard-overview-card');
    if (!overview) return;

    const recent = document.createElement('section');
    recent.id = 'dashboard-recent-card';
    recent.className = 'surface-card dashboard-recent-card';
    recent.innerHTML = `
      <div class="card-heading dashboard-section-heading">
        <div><h2>${text('Recent Transaction History','রিসেন্ট লেনদেন খাতা')}</h2><p>${text('Latest customer and supplier ledger activity','সর্বশেষ কাস্টমার ও সাপ্লায়ার লেনদেন')}</p></div>
      </div>
      <div id="dashboard-recent-list" class="dashboard-recent-list"><div class="empty-state">${text('Loading recent activity…','সাম্প্রতিক কার্যক্রম লোড হচ্ছে…')}</div></div>`;

    const reserves = document.createElement('section');
    reserves.id = 'dashboard-reserves-card';
    reserves.className = 'surface-card dashboard-reserves-card';
    reserves.innerHTML = `
      <div class="card-heading"><div><h2>${text('Ledger Reserves Details','ব্যালেন্স শিট ও সাপ্লায়ার রিজার্ভ')}</h2><p>${text('Current movement and available fund position','বর্তমান লেনদেন ও ফান্ড অবস্থান')}</p></div></div>
      <div id="dashboard-reserves" class="reserve-grid"></div>`;

    overview.insertAdjacentElement('afterend', recent);
    recent.insertAdjacentElement('afterend', reserves);
  }

  function customerBalance(transactions, customerId) {
    return transactions.filter(tx => Number(tx.customer_id) === Number(customerId))
      .reduce((sum, tx) => sum + num(tx.amount_sar) - num(tx.sar_collected), 0);
  }

  function supplierBalance(deposits, supplierId) {
    const rows = deposits.filter(dep => Number(dep.supplier_id) === Number(supplierId));
    const acquired = rows.filter(dep => ['SAR_GIVEN','SAR_DEPOSIT'].includes(dep.transaction_type)).reduce((sum, dep) => sum + num(dep.amount_bdt), 0);
    const paid = rows.reduce((sum, dep) => sum + num(dep.paid_bdt), 0);
    return acquired - paid;
  }

  function renderRecent(data) {
    const list = document.getElementById('dashboard-recent-list');
    if (!list) return;
    const customers = data.customers || [];
    const suppliers = data.suppliers || [];
    const transactions = data.transactions || [];
    const deposits = data.supplier_deposits || [];
    const foreign = data.settings?.foreign_currency || 'SAR';
    const local = data.settings?.local_currency || 'BDT';

    const customerRows = transactions.map(tx => ({
      kind:'customer', id:tx.id, entityId:tx.customer_id, timestamp:tx.timestamp,
      name:customers.find(item => Number(item.id) === Number(tx.customer_id))?.name || text('Customer','কাস্টমার'),
      subtitle:`${text('Rate','রেট')}: ${tx.customer_rate ?? '—'} · ${timeText(tx.timestamp)}`,
      amount:`${foreign} ${money(tx.amount_sar, 0)}`,
      amountClass:(num(tx.amount_sar) - num(tx.sar_collected)) > .05 ? 'negative' : 'positive'
    }));

    const supplierRows = deposits.map(dep => {
      const settlement = dep.transaction_type === 'BDT_SETTLEMENT';
      return {
        kind:'supplier', id:dep.id, entityId:dep.supplier_id, timestamp:dep.timestamp,
        name:suppliers.find(item => Number(item.id) === Number(dep.supplier_id))?.name || text('Supplier','সাপ্লায়ার'),
        subtitle:settlement ? `${text('Settle dues','বকেয়া পরিশোধ')} · ${timeText(dep.timestamp)}` : `${text('Rate','রেট')}: ${dep.rate ?? '—'} · ${timeText(dep.timestamp)}`,
        amount:settlement ? `${local} ${money(dep.paid_bdt, 0)}` : `${foreign} ${money(dep.amount_sar, 0)}`,
        amountClass:settlement ? 'positive' : 'info'
      };
    });

    const rows = [...customerRows, ...supplierRows].sort((a,b) => Number(b.timestamp) - Number(a.timestamp)).slice(0, 8);
    if (!rows.length) {
      list.innerHTML = `<div class="empty-state"><strong>${text('No transactions found','কোনো লেনদেন পাওয়া যায়নি')}</strong>${text('New customer and supplier activity will appear here.','নতুন কাস্টমার ও সাপ্লায়ার কার্যক্রম এখানে দেখাবে।')}</div>`;
      return;
    }

    const groups = new Map();
    rows.forEach(row => {
      const key = dateText(row.timestamp);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(row);
    });

    list.innerHTML = [...groups.entries()].map(([date, items]) => `
      <div class="date-group dashboard-date-group">
        <div class="date-heading"><span>${esc(date)}</span><span>${items.length}</span></div>
        ${items.map(item => `<div class="ledger-row dashboard-recent-row" ${item.kind === 'customer' ? `data-open-customer="${Number(item.entityId)}"` : `data-open-supplier="${Number(item.entityId)}"`}>
          <div class="recent-entry-main">
            <span class="recent-entry-icon ${item.kind}"><span class="icon ${item.kind === 'customer' ? 'icon-people' : 'icon-supplier'}" aria-hidden="true"></span></span>
            <span class="ledger-main"><strong>${esc(item.name)}</strong><small>${esc(item.subtitle)}</small></span>
          </div>
          <span class="recent-entry-amount ${item.amountClass}">${esc(item.amount)}</span>
        </div>`).join('')}
      </div>`).join('');
  }

  function renderReserves(data) {
    const root = document.getElementById('dashboard-reserves');
    if (!root) return;
    const transactions = data.transactions || [];
    const deposits = data.supplier_deposits || [];
    const customers = data.customers || [];
    const suppliers = data.suppliers || [];
    const batches = data.wallet_batches || [];
    const foreign = data.settings?.foreign_currency || 'SAR';
    const local = data.settings?.local_currency || 'BDT';

    const sarVolume = transactions.reduce((sum, tx) => sum + num(tx.amount_sar), 0);
    const walletStock = batches.reduce((sum, batch) => sum + num(batch.remaining_bdt), 0);
    const customerDue = customers.reduce((sum, customer) => sum + Math.max(customerBalance(transactions, customer.id), 0), 0);
    const supplierNet = suppliers.reduce((sum, supplier) => sum + supplierBalance(deposits, supplier.id), 0);

    root.innerHTML = `
      <div class="reserve-item"><small>${text('Customer SAR volume','কাস্টমার রিয়াল ভলিউম')}</small><strong>${money(sarVolume,0)} ${esc(foreign)}</strong></div>
      <div class="reserve-item"><small>${text('Current fund stock','বর্তমান ফান্ড স্টক')}</small><strong>${money(walletStock,0)} ${esc(local)}</strong></div>
      <div class="reserve-item"><small>${text('Customer due','কাস্টমার বকেয়া')}</small><strong class="${customerDue > .05 ? 'negative' : ''}">${money(customerDue,0)} ${esc(foreign)}</strong></div>
      <div class="reserve-item"><small>${text('Supplier ledger net','সাপ্লায়ার লেজার নেট')}</small><strong>${money(Math.abs(supplierNet),0)} ${esc(local)}</strong></div>`;
  }

  let requestSequence = 0;
  async function refreshPresentation() {
    ensureDashboardSections();
    const url = app.dataset.workspaceUrl || '';
    if (!url || !app.dataset.activeAccount) {
      const list = document.getElementById('dashboard-recent-list');
      if (list) list.innerHTML = `<div class="empty-state">${text('Select an account to load recent activity.','সাম্প্রতিক কার্যক্রম দেখতে একটি অ্যাকাউন্ট নির্বাচন করুন।')}</div>`;
      return;
    }
    const sequence = ++requestSequence;
    try {
      const response = await fetch(url, {credentials:'same-origin', headers:{Accept:'application/json'}});
      if (!response.ok) return;
      const data = await response.json();
      if (sequence !== requestSequence) return;
      renderRecent(data || {});
      renderReserves(data || {});
    } catch (_) {
      /* The primary runtime owns user-facing API error reporting. */
    }
  }

  ensureDashboardSections();
  refreshPresentation();

  const stats = document.getElementById('dashboard-stats');
  if (stats) {
    let timer = null;
    new MutationObserver(() => {
      clearTimeout(timer);
      timer = setTimeout(refreshPresentation, 80);
    }).observe(stats, {childList:true});
  }
  new MutationObserver(refreshPresentation).observe(app, {attributes:true, attributeFilter:['data-active-account']});
})();
