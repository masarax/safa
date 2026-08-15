(() => {
    'use strict';

    const app = document.getElementById('safa-app');
    if (!app) return;

    const csrf = document.querySelector('meta[name="csrf-token"]')?.content || '';
    const language = app.dataset.language === 'bn' ? 'bn' : 'en';
    const text = (en, bn) => language === 'bn' ? bn : en;
    const state = {
        customers: [],
        suppliers: [],
        transactions: [],
        'wallet-ledgers': [],
        'wallet-batches': [],
        'supplier-deposits': [],
        expenses: [],
        users: [],
        transactionTotal: 0,
        activeProfile: null,
    };

    const endpoint = (resource) => {
        const key = resource.replace(/-([a-z])/g, (_, c) => c.toUpperCase()) + 'Url';
        return app.dataset[key] || '';
    };

    const toast = (message, isError = false) => {
        const region = document.getElementById('toast-region');
        if (!region) return;
        const node = document.createElement('div');
        node.className = `toast${isError ? ' error' : ''}`;
        node.textContent = message;
        region.appendChild(node);
        window.setTimeout(() => node.remove(), 4200);
    };

    const request = async (url, options = {}) => {
        if (!url) throw new Error('Missing endpoint');
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');
        headers.set('X-Requested-With', 'XMLHttpRequest');
        if (csrf) headers.set('X-CSRF-TOKEN', csrf);
        if (app.dataset.activeAccount) headers.set('X-SAFA-ACCOUNT-ID', app.dataset.activeAccount);

        let body = options.body;
        if (body && !(body instanceof FormData) && typeof body !== 'string') {
            headers.set('Content-Type', 'application/json');
            body = JSON.stringify(body);
        }

        const response = await fetch(url, {
            method: options.method || 'GET',
            credentials: 'same-origin',
            headers,
            body,
        });

        let payload = {};
        try { payload = await response.json(); } catch (_) { payload = {}; }
        if (!response.ok) {
            const validation = payload.errors && typeof payload.errors === 'object'
                ? Object.values(payload.errors).flat().join(' ')
                : '';
            throw new Error(validation || payload.message || text('Request failed.', 'অনুরোধ ব্যর্থ হয়েছে।'));
        }
        return payload;
    };

    const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const closeProfiles = () => {
        document.querySelectorAll('.profile-view').forEach((node) => node.classList.add('hidden'));
        document.getElementById('customers-list-view')?.classList.remove('hidden');
        document.getElementById('suppliers-list-view')?.classList.remove('hidden');
        state.activeProfile = null;
    };

    const activatePanel = (name) => {
        closeProfiles();
        document.querySelectorAll('[data-panel]').forEach((panel) => panel.classList.toggle('active', panel.dataset.panel === name));
        document.querySelectorAll('[data-section]').forEach((button) => button.classList.toggle('active', button.dataset.section === name));
        document.getElementById('sidebar')?.classList.remove('open');
        document.getElementById('menu-toggle')?.setAttribute('aria-expanded', 'false');
        window.scrollTo({ top: 0, behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
    };

    document.querySelectorAll('[data-section]').forEach((button) => button.addEventListener('click', () => activatePanel(button.dataset.section)));

    document.getElementById('menu-toggle')?.addEventListener('click', (event) => {
        const sidebar = document.getElementById('sidebar');
        if (!sidebar) return;
        const open = sidebar.classList.toggle('open');
        event.currentTarget.setAttribute('aria-expanded', open ? 'true' : 'false');
    });

    document.querySelectorAll('[data-subsection]').forEach((button) => {
        button.addEventListener('click', () => {
            const name = button.dataset.subsection;
            document.querySelectorAll('[data-subsection]').forEach((candidate) => candidate.classList.toggle('active', candidate === button));
            document.querySelectorAll('[data-subpanel]').forEach((panel) => panel.classList.toggle('active', panel.dataset.subpanel === name));
        });
    });

    const emptyRow = (body, columns, message) => {
        if (!body) return;
        body.replaceChildren();
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = columns;
        cell.className = 'empty-cell';
        cell.textContent = message;
        row.appendChild(cell);
        body.appendChild(row);
    };

    const td = (value) => {
        const cell = document.createElement('td');
        cell.textContent = value == null || value === '' ? '—' : String(value);
        return cell;
    };

    const button = (label, className, dataset = {}) => {
        const node = document.createElement('button');
        node.type = 'button';
        node.className = className;
        node.textContent = label;
        Object.entries(dataset).forEach(([key, value]) => { node.dataset[key] = String(value); });
        return node;
    };

    const recordId = (record) => Number(record?.id || record?.server_id || 0);

    const actionCell = (resource, record, options = {}) => {
        const cell = document.createElement('td');
        const wrap = document.createElement('div');
        wrap.className = 'row-actions';
        const id = recordId(record);

        if (options.profile && id > 0) {
            wrap.appendChild(button(text('Open', 'খুলুন'), 'button primary small', {
                profileResource: options.profile,
                profileId: id,
            }));
        }
        if (options.edit !== false && id > 0) {
            wrap.appendChild(button(text('Edit', 'এডিট'), 'button secondary small', { editResource: resource, editId: id }));
        }
        if (options.remove !== false && id > 0) {
            wrap.appendChild(button(text('Delete', 'মুছুন'), 'button danger small', { deleteResource: resource, deleteId: id }));
        }
        cell.appendChild(wrap);
        return cell;
    };

    const setStat = (id, value) => {
        const node = document.getElementById(id);
        if (node) node.textContent = String(value);
    };

    const listFromPayload = (resource, payload) => {
        if (resource === 'customers') return payload.customers || [];
        if (resource === 'suppliers') return payload.suppliers || [];
        if (resource === 'transactions') return payload.transactions?.data || payload.transactions || [];
        if (resource === 'wallet-ledgers') return payload.wallet_ledgers || [];
        if (resource === 'wallet-batches') return payload.wallet_batches || [];
        if (resource === 'supplier-deposits') return payload.supplier_deposits || [];
        if (resource === 'expenses') return payload.expenses_incomes || [];
        if (resource === 'users') return payload.users || [];
        return [];
    };

    const populateProfileSelects = () => {
        document.querySelectorAll('[data-profile-select="customers"]').forEach((select) => populateSelect(select, state.customers));
        document.querySelectorAll('[data-profile-select="suppliers"]').forEach((select) => populateSelect(select, state.suppliers));
    };

    const populateSelect = (select, records) => {
        if (!select) return;
        const previous = select.value;
        const first = select.options[0]?.cloneNode(true) || new Option(text('Optional', 'ঐচ্ছিক'), '');
        select.replaceChildren(first);
        records.forEach((record) => {
            const id = recordId(record);
            if (!id) return;
            select.appendChild(new Option(`${record.name || text('Record', 'রেকর্ড')} · #${id}`, String(id)));
        });
        if (Array.from(select.options).some((option) => option.value === previous)) select.value = previous;
    };

    const renderCustomers = () => {
        const body = document.getElementById('customers-body');
        if (!body) return;
        if (!state.customers.length) return emptyRow(body, 4, text('No customers found.', 'কোনো কাস্টমার পাওয়া যায়নি।'));
        body.replaceChildren();
        state.customers.forEach((record) => {
            const row = document.createElement('tr');
            row.append(
                td(record.name),
                td(record.phone),
                td(record.address),
                actionCell('customers', record, {
                    profile: 'customer',
                    edit: app.dataset.canEditCustomers !== '0',
                    remove: app.dataset.canDeleteCustomers !== '0',
                })
            );
            body.appendChild(row);
        });
        setStat('stat-customers', state.customers.length);
        populateProfileSelects();
        refreshActiveProfile();
    };

    const renderSuppliers = () => {
        const body = document.getElementById('suppliers-body');
        if (!body) return;
        if (!state.suppliers.length) return emptyRow(body, 4, text('No suppliers found.', 'কোনো সাপ্লায়ার পাওয়া যায়নি।'));
        body.replaceChildren();
        state.suppliers.forEach((record) => {
            const row = document.createElement('tr');
            row.append(
                td(record.name),
                td(record.phone),
                td(record.address),
                actionCell('suppliers', record, {
                    profile: 'supplier',
                    edit: app.dataset.canEditSuppliers !== '0',
                    remove: app.dataset.canDeleteSuppliers !== '0',
                })
            );
            body.appendChild(row);
        });
        setStat('stat-suppliers', state.suppliers.length);
        populateProfileSelects();
        refreshActiveProfile();
    };

    const renderTransactions = () => {
        setStat('stat-transactions', state.transactionTotal || state.transactions.length);
        updateReports();
        refreshActiveProfile();
    };

    const renderWalletLedgers = () => {
        const body = document.getElementById('wallet-ledgers-body');
        if (!body) return;
        if (!state['wallet-ledgers'].length) return emptyRow(body, 3, text('No wallet ledgers found.', 'কোনো ওয়ালেট লেজার পাওয়া যায়নি।'));
        body.replaceChildren();
        state['wallet-ledgers'].forEach((record) => {
            const row = document.createElement('tr');
            row.append(td(record.id), td(record.name), actionCell('wallet-ledgers', record));
            body.appendChild(row);
        });
    };

    const renderWalletBatches = () => {
        const body = document.getElementById('wallet-batches-body');
        if (!body) return;
        if (!state['wallet-batches'].length) return emptyRow(body, 5, text('No wallet batches found.', 'কোনো ওয়ালেট ব্যাচ পাওয়া যায়নি।'));
        body.replaceChildren();
        state['wallet-batches'].forEach((record) => {
            const row = document.createElement('tr');
            row.append(td(record.id), td(record.rate), td(record.initial_bdt), td(record.remaining_bdt), actionCell('wallet-batches', record));
            body.appendChild(row);
        });
    };

    const renderSupplierDeposits = () => {
        const body = document.getElementById('supplier-deposits-body');
        if (body) {
            if (!state['supplier-deposits'].length) {
                emptyRow(body, 6, text('No supplier deposits found.', 'কোনো সাপ্লায়ার ডিপোজিট পাওয়া যায়নি।'));
            } else {
                body.replaceChildren();
                state['supplier-deposits'].forEach((record) => {
                    const row = document.createElement('tr');
                    row.append(td(record.id), td(record.supplier_id), td(record.amount_sar), td(record.amount_bdt), td(record.paid_bdt), actionCell('supplier-deposits', record));
                    body.appendChild(row);
                });
            }
        }
        refreshActiveProfile();
    };

    const renderExpenses = () => {
        const body = document.getElementById('expenses-body');
        if (!body) return;
        if (!state.expenses.length) return emptyRow(body, 5, text('No income/expense entries found.', 'কোনো আয়/ব্যয় এন্ট্রি পাওয়া যায়নি।'));
        body.replaceChildren();
        state.expenses.forEach((record) => {
            const row = document.createElement('tr');
            row.append(
                td(record.title),
                td(`${record.amount ?? '0.00'} ${record.currency ?? ''}`),
                td(record.is_expense ? text('Expense', 'ব্যয়') : text('Income', 'আয়')),
                td(record.category),
                actionCell('expenses', record)
            );
            body.appendChild(row);
        });
        setStat('stat-expenses', state.expenses.length);
        updateReports();
    };

    const renderUsers = () => {
        const body = document.getElementById('users-body');
        if (!body) return;
        if (!state.users.length) return emptyRow(body, 6, text('No manageable users found.', 'পরিচালনাযোগ্য কোনো ইউজার পাওয়া যায়নি।'));
        body.replaceChildren();
        state.users.forEach((record) => {
            const row = document.createElement('tr');
            const status = document.createElement('td');
            const pill = document.createElement('span');
            pill.className = `status-pill${record.is_activated ? '' : ' off'}`;
            pill.textContent = record.is_activated ? text('Active', 'সক্রিয়') : text('Inactive', 'নিষ্ক্রিয়');
            status.appendChild(pill);
            row.append(td(record.name), td(record.mobile), td(record.email), td(record.role_label || record.role), status, actionCell('users', record));
            body.appendChild(row);
        });
    };

    const renderers = {
        customers: renderCustomers,
        suppliers: renderSuppliers,
        transactions: renderTransactions,
        'wallet-ledgers': renderWalletLedgers,
        'wallet-batches': renderWalletBatches,
        'supplier-deposits': renderSupplierDeposits,
        expenses: renderExpenses,
        users: renderUsers,
    };

    const resourceUrl = (resource) => {
        const base = endpoint(resource);
        if (resource !== 'transactions' || !base) return base;
        const separator = base.includes('?') ? '&' : '?';
        return `${base}${separator}per_page=200`;
    };

    const loadResource = async (resource) => {
        const url = resourceUrl(resource);
        if (!url) return;
        if (!app.dataset.activeAccount && resource !== 'users') return;
        try {
            const payload = await request(url);
            state[resource] = listFromPayload(resource, payload);
            if (resource === 'transactions') state.transactionTotal = Number(payload.transactions?.total ?? state[resource].length);
            renderers[resource]?.();
        } catch (error) {
            toast(error.message, true);
        }
    };

    const enabledResources = () => ['customers', 'suppliers', 'transactions', 'wallet-ledgers', 'wallet-batches', 'supplier-deposits', 'expenses', 'users']
        .filter((resource) => endpoint(resource));

    const loadAll = async () => {
        const resources = enabledResources().filter((resource) => app.dataset.activeAccount || resource === 'users');
        await Promise.all(resources.map((resource) => loadResource(resource)));
    };

    const cleanFormObject = (form) => {
        const data = {};
        const formData = new FormData(form);
        for (const [key, value] of formData.entries()) {
            if (value instanceof File) continue;
            if (value === '') continue;
            data[key] = value;
        }
        if (form.dataset.resource === 'expenses') data.is_expense = form.elements.is_expense?.checked === true;
        return data;
    };

    const fillForm = (resource, record) => {
        const form = document.querySelector(`form[data-resource="${resource}"]`);
        if (!form) return;
        form.dataset.editId = String(recordId(record));
        Array.from(form.elements).forEach((control) => {
            if (!control.name) return;
            if (control.type === 'checkbox') control.checked = Boolean(record[control.name]);
            else if (Object.prototype.hasOwnProperty.call(record, control.name) && record[control.name] != null) control.value = String(record[control.name]);
        });
        form.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'center' });
        toast(text('Editing selected record. Save to apply changes.', 'নির্বাচিত রেকর্ড এডিট হচ্ছে। পরিবর্তন প্রয়োগ করতে সংরক্ষণ করুন।'));
    };

    document.querySelectorAll('.resource-form').forEach((form) => {
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const resource = form.dataset.resource;
            const base = endpoint(resource);
            if (!base || !app.dataset.activeAccount) return toast(text('Select an account first.', 'আগে একটি অ্যাকাউন্ট নির্বাচন করুন।'), true);
            const editId = form.dataset.editId || '';
            const submit = form.querySelector('button[type="submit"]');
            if (submit) submit.disabled = true;
            try {
                await request(editId ? `${base}/${encodeURIComponent(editId)}` : base, {
                    method: editId ? 'PUT' : 'POST',
                    body: cleanFormObject(form),
                });
                form.reset();
                delete form.dataset.editId;
                toast(text('Saved successfully.', 'সফলভাবে সংরক্ষণ হয়েছে।'));
                await loadResource(resource);
            } catch (error) {
                toast(error.message, true);
            } finally {
                if (submit) submit.disabled = false;
            }
        });
    });

    const profileTransactions = (type, id) => state.transactions.filter((record) => Number(record[`${type}_id`]) === Number(id));

    const formatDate = (value) => {
        const raw = Number(value || 0);
        if (!raw) return '—';
        const millis = raw < 20_000_000_000 ? raw * 1000 : raw;
        const date = new Date(millis);
        if (Number.isNaN(date.getTime())) return '—';
        return new Intl.DateTimeFormat(language === 'bn' ? 'bn-BD' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
    };

    const toMinorUnits = (value, scale = 2) => {
        const raw = String(value ?? '0').trim();
        const match = raw.match(/^([+-]?)(\d+)(?:\.(\d+))?$/);
        if (!match) return 0n;
        const negative = match[1] === '-';
        const whole = BigInt(match[2]);
        const fraction = match[3] || '';
        const factor = 10n ** BigInt(scale);
        const padded = (fraction + '0'.repeat(scale + 1)).slice(0, scale + 1);
        const kept = padded.slice(0, scale) || '0';
        let units = (whole * factor) + BigInt(kept);
        if (padded.length > scale && padded.charCodeAt(scale) >= 53) units += 1n;
        return negative ? -units : units;
    };

    const formatMinorUnits = (units, scale = 2) => {
        const negative = units < 0n;
        const absolute = negative ? -units : units;
        const factor = 10n ** BigInt(scale);
        const whole = absolute / factor;
        const fraction = (absolute % factor).toString().padStart(scale, '0');
        return `${negative ? '-' : ''}${whole.toString()}.${fraction}`;
    };

    const transactionActionCell = (record) => {
        const cell = document.createElement('td');
        const wrap = document.createElement('div');
        wrap.className = 'row-actions';
        const id = recordId(record);
        if (app.dataset.canEditTransactions === '1') wrap.appendChild(button(text('Edit', 'এডিট'), 'button secondary small', { profileTxEdit: id }));
        if (app.dataset.canDeleteTransactions === '1') wrap.appendChild(button(text('Delete', 'মুছুন'), 'button danger small', { profileTxDelete: id }));
        cell.appendChild(wrap);
        return cell;
    };

    const renderProfileTransactionRows = (body, records) => {
        if (!body) return;
        if (!records.length) return emptyRow(body, 6, text('No transactions for this profile.', 'এই প্রোফাইলে কোনো লেনদেন নেই।'));
        body.replaceChildren();
        records.forEach((record) => {
            const row = document.createElement('tr');
            row.append(
                td(record.amount_sar ?? record.amount),
                td(record.amount_bdt),
                td(`${record.customer_rate ?? '0.0000'} / ${record.supplier_rate ?? '0.0000'}`),
                td(record.receiver_name || record.receiver_phone),
                td(formatDate(record.timestamp)),
                transactionActionCell(record)
            );
            body.appendChild(row);
        });
    };

    const resetProfileTransactionForm = (type, id) => {
        const form = document.getElementById(`${type}-transaction-form`);
        if (!form) return;
        form.reset();
        delete form.dataset.editId;
        const bound = form.elements[`${type}_id`];
        if (bound) bound.value = String(id);
        form.querySelector('[data-cancel-transaction-edit]')?.classList.add('hidden');
    };

    const renderCustomerProfile = (customer) => {
        const profile = document.getElementById('customer-profile');
        if (!profile || !customer) return;
        document.getElementById('customers-list-view')?.classList.add('hidden');
        profile.classList.remove('hidden');
        document.getElementById('customer-profile-name').textContent = customer.name || '—';
        document.getElementById('customer-profile-contact').textContent = [customer.phone, customer.address].filter(Boolean).join(' · ') || '—';
        const records = profileTransactions('customer', recordId(customer));
        const sar = records.reduce((sum, item) => sum + toMinorUnits(item.amount_sar ?? item.amount), 0n);
        const bdt = records.reduce((sum, item) => sum + toMinorUnits(item.amount_bdt), 0n);
        const collected = records.reduce((sum, item) => sum + toMinorUnits(item.sar_collected), 0n);
        setStat('customer-profile-count', records.length);
        setStat('customer-profile-sar', formatMinorUnits(sar));
        setStat('customer-profile-bdt', formatMinorUnits(bdt));
        setStat('customer-profile-due', formatMinorUnits(sar - collected));
        renderProfileTransactionRows(document.getElementById('customer-profile-transactions-body'), records);
        const form = document.getElementById('customer-transaction-form');
        if (form && !form.dataset.editId) form.elements.customer_id.value = String(recordId(customer));
    };

    const renderSupplierProfile = (supplier) => {
        const profile = document.getElementById('supplier-profile');
        if (!profile || !supplier) return;
        document.getElementById('suppliers-list-view')?.classList.add('hidden');
        profile.classList.remove('hidden');
        document.getElementById('supplier-profile-name').textContent = supplier.name || '—';
        document.getElementById('supplier-profile-contact').textContent = [supplier.phone, supplier.address].filter(Boolean).join(' · ') || '—';
        const records = profileTransactions('supplier', recordId(supplier));
        const sar = records.reduce((sum, item) => sum + toMinorUnits(item.amount_sar ?? item.amount), 0n);
        const bdt = records.reduce((sum, item) => sum + toMinorUnits(item.amount_bdt), 0n);
        setStat('supplier-profile-count', records.length);
        setStat('supplier-profile-sar', formatMinorUnits(sar));
        setStat('supplier-profile-bdt', formatMinorUnits(bdt));
        renderProfileTransactionRows(document.getElementById('supplier-profile-transactions-body'), records);
        const deposits = state['supplier-deposits'].filter((record) => Number(record.supplier_id) === recordId(supplier));
        setStat('supplier-profile-deposit-count', deposits.length);
        const depositBody = document.getElementById('supplier-profile-deposits-body');
        if (depositBody) {
            if (!deposits.length) emptyRow(depositBody, 5, text('No deposits for this supplier.', 'এই সাপ্লায়ারের কোনো ডিপোজিট নেই।'));
            else {
                depositBody.replaceChildren();
                deposits.forEach((record) => {
                    const row = document.createElement('tr');
                    row.append(td(record.amount_sar), td(record.rate), td(record.amount_bdt), td(record.paid_bdt), td(record.transaction_type));
                    depositBody.appendChild(row);
                });
            }
        }
        const form = document.getElementById('supplier-transaction-form');
        if (form && !form.dataset.editId) form.elements.supplier_id.value = String(recordId(supplier));
    };

    const refreshActiveProfile = () => {
        if (!state.activeProfile) return;
        const { type, id } = state.activeProfile;
        const collection = type === 'customer' ? state.customers : state.suppliers;
        const record = collection.find((item) => recordId(item) === Number(id));
        if (!record) return closeProfiles();
        if (type === 'customer') renderCustomerProfile(record);
        else renderSupplierProfile(record);
    };

    const openProfile = (type, id) => {
        state.activeProfile = { type, id: Number(id) };
        if (type === 'customer') {
            const record = state.customers.find((item) => recordId(item) === Number(id));
            if (record) renderCustomerProfile(record);
        } else {
            const record = state.suppliers.find((item) => recordId(item) === Number(id));
            if (record) renderSupplierProfile(record);
        }
        resetProfileTransactionForm(type, id);
        window.scrollTo({ top: 0, behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
    };

    document.querySelectorAll('.profile-transaction-form').forEach((form) => {
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const base = endpoint('transactions');
            if (!base || !state.activeProfile || !app.dataset.activeAccount) return;
            const editId = form.dataset.editId || '';
            const submit = form.querySelector('button[type="submit"]');
            if (submit) submit.disabled = true;
            try {
                await request(editId ? `${base}/${encodeURIComponent(editId)}` : base, {
                    method: editId ? 'PUT' : 'POST',
                    body: cleanFormObject(form),
                });
                const profile = { ...state.activeProfile };
                resetProfileTransactionForm(profile.type, profile.id);
                toast(text('Transaction saved successfully.', 'লেনদেন সফলভাবে সংরক্ষণ হয়েছে।'));
                await loadResource('transactions');
            } catch (error) {
                toast(error.message, true);
            } finally {
                if (submit) submit.disabled = false;
            }
        });
    });

    document.querySelectorAll('[data-cancel-transaction-edit]').forEach((node) => node.addEventListener('click', () => {
        if (!state.activeProfile) return;
        resetProfileTransactionForm(state.activeProfile.type, state.activeProfile.id);
    }));

    document.addEventListener('click', async (event) => {
        const profileButton = event.target.closest('[data-profile-resource]');
        if (profileButton) {
            openProfile(profileButton.dataset.profileResource, Number(profileButton.dataset.profileId));
            return;
        }

        const close = event.target.closest('[data-close-profile]');
        if (close) {
            closeProfiles();
            return;
        }

        const txEdit = event.target.closest('[data-profile-tx-edit]');
        if (txEdit && state.activeProfile) {
            const id = Number(txEdit.dataset.profileTxEdit);
            const record = state.transactions.find((item) => recordId(item) === id);
            const form = document.getElementById(`${state.activeProfile.type}-transaction-form`);
            if (!record || !form) return;
            form.dataset.editId = String(id);
            Array.from(form.elements).forEach((control) => {
                if (!control.name) return;
                if (Object.prototype.hasOwnProperty.call(record, control.name) && record[control.name] != null) control.value = String(record[control.name]);
            });
            const bound = form.elements[`${state.activeProfile.type}_id`];
            if (bound) bound.value = String(state.activeProfile.id);
            form.querySelector('[data-cancel-transaction-edit]')?.classList.remove('hidden');
            form.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'center' });
            return;
        }

        const txDelete = event.target.closest('[data-profile-tx-delete]');
        if (txDelete) {
            const id = Number(txDelete.dataset.profileTxDelete);
            if (!id || !endpoint('transactions')) return;
            if (!window.confirm(text('Delete this transaction?', 'এই লেনদেন মুছে ফেলবেন?'))) return;
            txDelete.disabled = true;
            try {
                await request(`${endpoint('transactions')}/${encodeURIComponent(id)}`, { method: 'DELETE', body: { confirmed: true } });
                toast(text('Transaction deleted successfully.', 'লেনদেন সফলভাবে মুছে ফেলা হয়েছে।'));
                await loadResource('transactions');
            } catch (error) {
                toast(error.message, true);
            } finally {
                txDelete.disabled = false;
            }
            return;
        }

        const edit = event.target.closest('[data-edit-resource]');
        if (edit) {
            const resource = edit.dataset.editResource;
            if (resource === 'users') return;
            const id = Number(edit.dataset.editId);
            const record = state[resource]?.find((item) => recordId(item) === id);
            if (record) fillForm(resource, record);
            return;
        }

        const remove = event.target.closest('[data-delete-resource]');
        if (!remove) return;
        const resource = remove.dataset.deleteResource;
        const id = Number(remove.dataset.deleteId);
        if (!id || !endpoint(resource)) return;
        if (!window.confirm(text('Delete this record?', 'এই রেকর্ড মুছে ফেলবেন?'))) return;
        remove.disabled = true;
        try {
            await request(`${endpoint(resource)}/${encodeURIComponent(id)}`, { method: 'DELETE', body: { confirmed: true } });
            toast(text('Deleted successfully.', 'সফলভাবে মুছে ফেলা হয়েছে।'));
            await loadResource(resource);
        } catch (error) {
            toast(error.message, true);
        } finally {
            remove.disabled = false;
        }
    });

    const userForm = document.getElementById('user-form');
    userForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const url = endpoint('users');
        if (!url) return;
        const editId = userForm.dataset.editId || '';
        const data = cleanFormObject(userForm);
        data.is_activated = userForm.elements.is_activated?.checked === true;
        if (editId && !data.pin) delete data.pin;
        const submit = userForm.querySelector('button[type="submit"]');
        if (submit) submit.disabled = true;
        try {
            await request(editId ? `${url}/${encodeURIComponent(editId)}` : url, { method: editId ? 'PATCH' : 'POST', body: data });
            userForm.reset();
            userForm.elements.is_activated.checked = true;
            userForm.elements.pin.required = true;
            delete userForm.dataset.editId;
            toast(text('User saved successfully.', 'ইউজার সফলভাবে সংরক্ষণ হয়েছে।'));
            await loadResource('users');
        } catch (error) {
            toast(error.message, true);
        } finally {
            if (submit) submit.disabled = false;
        }
    });

    document.addEventListener('click', (event) => {
        const edit = event.target.closest('[data-edit-resource="users"]');
        if (!edit || !userForm) return;
        const id = Number(edit.dataset.editId);
        const record = state.users.find((item) => recordId(item) === id);
        if (!record) return;
        userForm.dataset.editId = String(id);
        ['name', 'mobile', 'email', 'role'].forEach((name) => {
            if (userForm.elements[name]) userForm.elements[name].value = record[name] ?? '';
        });
        userForm.elements.pin.value = '';
        userForm.elements.pin.required = false;
        userForm.elements.is_activated.checked = Boolean(record.is_activated);
        userForm.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'center' });
        event.stopPropagation();
    }, true);

    const updateBrandText = (settings) => {
        if (!settings) return;
        if (settings.app_name) document.querySelectorAll('.brand-name').forEach((node) => { node.textContent = settings.app_name; });
        const captain = settings.captain_name || text('Financial Operations', 'আর্থিক ব্যবস্থাপনা');
        document.querySelectorAll('.brand-caption').forEach((node) => { node.textContent = captain; });
    };

    const configForm = document.getElementById('config-form');
    configForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const url = app.dataset.configUrl;
        if (!url) return;
        const data = cleanFormObject(configForm);
        ['rate_based_mode', 'supplier_rate_enabled', 'wallet_rate_enabled'].forEach((name) => {
            data[name] = configForm.elements[name]?.checked === true;
        });
        try {
            const payload = await request(url, { method: 'POST', body: data });
            updateBrandText(payload.settings);
            toast(text('System settings updated.', 'সিস্টেম সেটিংস আপডেট হয়েছে।'));
        } catch (error) { toast(error.message, true); }
    });

    const logoForm = document.getElementById('logo-form');
    logoForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const url = app.dataset.logoUrl;
        if (!url) return;
        const data = new FormData(logoForm);
        try {
            const payload = await request(url, { method: 'POST', body: data });
            const source = payload.app_logo_path || payload.app_logo_url || payload.url;
            if (source) {
                const separator = source.includes('?') ? '&' : '?';
                document.querySelectorAll('.brand-logo').forEach((image) => { image.src = `${source}${separator}v=${Date.now()}`; });
            }
            logoForm.reset();
            toast(text('Logo uploaded and applied.', 'লোগো আপলোড ও প্রয়োগ হয়েছে।'));
        } catch (error) { toast(error.message, true); }
    });

    const personalForm = document.getElementById('personal-settings-form');
    personalForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            await request(app.dataset.personalSettingsUrl, { method: 'POST', body: cleanFormObject(personalForm) });
            window.location.reload();
        } catch (error) { toast(error.message, true); }
    });

    const applyTheme = (theme) => {
        if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme;
        else delete document.documentElement.dataset.theme;
        try { window.localStorage.setItem('safa-web-theme', theme); } catch (_) { }
    };

    const themeForm = document.getElementById('theme-form');
    let storedTheme = 'system';
    try { storedTheme = window.localStorage.getItem('safa-web-theme') || 'system'; } catch (_) { }
    if (!['system', 'light', 'dark'].includes(storedTheme)) storedTheme = 'system';
    applyTheme(storedTheme);
    if (themeForm?.elements.theme) themeForm.elements.theme.value = storedTheme;
    themeForm?.addEventListener('submit', (event) => {
        event.preventDefault();
        applyTheme(themeForm.elements.theme.value);
        toast(text('Appearance updated.', 'চেহারা আপডেট হয়েছে।'));
    });

    const pinForm = document.getElementById('pin-form');
    pinForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            await request(app.dataset.pinSettingsUrl, { method: 'POST', body: cleanFormObject(pinForm) });
            pinForm.reset();
            toast(text('PIN changed successfully. Other sessions were revoked.', 'পিন পরিবর্তন হয়েছে। অন্যান্য সেশন বাতিল করা হয়েছে।'));
        } catch (error) { toast(error.message, true); }
    });

    document.getElementById('account-select')?.addEventListener('change', async (event) => {
        const accountId = Number(event.target.value);
        if (!accountId) return;
        event.target.disabled = true;
        try {
            const payload = await request(app.dataset.accountSwitchUrl, { method: 'POST', body: { account_id: accountId } });
            app.dataset.activeAccount = String(payload.active_account_id || accountId);
            document.getElementById('global-message')?.classList.add('hidden');
            ['customers', 'suppliers', 'transactions', 'wallet-ledgers', 'wallet-batches', 'supplier-deposits', 'expenses'].forEach((key) => { state[key] = []; });
            state.transactionTotal = 0;
            closeProfiles();
            await loadAll();
            toast(text('Business account switched.', 'ব্যবসার অ্যাকাউন্ট পরিবর্তন হয়েছে।'));
        } catch (error) {
            toast(error.message, true);
        } finally {
            event.target.disabled = false;
        }
    });

    document.querySelector('[data-action="refresh-all"]')?.addEventListener('click', () => loadAll());

    const updateReports = () => {
        const sar = state.transactions.reduce((sum, item) => sum + toMinorUnits(item.amount_sar ?? item.amount), 0n);
        const bdt = state.transactions.reduce((sum, item) => sum + toMinorUnits(item.amount_bdt), 0n);
        const expenses = state.expenses.filter((item) => Boolean(item.is_expense)).reduce((sum, item) => sum + toMinorUnits(item.amount), 0n);
        const income = state.expenses.filter((item) => !Boolean(item.is_expense)).reduce((sum, item) => sum + toMinorUnits(item.amount), 0n);
        setStat('report-sar', formatMinorUnits(sar));
        setStat('report-bdt', formatMinorUnits(bdt));
        setStat('report-expense', formatMinorUnits(expenses));
        setStat('report-income', formatMinorUnits(income));
    };

    loadAll();
})();
