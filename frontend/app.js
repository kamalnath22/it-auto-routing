const API_BASE = "http://localhost:8080";
const TOKEN_KEY = "ticket_jwt_token";
const USER_KEY = "ticket_auth_user";

// DOM elements - Header & Auth
const userBar = document.querySelector("#user-bar");
const userName = document.querySelector("#user-name");
const userRole = document.querySelector("#user-role");
const logoutButton = document.querySelector("#logout-button");
const authSection = document.querySelector("#auth-section");
const appSection = document.querySelector("#app-section");
const authMessage = document.querySelector("#auth-message");

// Auth Tabs
const tabLogin = document.querySelector("#tab-login");
const tabRegister = document.querySelector("#tab-register");
const loginForm = document.querySelector("#login-form");
const registerForm = document.querySelector("#register-form");
const loginEmailInput = document.querySelector("#login-email");
const loginPasswordInput = document.querySelector("#login-password");
const registerNameInput = document.querySelector("#register-name");
const registerEmailInput = document.querySelector("#register-email");
const registerPasswordInput = document.querySelector("#register-password");

// DOM elements - Workspace
const ticketForm = document.querySelector("#ticket-form");
const subjectInput = document.querySelector("#subject");
const descriptionInput = document.querySelector("#description");
const message = document.querySelector("#message");
const ticketsBody = document.querySelector("#tickets-body");
const detail = document.querySelector("#ticket-detail");
const refreshButton = document.querySelector("#refresh-button");
const ticketsTitle = document.querySelector("#tickets-title");

// Tab Switching
tabLogin.addEventListener("click", () => switchAuthTab("login"));
tabRegister.addEventListener("click", () => switchAuthTab("register"));

function switchAuthTab(tab) {
    setAuthMessage("");
    if (tab === "login") {
        tabLogin.classList.add("active");
        tabRegister.classList.remove("active");
        loginForm.hidden = false;
        registerForm.hidden = true;
    } else {
        tabRegister.classList.add("active");
        tabLogin.classList.remove("active");
        registerForm.hidden = false;
        loginForm.hidden = true;
    }
}

// Quick fill helper for demo accounts
window.fillLogin = function(email, password) {
    switchAuthTab("login");
    loginEmailInput.value = email;
    loginPasswordInput.value = password;
    loginPasswordInput.focus();
};

// Login submission
loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setAuthMessage("");
    setButtonLoading("#login-submit-btn", true);

    try {
        const response = await fetch(`${API_BASE}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                email: loginEmailInput.value.trim(),
                password: loginPasswordInput.value
            })
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(readErrorMessage(data));
        }

        saveAuthSession(data);
        loginForm.reset();
        setupAuthenticatedView();
    } catch (error) {
        setAuthMessage(error.message || "Login failed. Check your credentials.", "error");
    } finally {
        setButtonLoading("#login-submit-btn", false);
    }
});

// Registration submission (Strictly registers as EMPLOYEE)
registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setAuthMessage("");
    setButtonLoading("#register-submit-btn", true);

    try {
        const response = await fetch(`${API_BASE}/api/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                name: registerNameInput.value.trim(),
                email: registerEmailInput.value.trim(),
                password: registerPasswordInput.value
            })
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(readErrorMessage(data));
        }

        // Automatic sign-in upon successful registration
        saveAuthSession(data);
        registerForm.reset();
        setupAuthenticatedView();
    } catch (error) {
        setAuthMessage(error.message || "Registration failed.", "error");
    } finally {
        setButtonLoading("#register-submit-btn", false);
    }
});

// Logout handler (Stateless: removes token from sessionStorage)
logoutButton.addEventListener("click", () => {
    logout();
});

function logout(logoutMessage = "") {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    setupUnauthenticatedView();
    if (logoutMessage) {
        setAuthMessage(logoutMessage, "error");
    }
}

function saveAuthSession(authData) {
    sessionStorage.setItem(TOKEN_KEY, authData.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify({
        userId: authData.userId,
        name: authData.name,
        email: authData.email,
        role: authData.role
    }));
}

function getStoredToken() {
    return sessionStorage.getItem(TOKEN_KEY);
}

function getStoredUser() {
    try {
        return JSON.parse(sessionStorage.getItem(USER_KEY));
    } catch (e) {
        return null;
    }
}

// Reusable authenticated fetch wrapper
async function apiFetch(path, options = {}) {
    const token = getStoredToken();
    const headers = new Headers(options.headers || {});

    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    if (options.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers
    });

    if (response.status === 401) {
        logout("Session expired or invalid token. Please log in again.");
        throw new Error("Unauthorized");
    }

    return response;
}

