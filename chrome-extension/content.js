// content.js - Guardian Agent UI (Professional Edition)

console.log("Guardian Agent Active");

// --- Assets (SVGs) ---
const ICONS = {
    SHIELD: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`,
    LOADING: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#eab308" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="spin"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>`,
    SUCCESS: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
    ERROR: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`
};

// --- Styles ---
const style = document.createElement('style');
style.textContent = `
    @keyframes spin { 100% { transform: rotate(360deg); } }
    .guardian-icon-wrapper svg.spin { animation: spin 1s linear infinite; }
    .guardian-icon-wrapper {
        position: absolute;
        right: 8px;
        top: 50%;
        transform: translateY(-50%);
        cursor: pointer;
        z-index: 9999;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 4px;
        border-radius: 4px;
        transition: background-color 0.2s;
    }
    .guardian-icon-wrapper:hover { background-color: rgba(0,0,0,0.05); }
    
    #guardian-modal {
        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
        animation: fadeIn 0.2s ease-out;
    }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
`;
document.head.appendChild(style);

// --- Heuristics ---

function findUsernameField(passwordInput) {
    let prev = passwordInput.previousElementSibling;
    while (prev) {
        if (prev.tagName === 'INPUT' && (prev.type === 'text' || prev.type === 'email' || prev.type === 'tel')) {
            return prev;
        }
        prev = prev.previousElementSibling;
    }

    const allInputs = Array.from(document.querySelectorAll('input'));
    const idx = allInputs.indexOf(passwordInput);
    if (idx > 0) {
        const candidate = allInputs[idx - 1];
        if (candidate.type === 'text' || candidate.type === 'email' || candidate.type === 'tel') {
            return candidate;
        }
    }
    return null;
}

// --- UI Components ---

function createSelectionModal(credentials, onSelect) {
    const existing = document.getElementById("guardian-modal");
    if (existing) existing.remove();

    const modal = document.createElement("div");
    modal.id = "guardian-modal";
    Object.assign(modal.style, {
        position: "fixed",
        top: "20px",
        right: "20px",
        backgroundColor: "#ffffff",
        color: "#1e293b",
        padding: "0",
        borderRadius: "8px",
        boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)",
        zIndex: "100000",
        minWidth: "280px",
        border: "1px solid #e2e8f0",
        overflow: "hidden"
    });

    // Header
    const header = document.createElement("div");
    Object.assign(header.style, {
        padding: "12px 16px",
        backgroundColor: "#f8fafc",
        borderBottom: "1px solid #e2e8f0",
        fontWeight: "600",
        fontSize: "14px",
        color: "#334155",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
    });
    header.innerText = "Select Account";

    const closeBtn = document.createElement("span");
    closeBtn.innerHTML = "&times;";
    Object.assign(closeBtn.style, { cursor: "pointer", fontSize: "18px", color: "#94a3b8" });
    closeBtn.onclick = () => modal.remove();
    header.appendChild(closeBtn);

    modal.appendChild(header);

    // List
    const list = document.createElement("div");
    list.style.padding = "8px";

    credentials.forEach(cred => {
        const item = document.createElement("div");
        Object.assign(item.style, {
            padding: "10px 12px",
            borderRadius: "6px",
            cursor: "pointer",
            display: "flex",
            flexDirection: "column",
            transition: "background-color 0.15s"
        });

        const user = document.createElement("span");
        user.innerText = cred.username;
        Object.assign(user.style, { fontWeight: "500", fontSize: "14px", color: "#0f172a" });

        const app = document.createElement("span");
        app.innerText = cred.appName || "Unknown Service";
        Object.assign(app.style, { fontSize: "12px", color: "#64748b", marginTop: "2px" });

        item.appendChild(user);
        item.appendChild(app);

        item.onmouseenter = () => item.style.backgroundColor = "#f1f5f9";
        item.onmouseleave = () => item.style.backgroundColor = "transparent";

        item.onclick = () => {
            onSelect(cred);
            modal.remove();
        };

        list.appendChild(item);
    });

    modal.appendChild(list);
    document.body.appendChild(modal);
}

function fillCredentials(passwordInput, username, password) {
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input', { bubbles: true }));
    passwordInput.dispatchEvent(new Event('change', { bubbles: true }));

    const usernameInput = findUsernameField(passwordInput);
    if (usernameInput) {
        usernameInput.value = username;
        usernameInput.dispatchEvent(new Event('input', { bubbles: true }));
        usernameInput.dispatchEvent(new Event('change', { bubbles: true }));
        console.log("Username filled");
    }
}

// --- Main Injection ---

function injectGuardian() {
    const passwordInputs = document.querySelectorAll('input[type="password"]');
    if (passwordInputs.length === 0) return;

    passwordInputs.forEach(input => {
        if (input.dataset.guardianInjected) return;
        input.dataset.guardianInjected = "true";

        const wrapper = document.createElement("div");
        wrapper.className = "guardian-icon-wrapper";
        wrapper.innerHTML = ICONS.SHIELD;
        wrapper.title = "Guardian Identity Agent";

        const parent = input.parentElement;
        if (window.getComputedStyle(parent).position === "static") {
            parent.style.position = "relative";
        }
        parent.appendChild(wrapper);

        wrapper.onclick = (e) => {
            e.preventDefault();
            e.stopPropagation();
            wrapper.innerHTML = ICONS.LOADING;

            let service = window.location.hostname;
            if (service.includes("google")) service = "gmail";
            else if (service.includes("netflix")) service = "netflix";
            else if (service.includes("facebook")) service = "facebook";
            else if (service.includes("roblox")) service = "roblox";

            console.log(`Requesting access: ${service}`);

            try {
                chrome.runtime.sendMessage({ type: "START_LOGIN", service: service }, (response) => {
                    if (chrome.runtime.lastError) {
                        console.error("Connection Error:", chrome.runtime.lastError);
                        alert("Extension updated. Please refresh the page.");
                        wrapper.innerHTML = ICONS.ERROR;
                        return;
                    }

                    if (response && response.success) {
                        console.log("Credentials received");
                        wrapper.innerHTML = ICONS.SUCCESS;

                        let creds = [];
                        try {
                            const raw = response.credentials;
                            if (!raw) throw new Error("Empty Response");
                            if (raw.startsWith("ERROR:")) {
                                wrapper.innerHTML = ICONS.ERROR;
                                alert("Secure Decryption Failed: " + raw);
                                return;
                            }

                            if (raw.startsWith("{") || raw.startsWith("[")) {
                                const json = JSON.parse(raw);
                                if (Array.isArray(json)) creds = json;
                                else creds = [json];
                            } else {
                                creds = [{ username: "Unknown", password: raw, appName: "Legacy" }];
                            }
                        } catch (e) {
                            console.error("Parse Error", e);
                            wrapper.innerHTML = ICONS.ERROR;
                            alert("Data Integrity Error");
                            return;
                        }

                        if (creds.length === 0) {
                            alert("No suitable credentials found on device.");
                            wrapper.innerHTML = ICONS.SHIELD;
                            return;
                        }

                        if (creds.length === 1) {
                            fillCredentials(input, creds[0].username, creds[0].password);
                        } else {
                            createSelectionModal(creds, (selected) => {
                                fillCredentials(input, selected.username, selected.password);
                            });
                        }

                        setTimeout(() => wrapper.innerHTML = ICONS.SHIELD, 2000);
                    } else {
                        console.error("Request failed:", response ? response.error : "Unknown");
                        wrapper.innerHTML = ICONS.ERROR;
                    }
                });
            } catch (err) {
                console.error("Context Invalidated:", err);
                alert("Extension updated. Please refresh the page.");
                wrapper.innerHTML = ICONS.ERROR;
            }
        };
    });

    // --- OTP Injection (Enhanced) ---
    // Expanded selectors to catch more OTP fields
    const otpSelectors = [
        'input[autocomplete="one-time-code"]',
        'input[name*="otp" i]', 'input[id*="otp" i]',
        'input[name*="2fa" i]', 'input[id*="2fa" i]',
        'input[name*="verification" i]', 'input[id*="verification" i]',
        'input[name*="code" i]', 'input[id*="code" i]',
        'input[placeholder*="code" i]', 'input[placeholder*="otp" i]', 'input[placeholder*="verification" i]',
        'input[aria-label*="code" i]', 'input[aria-label*="otp" i]',
        // New Heuristics for "6-digit boxes"
        'input[inputmode="numeric"]',
        'input[type="tel"]',
        'input[maxlength="6"]'
    ];

    const otpInputs = document.querySelectorAll(otpSelectors.join(','));
    otpInputs.forEach(input => {
        if (input.dataset.guardianInjected) return;
        input.dataset.guardianInjected = "true";

        injectShield(input, "Auto-Fill OTP", async (wrapper) => {
            console.log("Requesting OTP...");
            chrome.runtime.sendMessage({ type: "START_LOGIN", service: "otp_request" }, (response) => {
                handleOtpResponse(input, wrapper, response);
            });
        });
    });

    // --- Universal Email/Identity Fallback ---
    // Target inputs that look like they want an Identity (Email, Phone, Username)
    const identitySelectors = [
        'input[type="email"]',
        'input[name*="email" i]', 'input[id*="email" i]',
        'input[placeholder*="email" i]',
        // Broader Identity Selectors (for Netflix, Amazon, etc.)
        'input[name*="user" i]', 'input[id*="user" i]', // matches userLoginId, username
        'input[name*="login" i]', 'input[id*="login" i]',
        'input[autocomplete="email"]',
        'input[autocomplete="username"]',
        'input[autocomplete="tel"]'
    ];

    // Check if we are on a login page but NOT injected yet
    const identityInputs = document.querySelectorAll(identitySelectors.join(','));
    identityInputs.forEach(input => {
        if (input.dataset.guardianInjected) return;
        // Don't inject if it's already got a value
        if (input.value && input.value.length > 5) return;

        input.dataset.guardianInjected = "true";

        injectShield(input, "Auto-Fill Default Identity", async (wrapper) => {
            console.log("Requesting Default Identity...");
            chrome.runtime.sendMessage({ type: "START_LOGIN", service: "identity_request" }, (response) => {
                handleIdentityResponse(input, wrapper, response, "email");
            });
        });
    });
}

// Reuseable Injection Helper
function injectShield(input, title, onClick) {
    const wrapper = document.createElement("div");
    wrapper.className = "guardian-icon-wrapper";
    wrapper.innerHTML = ICONS.SHIELD;
    wrapper.title = title;

    const parent = input.parentElement;
    if (window.getComputedStyle(parent).position === "static") {
        parent.style.position = "relative";
    }
    parent.appendChild(wrapper);

    wrapper.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        wrapper.innerHTML = ICONS.LOADING;
        onClick(wrapper);
    };
}

function handleOtpResponse(input, wrapper, response) {
    if (response && response.success) {
        try {
            const json = JSON.parse(response.credentials);
            const code = json.otp;
            if (code) {
                input.value = code;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                wrapper.innerHTML = ICONS.SUCCESS;
                setTimeout(() => wrapper.innerHTML = ICONS.SHIELD, 3000);
            } else {
                alert("No recent OTP found on phone.");
                wrapper.innerHTML = ICONS.SHIELD;
            }
        } catch (err) {
            console.error(err);
            wrapper.innerHTML = ICONS.ERROR;
        }
    } else {
        wrapper.innerHTML = ICONS.ERROR;
        alert("Failed to reach mobile agent.");
    }
}

function handleIdentityResponse(input, wrapper, response, type) {
    if (response && response.success) {
        try {
            const json = JSON.parse(response.credentials);
            const email = json.email;
            const phone = json.phone;

            // Format as "credentials" for the selection modal
            const choices = [];
            if (email) choices.push({ username: email, appName: "Email Address", password: email });
            if (phone) choices.push({ username: phone, appName: "Mobile Number", password: phone });

            if (choices.length === 0) {
                alert("No identity details found on phone.");
                wrapper.innerHTML = ICONS.SHIELD;
                return;
            }

            // Always show modal for "Identity" so user can pick
            wrapper.innerHTML = ICONS.SUCCESS;
            createSelectionModal(choices, (selected) => {
                input.value = selected.password; // Using 'password' field as value carrier
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                setTimeout(() => wrapper.innerHTML = ICONS.SHIELD, 1000);
            });

        } catch (err) {
            console.error(err);
            wrapper.innerHTML = ICONS.ERROR;
        }
    } else {
        wrapper.innerHTML = ICONS.ERROR;
    }
}

injectGuardian();
const observer = new MutationObserver(injectGuardian);
observer.observe(document.body, { childList: true, subtree: true });
