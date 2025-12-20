// background.js - The "Ghost" Agent Core

const BACKEND_URL = "https://unexempt-danial-unousted.ngrok-free.dev";
let keyPair = null;

// 1. Persistent Keys: Load or Generate
async function loadOrGenerateKeys() {
  try {
    const stored = await chrome.storage.local.get(["privateKeyJwk", "publicKeyJwk"]);

    if (stored.privateKeyJwk && stored.publicKeyJwk) {
      // Import existing keys
      const privateKey = await crypto.subtle.importKey(
        "jwk", stored.privateKeyJwk,
        { name: "RSA-OAEP", hash: "SHA-1" },
        false, ["decrypt"]
      );
      const publicKey = await crypto.subtle.importKey(
        "jwk", stored.publicKeyJwk,
        { name: "RSA-OAEP", hash: "SHA-1" },
        true, ["encrypt"]
      );
      keyPair = { privateKey, publicKey };
      console.log("🔑 Ghost Agent: Identity Loaded from Storage");
    } else {
      // Generate new keys
      keyPair = await crypto.subtle.generateKey(
        {
          name: "RSA-OAEP",
          modulusLength: 2048,
          publicExponent: new Uint8Array([1, 0, 1]),
          hash: "SHA-1",
        },
        true,
        ["encrypt", "decrypt"]
      );

      // Export and Save
      const privateKeyJwk = await crypto.subtle.exportKey("jwk", keyPair.privateKey);
      const publicKeyJwk = await crypto.subtle.exportKey("jwk", keyPair.publicKey);

      await chrome.storage.local.set({ privateKeyJwk, publicKeyJwk });
      console.log("🔑 Ghost Agent: New Identity Created & Saved");
    }
  } catch (e) {
    console.error("Key persistence error:", e);
  }
}

// Convert ArrayBuffer to PEM (for Backend/Go compatibility)
function arrayBufferToPem(buffer, label) {
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  const pem = `-----BEGIN ${label}-----\n${base64}\n-----END ${label}-----\n`;
  return pem;
}

// Convert Base64 to ArrayBuffer (for Decryption)
function base64ToArrayBuffer(base64) {
  const binary_string = w.atob(base64);
  const len = binary_string.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary_string.charCodeAt(i);
  }
  return bytes.buffer;
}

// Initialize
loadOrGenerateKeys();

// Listen for messages from Content Script
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === "START_LOGIN") {
    handleLoginRequest(message.service, sendResponse);
    return true; // Keep channel open for async response
  }
  if (message.type === "GET_IDENTITY") {
    getIdentity(sendResponse);
    return true;
  }
});

async function getIdentity(sendResponse) {
  if (!keyPair) await loadOrGenerateKeys();
  const exportedKey = await crypto.subtle.exportKey("spki", keyPair.publicKey);
  const publicKeyPem = arrayBufferToPem(exportedKey, "PUBLIC KEY");
  sendResponse({ publicKey: publicKeyPem });
}

async function handleLoginRequest(service, sendResponse) {
  if (!keyPair) {
    await loadOrGenerateKeys();
  }

  // Export Public Key to PEM
  const exportedKey = await crypto.subtle.exportKey("spki", keyPair.publicKey);
  const publicKeyPem = arrayBufferToPem(exportedKey, "PUBLIC KEY");

  const reqId = "req-" + Math.floor(Math.random() * 1000000000);
  console.log(`🚀 Requesting Login for: ${service} (ID: ${reqId})`);

  // 1. Post Request to Backend
  try {
    await fetch(`${BACKEND_URL}/agent/request`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        requestId: reqId,
        service: service,
        source: "Chrome_Extension",
        status: "pending",
        timestamp: Date.now(),
        publicKey: publicKeyPem
      })
    });

    // 2. Poll for Response
    pollForResponse(reqId, sendResponse);

  } catch (e) {
    console.error("Network Error:", e);
    sendResponse({ success: false, error: "Network Error" });
  }
}

async function pollForResponse(reqId, sendResponse) {
  let attempts = 0;
  const maxAttempts = 30; // 60 seconds

  const interval = setInterval(async () => {
    attempts++;
    if (attempts > maxAttempts) {
      clearInterval(interval);
      sendResponse({ success: false, error: "Timeout" });
      return;
    }

    try {
      const resp = await fetch(`${BACKEND_URL}/agent/poll?requestId=${reqId}`);
      if (resp.status === 200) {
        const data = await resp.json();
        console.log("📦 Received Encrypted Data:", data);

        // 3. Decrypt
        clearInterval(interval);
        const decrypted = await decryptData(data.credentials);
        sendResponse({ success: true, credentials: decrypted });
      }
    } catch (e) {
      console.log("Polling...", e);
    }
  }, 2000);
}

async function decryptData(encryptedBase64) {
  try {
    // Clean input (remove newlines, whitespace)
    const cleanBase64 = encryptedBase64.replace(/\s/g, '');
    console.log("Cleaned Base64 Length:", cleanBase64.length);

    const encryptedData = Uint8Array.from(atob(cleanBase64), c => c.charCodeAt(0));

    const decrypted = await crypto.subtle.decrypt(
      { name: "RSA-OAEP" },
      keyPair.privateKey,
      encryptedData
    );

    const decoded = new TextDecoder().decode(decrypted);
    console.log("Decrypted:", decoded);
    return decoded;
  } catch (e) {
    console.error("Decryption Failed:", e);
    // FALLBACK: If atob failed, maybe it's just plaintext JSON?
    // We try to parsing it as JSON to see if it's usable.
    try {
      if (encryptedBase64.startsWith("[") || encryptedBase64.startsWith("{")) {
        console.warn("⚠️ Data appears to be Plaintext JSON. Returning as-is.");
        return encryptedBase64;
      }
    } catch (ignore) { }

    return "ERROR: " + e.message + " (Len: " + (encryptedBase64 ? encryptedBase64.length : 0) + ")";
  }
}