// View State Management
function setupAuthenticatedView() {
    const user = getStoredUser();
    if (!user) {
        setupUnauthenticatedView();
        return;
    }

    authSection.hidden = true;
    appSection.hidden = false;
    userBar.hidden = false;

    userName.textContent = user.name;
    userRole.textContent = user.role;
    userRole.className = `role-badge role-${user.role}`;

    ticketsTitle.textContent = user.role === "EMPLOYEE" ? "My Tickets" : "All Tickets";

    setMessage("");
    loadTickets();
}

function setupUnauthenticatedView() {
    authSection.hidden = false;
    appSection.hidden = true;
    userBar.hidden = true;
    detail.hidden = true;
    ticketsBody.innerHTML = "";
    setMessage("");
}

// Ticket Form submission
ticketForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setMessage("");
    setButtonLoading("#ticket-submit-btn", true);

    try {
        const response = await apiFetch("/api/tickets", {
            method: "POST",
            body: JSON.stringify({
                subject: subjectInput.value.trim(),
                description: descriptionInput.value.trim()
            })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(readErrorMessage(data));
        }

        ticketForm.reset();
        setMessage(`Ticket #${data.id} created successfully.`, "success");
        await loadTickets();
        await showTicket(data.id);
    } catch (error) {
        if (error.message !== "Unauthorized") {
            setMessage(error.message || "Ticket could not be created.", "error");
        }
    } finally {
        setButtonLoading("#ticket-submit-btn", false);
    }
});

refreshButton.addEventListener("click", loadTickets);

async function loadTickets() {
    try {
        const response = await apiFetch("/api/tickets");
        const tickets = await response.json();

        if (!response.ok) {
            throw new Error(readErrorMessage(tickets));
        }

        renderTickets(tickets);
    } catch (error) {
        if (error.message !== "Unauthorized") {
            ticketsBody.innerHTML = `<tr><td colspan="5">${escapeHtml(error.message || "Tickets could not be loaded.")}</td></tr>`;
        }
    }
}

async function showTicket(id) {
    try {
        const response = await apiFetch(`/api/tickets/${id}`);
        const ticket = await response.json();

        if (!response.ok) {
            throw new Error(readErrorMessage(ticket));
        }

        detail.hidden = false;
        detail.innerHTML = `
            <h3>Ticket #${ticket.id}: ${escapeHtml(ticket.subject)}</h3>
            <p><strong>Status:</strong> <span class="status-badge status-${escapeHtml(ticket.status)}">${escapeHtml(ticket.status)}</span></p>
            <p><strong>Owner:</strong> ${escapeHtml(ticket.userEmail || "System")}</p>
            <p><strong>Created:</strong> ${formatDate(ticket.createdAt)}</p>
            <p><strong>Description:</strong></p>
            <p>${escapeHtml(ticket.description)}</p>
        `;
    } catch (error) {
        if (error.message !== "Unauthorized") {
            setMessage(error.message || "Ticket details could not be loaded.", "error");
        }
    }
}

function renderTickets(tickets) {
    if (!Array.isArray(tickets) || tickets.length === 0) {
        ticketsBody.innerHTML = `<tr><td colspan="5">No tickets found.</td></tr>`;
        detail.hidden = true;
        return;
    }

    ticketsBody.innerHTML = tickets.map((ticket) => `
        <tr data-id="${ticket.id}">
            <td><strong>#${ticket.id}</strong></td>
            <td>${escapeHtml(ticket.subject)}</td>
            <td><span class="status-badge status-${escapeHtml(ticket.status)}">${escapeHtml(ticket.status)}</span></td>
            <td>${escapeHtml(ticket.userEmail || "System")}</td>
            <td>${formatDate(ticket.createdAt)}</td>
        </tr>
    `).join("");

    ticketsBody.querySelectorAll("tr").forEach((row) => {
        row.addEventListener("click", () => showTicket(row.dataset.id));
    });
}

function setButtonLoading(buttonSelector, isLoading) {
    const btn = document.querySelector(buttonSelector);
    if (btn) {
        btn.disabled = isLoading;
    }
}

function setMessage(text, type = "") {
    message.textContent = text;
    message.className = `message ${type}`.trim();
}

function setAuthMessage(text, type = "") {
    authMessage.textContent = text;
    authMessage.className = `message ${type}`.trim();
}

function readErrorMessage(body) {
    if (body.errors) {
        return Object.values(body.errors).join(" ");
    }
    return body.message || "Request failed.";
}

function formatDate(value) {
    if (!value) return "N/A";
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#039;");
}

// Initial bootstrap check
if (getStoredToken() && getStoredUser()) {
    setupAuthenticatedView();
} else {
    setupUnauthenticatedView();
}
