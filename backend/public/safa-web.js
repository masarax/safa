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

    const activatePanel = (name) => {
        document.querySelectorAll('[data-panel]').forEach((panel) => panel.classList.toggle('active', panel.dataset.panel === name));
        document.querySelectorAll('[data-section]').forEach((button) => button.classList.toggle('active', button.dataset.section === name));
        document.getElementById('sidebar')?.classList.remove('open');
        document.getElementById('menu-toggle')?.setAttribute('aria-expanded', 'false');
        window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    };

    document.querySelectorAll('[data-section]').forEach((button) => {
        button.addEventListener('click', () => activatePanel(button.dataset.section));
    });

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

    const actionCell = (resource, record, allowEdit = true, allowDelete = true) => {
        const cell = document.createElement('td');
        const wrap = document.createElement('div');
        wrap.className = 'row-actions';
        const id = Number(record.id || record.server_id || 0);
        if (allowEdit && id > 0) {
            const edit = document.createElement('button');
            edit.type = 'button';
            edit.className = 'button secondary small';
            edit.textContent = text('Edit', 'এডিট');
            edit.dataset.editResource = resource;
            edit.dataset.editId = String(id);
            wrap.appendChild(edit);
        }
        if (allowDelete && id > 0) {
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'button danger small';
            remove.textContent = text('Delete', 'মুছুন');
            remove.dataset.deleteResource = resource;
            remove.dataset.deleteId = String(id);
            wrap.appendChild(remove);
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

    const renderCustomers = () => {
        const body = document.getElementById('customers-body');
        if (!body) return;
        if (!state.customers.length) return emptyRow(body, 4, text('No customers found.', 'কোনো কাস্টমার পাওয়া যায়নি।'));
        body.replaceChildren();
        state.customers.forEach((record) => {
            const row = document.createElement('tr');
            row.append(td(record.name), td(record.phone), td(record.address), actionCell('customers', record));
            body.appendChild(row);
        });
        setStat('stat-customers', state.customers.length);
    };

    const renderSuppliers = () => {
        const body = document.getElementById('suppliers-body');
        if (!body) return;
        if (!state.suppliers.length) return emptyRow(body, 4, text('No suppliers found.', 'কোনো সাপ্লায়ার পাওয়া যায়নি।'));
        body.replaceChildren();
        state.suppliers.forEach((record) => {
            const row = document.createElement('tr');
            row.append(td(record.name), td(record.phone), td(record.address), actionCell('suppliers', record));
            body.appendChild(row);
        });
        setStat('stat-suppliers', state.suppliers.length);
    };

    const renderTransactions = () => {
        const body = document.getElementById('transactions-body');
        if (!body) return;
        if (!state.transactions.length) return emptyRow(body, 6, text('No transactions found.', 'কোনো লেনদেন পাওয়া যায়নি।'));
        body.replaceChildren();
        state.transactions.forEach((record) => {
            const row = document.createElement('tr');
            row.append(
                td(record.id),
                td(record.amount_sar ?? record.amount),
                td(record.amount_bdt),
                td(`${record.customer_rate ?? '0'} / ${record.supplier_rate ?? '0'}`),
                td(record.receiver_name),
                actionCell('transactions', record)
            );
            body.appendChild(row);
        });
        setStat('stat-transactions', state.transactions.length);
        updateReports();
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
        if (!body) return;
        if (!state['supplier-deposits'].length) return emptyRow(body, 6, text('No supplier deposits found.', 'কোনো সাপ্লায়ার ডিপোজিট পাওয়া যায়নি।'));
        body.replaceChildren();
        state['supplier-deposits'].forEach((record) => {
            const row = document.createElement('tr');
            row.append(td(record.id), td(record.supplier_id), td(record.amount_sar), td(record.amount_bdt), td(record.paid_bdt), actionCell('supplier-deposits', record));
            body.appendChild(row);
        });
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

    const loadResource = async (resource) => {
        const url = endpoint(resource);
        if (!url || !app.dataset.activeAccount && resource !== 'users') return;
        try {
            const payload = await request(url);
            state[resource] = listFromPayload(resource, payload);
            renderers[resource]?.();
        } catch (error) {
            toast(error.message, true);
        }
    };

    const enabledResources = () => Object.keys(state).filter((resource) => endpoint(resource));

    const loadAll = async () => {
        if (!app.dataset.activeAccount) return;
        await Promise.all(enabledResources().map((resource) => loadResource(resource)));
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

    const recordId = (record) => Number(record?.id || record?.server_id || 0);

    const fillForm = (resource, record) => {
        const form = document.querySelector(`form[data-resource="${resource}"]`);
        if (!form) return;
        form.dataset.editId = String(recordId(record));
        Array.from(form.elements).forEach((control) => {
            if (!control.name) return;
            if (control.type === 'checkbox') {
                control.checked = Boolean(record[control.name]);
            } else if (Object.prototype.hasOwnProperty.call(record, control.name) && record[control.name] != null) {
                control.value = String(record[control.name]);
            }
        });
        form.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' });
        toast(text('Editing selected record. Save to apply changes.', 'নির্বাচিত রেকর্ড এডিট হচ্ছে। পরিবর্তন প্রয়োগ করতে সংরক্ষণ করুন।'));
    };

    document.querySelectorAll('.resource-form').forEach((form) => {
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const resource = form.dataset.resource;
            const base = endpoint(resource);
            if (!base || !app.dataset.activeAccount) return toast(text('Select an account first.', 'আগে একটি অ্যাকাউন্ট নির্বাচন করুন।'), true);
            const editId = form.dataset.editId || '';
            const button = form.querySelector('button[type="submit"]');
            if (button) button.disabled = true;
            try {
                const payload = cleanFormObject(form);
                await request(editId ? `${base}/${encodeURIComponent(editId)}` : base, {
                    method: editId ? 'PUT' : 'POST',
                    body: payload,
                });
                form.reset();
                delete form.dataset.editId;
                toast(text('Saved successfully.', 'সফলভাবে সংরক্ষণ হয়েছে।'));
                await loadResource(resource);
            } catch (error) {
                toast(error.message, true);
            } finally {
                if (button) button.disabled = false;
            }
        });
    });

    document.addEventListener('click', async (event) => {
        const edit = event.target.closest('[data-edit-resource]');
        if (edit) {
            const resource = edit.dataset.editResource;
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
        const confirmed = window.confirm(text('Delete this record permanently from the active view?', 'সক্রিয় তালিকা থেকে এই রেকর্ড মুছে ফেলবেন?'));
        if (!confirmed) return;
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
        const button = userForm.querySelector('button[type="submit"]');
        if (button) button.disabled = true;
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
            if (button) button.disabled = false;
        }
    });

    const originalFillForm = fillForm;
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
        userForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
        event.stopPropagation();
    }, true);

    const configForm = document.getElementById('config-form');
    configForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const url = app.dataset.configUrl;
        try {
            await request(url, { method: 'POST', body: cleanFormObject(configForm) });
            toast(text('Application settings updated.', 'অ্যাপ সেটিংস আপডেট হয়েছে।'));
        } catch (error) { toast(error.message, true); }
    });

    const logoForm = document.getElementById('logo-form');
    logoForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const url = app.dataset.logoUrl;
        const data = new FormData(logoForm);
        try {
            await request(url, { method: 'POST', body: data });
            logoForm.reset();
            toast(text('Logo uploaded successfully. Reload to see it everywhere.', 'লোগো আপলোড হয়েছে। সব জায়গায় দেখতে পেজ রিলোড করুন।'));
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
            Object.keys(state).forEach((key) => { state[key] = []; });
            await loadAll();
            toast(text('Business account switched.', 'ব্যবসার অ্যাকাউন্ট পরিবর্তন হয়েছে।'));
        } catch (error) {
            toast(error.message, true);
        } finally {
            event.target.disabled = false;
        }
    });

    document.querySelector('[data-action="refresh-all"]')?.addEventListener('click', () => loadAll());

    const decimal = (value) => {
        const number = Number.parseFloat(String(value ?? 0));
        return Number.isFinite(number) ? number : 0;
    };

    const updateReports = () => {
        const sar = state.transactions.reduce((sum, item) => sum + decimal(item.amount_sar ?? item.amount), 0);
        const bdt = state.transactions.reduce((sum, item) => sum + decimal(item.amount_bdt), 0);
        const expenses = state.expenses.filter((item) => Boolean(item.is_expense)).reduce((sum, item) => sum + decimal(item.amount), 0);
        const income = state.expenses.filter((item) => !Boolean(item.is_expense)).reduce((sum, item) => sum + decimal(item.amount), 0);
        setStat('report-sar', sar.toFixed(2));
        setStat('report-bdt', bdt.toFixed(2));
        setStat('report-expense', expenses.toFixed(2));
        setStat('report-income', income.toFixed(2));
    };

    if (app.dataset.activeAccount) {
        loadAll();
    } else if (endpoint('users')) {
        loadResource('users');
    }
})();
