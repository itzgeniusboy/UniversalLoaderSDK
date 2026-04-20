/**
 * OneCore SDK Engine Admin Panel Logic
 * Handles CRUD and Data Persistence via LocalStorage
 */

let customers = JSON.parse(localStorage.getItem('sdk_customers')) || [
    { id: 1, name: "John Developer", key: "CUST_JOHN_001", expiry: "2025-06-01", status: "active", plan: "Pro", amount: 49 },
    { id: 2, name: "Mike Modder", key: "CUST_MIKE_002", expiry: "2025-05-15", status: "active", plan: "Enterprise", amount: 149 },
    { id: 3, name: "Trial User", key: "CUST_TRIAL_001", expiry: "2025-03-20", status: "expired", plan: "Trial", amount: 0 },
    { id: 4, name: "Legacy User", key: "CUST_LEGACY_99", expiry: "2024-01-01", status: "expired", plan: "Basic", amount: 19 }
];

let sales = JSON.parse(localStorage.getItem('sdk_sales')) || [
    { date: "2025-03-10", customer: "John Developer", plan: "Pro", amount: 49 },
    { date: "2025-03-12", customer: "Mike Modder", plan: "Enterprise", amount: 149 }
];

// Check Auth
async function checkAuth() {
    if (!localStorage.getItem('sdk_admin_token')) {
        window.location.href = 'index.html';
    }
}

// Data Persistence
function saveState() {
    localStorage.setItem('sdk_customers', JSON.stringify(customers));
    localStorage.setItem('sdk_sales', JSON.stringify(sales));
}

// Improved Login logic for index.html (if we were in its script)
// But admin.js handles the dashboard.

function logout() {
    localStorage.removeItem('sdk_admin_token');
    window.location.href = 'index.html';
}

// Tab Management
function showTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(t => t.classList.add('hidden'));
    const targetTab = document.getElementById('tab-' + tabId);
    if(targetTab) targetTab.classList.remove('hidden');
    
    document.querySelectorAll('.nav-link').forEach(n => n.classList.remove('active'));
    // Find button that calls this tab
    const btns = document.querySelectorAll('.nav-link');
    btns.forEach(btn => {
        if(btn.innerText.toLowerCase().includes(tabId)) btn.classList.add('active');
    });
    
    if (tabId === 'dashboard') updateDashboard();
    if (tabId === 'customers') renderCustomers();
    if (tabId === 'sales') renderSales();
}

// Dashboard Stats
function updateDashboard() {
    const active = customers.filter(c => c.status === 'active').length;
    const expired = customers.filter(c => c.status === 'expired').length;
    const revenue = sales.reduce((sum, s) => sum + s.amount, 0);

    document.getElementById('stats-total').innerText = customers.length;
    document.getElementById('stats-active').innerText = active;
    document.getElementById('stats-expired').innerText = expired;
    document.getElementById('stats-revenue').innerText = '$' + revenue.toLocaleString();

    // Activity Log
    const log = document.getElementById('recent-activity');
    log.innerHTML = `
        <div class="text-green-500">> System status: ONLINE</div>
        <div class="text-gray-500">> [${new Date().toLocaleTimeString()}] Dashboard synchronized.</div>
        <div class="text-gray-500">> ${active} active licenses found in JSON registry.</div>
    `;
}

// Customer Management
function renderCustomers() {
    const list = document.getElementById('cust-table-body');
    list.innerHTML = '';
    
    const search = document.getElementById('search-cust').value.toLowerCase();
    const filterStatus = document.getElementById('filter-status').value;

    customers.forEach((c, i) => {
        if (search && !c.name.toLowerCase().includes(search) && !c.key.toLowerCase().includes(search)) return;
        if (filterStatus !== 'all' && c.status !== filterStatus) return;

        const row = document.createElement('tr');
        row.innerHTML = `
            <td data-label="Name" class="px-8 py-5 text-sm font-medium text-white">${c.name}</td>
            <td data-label="License Key" class="px-8 py-5 text-xs font-mono text-gray-500 uppercase">${c.key}</td>
            <td data-label="Expiry" class="px-8 py-5 text-sm text-gray-400">${c.expiry || '∞'}</td>
            <td data-label="Days Left" class="px-8 py-5 text-sm font-mono text-gray-500">${calculateDays(c.expiry)}</td>
            <td data-label="Status" class="px-8 py-5">
                <span class="status-badge status-${c.status}">${c.status}</span>
            </td>
            <td class="px-8 py-5 text-right space-x-3">
                <button onclick="editCustomer(${i})" class="text-blue-500 hover:text-blue-400 text-[10px] font-bold uppercase min-h-[44px] min-w-[44px]">Edit</button>
                <button onclick="toggleSuspend(${i})" class="text-yellow-500 hover:text-yellow-400 text-[10px] font-bold uppercase min-h-[44px] min-w-[44px]">${c.status === 'suspended' ? 'Activate' : 'Suspend'}</button>
                <button onclick="deleteCustomer(${i})" class="text-red-500 hover:text-red-400 text-[10px] font-bold uppercase min-h-[44px] min-w-[44px]">Delete</button>
            </td>
        `;
        list.appendChild(row);
    });
}

function calculateDays(expiry) {
    if (!expiry) return '∞';
    const diff = new Date(expiry) - new Date();
    const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
    return days < 0 ? 0 : days;
}

// Modal CRUD
function openModal(type, index = -1) {
    document.getElementById('modal-' + type).classList.remove('hidden');
    if (index > -1) {
        const c = customers[index];
        document.getElementById('cust-modal-title').innerText = "Edit " + c.name;
        document.getElementById('cust-id').value = index;
        document.getElementById('cust-name').value = c.name;
        document.getElementById('cust-key').value = c.key;
        document.getElementById('cust-expiry').value = c.expiry;
        document.getElementById('cust-plan').value = c.plan;
    } else {
        document.getElementById('cust-modal-title').innerText = "Add New License";
        document.getElementById('cust-form').reset();
        document.getElementById('cust-key').value = 'CUST_' + Math.random().toString(36).substr(2, 6).toUpperCase();
        document.getElementById('cust-id').value = -1;
    }
}

function closeModal(type) {
    document.getElementById('modal-' + type).classList.add('hidden');
}

document.getElementById('cust-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const id = document.getElementById('cust-id').value;
    const name = document.getElementById('cust-name').value;
    const key = document.getElementById('cust-key').value;
    const expiry = document.getElementById('cust-expiry').value;
    const plan = document.getElementById('cust-plan').value;

    const newCust = {
        name, key, expiry, plan, 
        status: calculateStatus(expiry),
        amount: getPlanAmount(plan)
    };

    if (id == -1) {
        customers.push(newCust);
        // Track Sale
        sales.push({
            date: new Date().toISOString().split('T')[0],
            customer: name,
            plan: plan,
            amount: newCust.amount
        });
    } else {
        customers[id] = { ...customers[id], ...newCust };
    }

    saveState();
    closeModal('customer');
    renderCustomers();
});

function calculateStatus(expiry) {
    if (!expiry) return 'active';
    return (new Date(expiry) < new Date()) ? 'expired' : 'active';
}

function getPlanAmount(plan) {
    if (plan === 'Basic') return 19;
    if (plan === 'Pro') return 49;
    if (plan === 'Enterprise') return 149;
    return 0;
}

function editCustomer(index) {
    openModal('customer', index);
}

function toggleSuspend(index) {
    customers[index].status = customers[index].status === 'suspended' ? calculateStatus(customers[index].expiry) : 'suspended';
    saveState();
    renderCustomers();
}

function deleteCustomer(index) {
    if (confirm("Permanently remove this customer?")) {
        customers.splice(index, 1);
        saveState();
        renderCustomers();
    }
}

function filterData() {
    renderCustomers();
}

// Sales Table
function renderSales() {
    const list = document.getElementById('sales-table-body');
    list.innerHTML = '';
    sales.forEach(s => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td data-label="Date" class="px-8 py-5 text-sm font-mono text-gray-500">${s.date}</td>
            <td data-label="Customer" class="px-8 py-5 text-sm text-white">${s.customer}</td>
            <td data-label="Plan" class="px-8 py-5 text-sm text-gray-400">${s.plan}</td>
            <td data-label="Amount" class="px-8 py-5 text-sm font-mono text-green-500">$${s.amount}</td>
        `;
        list.prepend(row);
    });
}

// Data Handling
function saveState() {
    localStorage.setItem('sdk_customers', JSON.stringify(customers));
    localStorage.setItem('sdk_sales', JSON.stringify(sales));
}

function backupData() {
    const data = JSON.stringify({ customers, sales }, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `universal_loader_backup_${new Date().toISOString().split('T')[0]}.json`;
    a.click();
}

function exportCSV() {
    let csv = "Date,Customer,Plan,Amount\n";
    sales.forEach(s => {
        csv += `${s.date},"${s.customer}",${s.plan},${s.amount}\n`;
    });
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'sdk_sales_report.csv';
    a.click();
}

// Touch Handlers (Swipe to Delete & Pull to Refresh)
let touchStartTop = 0;
let touchStartX = 0;
const scrollContainer = document.getElementById('scroll-container');
const ptrIndicator = document.getElementById('ptr-indicator');

window.addEventListener('touchstart', (e) => {
    touchStartTop = e.touches[0].clientY;
    touchStartX = e.touches[0].clientX;
});

window.addEventListener('touchmove', (e) => {
    const touchY = e.touches[0].clientY;
    const diff = touchY - touchStartTop;

    if (window.scrollY === 0 && diff > 0) {
        ptrIndicator.style.transform = `translateY(${Math.min(diff, 100)}px)`;
        if (diff > 70) ptrIndicator.innerText = "Release to refresh";
    }
});

window.addEventListener('touchend', (e) => {
    const touchY = e.changedTouches[0].clientY;
    const touchX = e.changedTouches[0].clientX;
    const diffY = touchY - touchStartTop;
    const diffX = touchX - touchStartX;

    // Pull to Refresh Logic
    if (window.scrollY === 0 && diffY > 70) {
        ptrIndicator.innerText = "Refreshing...";
        setTimeout(() => {
            ptrIndicator.style.transform = `translateY(0)`;
            updateDashboard();
            renderCustomers();
            renderSales();
            ptrIndicator.innerText = "Pull down to refresh";
        }, 800);
    } else {
        ptrIndicator.style.transform = `translateY(0)`;
    }

    // Swipe to Delete Detection
    if (Math.abs(diffX) > 100 && Math.abs(diffY) < 30) {
        const target = e.target.closest('tr');
        if (target && target.querySelector('button[onclick*="deleteCustomer"]')) {
            const index = target.querySelector('button[onclick*="deleteCustomer"]').getAttribute('onclick').match(/\d+/)[0];
            if (diffX < -100) { // Swipe Left
                deleteCustomer(index);
            }
        }
    }
});

// Init
window.onload = () => {
    checkAuth();
    updateDashboard();
    // Pre-load if needed
}
