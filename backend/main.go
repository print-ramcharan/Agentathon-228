package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"os"
	"sync"

	"github.com/guardian/mesh-backend/crypto"
	"golang.org/x/crypto/bcrypt" // Requires 'go get golang.org/x/crypto/bcrypt'
)

// --- Configuration ---
const dbFile = "users.json"

// MASTER_KEY for AES Encryption (In prod, use Vault/Env Var)
// 32 bytes for AES-256
var MASTER_KEY = []byte("01234567890123456789012345678901")

// --- Data Structures ---

type User struct {
	Email              string `json:"email"`
	PasswordHash       string `json:"passwordHash"` // secure
	Mobile             string `json:"mobile"`
	PublicKey          string `json:"publicKey"`
	EncryptedFaceData  string `json:"encryptedFaceData"` // AES
	Signature          string `json:"signature"`
	EncryptedEmbedding string `json:"encryptedEmbedding"` // AES(json([]float64))
}

type LoginRequest struct {
	Email         string    `json:"email"`
	Password      string    `json:"password"`
	Signature     string    `json:"signature"`
	Challenge     string    `json:"challenge"`
	FaceEmbedding []float64 `json:"faceEmbedding"` // Live embedding from device
	PublicKey     string    `json:"publicKey"`     // Device's Public Key (for rotation)
}

type ChallengeResponse struct {
	Challenge string `json:"challenge"`
}

type VerifyRequest struct {
	DeviceID  string  `json:"deviceId"`
	RiskScore float64 `json:"riskScore"`
	Signature string  `json:"signature"`
	Challenge string  `json:"challenge"`
	PublicKey string  `json:"publicKey"`
}

// Check crypto/VerifySignature signature.
// Ideally usage: crypto.VerifySignature(pubKey, challenge, signature)

var (
	users = make(map[string]User)
	mutex = &sync.Mutex{}
)

func main() {
	fmt.Println("Starting Guardian Mesh Gatekeeper (SECURE MODE)...")
	loadUsers()

	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "Guardian Gatekeeper is Online")
	})

	http.HandleFunc("/auth/challenge", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		challenge, err := crypto.GenerateChallenge()
		if err != nil {
			http.Error(w, "Failed to generate challenge", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(ChallengeResponse{Challenge: challenge})
	})

	http.HandleFunc("/auth/verify", func(w http.ResponseWriter, r *http.Request) {
		// Existing Risk Score Logic ...
		if r.Method != http.MethodPost {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var req VerifyRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Invalid request", http.StatusBadRequest)
			return
		}

		// NOTE: 'riskScore' field now carries the TRUST SCORE (0.0 to 1.0, Higher is Better)
		// We use the same field name for backward compatibility during migration.
		trustScore := req.RiskScore

		log.Printf("Received verification request for DeviceID: %s with Trust Score: %.2f", req.DeviceID, trustScore)

		if trustScore < 0.5 { // Threshold for Trust
			log.Printf("Trust Score too low (%.2f < 0.5). Auth Denied.", trustScore)
			http.Error(w, "Trust Score too low. Auth Denied.", http.StatusForbidden)
			return
		}

		// For verification endpoint, we just say successful if trust is high
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("Verified"))
	})

	http.HandleFunc("/register", registerHandler)
	http.HandleFunc("/auth/login", loginHandler)

	// --- Cloud Broker Endpoints ---
	http.HandleFunc("/agent/request", agentRequestHandler)
	http.HandleFunc("/agent/poll", agentPollHandler)
	http.HandleFunc("/agent/pending", agentPendingHandler)
	http.HandleFunc("/agent/respond", agentRespondHandler)
	http.HandleFunc("/agent/alert", agentAlertHandler)

	log.Println("Listening on :8080")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		log.Fatal(err)
	}
}

// --- Handlers ---

func registerHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Temporary struct to decode request including Plaintext fields
	type RegisterRequest struct {
		Email         string    `json:"email"`
		Password      string    `json:"password"`
		Mobile        string    `json:"mobile"`
		PublicKey     string    `json:"publicKey"`
		FaceData      string    `json:"faceData"` // Plain Base64 from App
		Signature     string    `json:"signature"`
		FaceEmbedding []float64 `json:"faceEmbedding"` // Plain vector
	}

	var req RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	mutex.Lock()
	defer mutex.Unlock()

	if _, exists := users[req.Email]; exists {
		// Allow Overwrite for Demo purposes (User requested Re-Register)
		fmt.Printf("Updating existing user: %s\n", req.Email)
	}

	// 1. Hash Password
	hash, err := hashPassword(req.Password)
	if err != nil {
		http.Error(w, "Password hashing failed", http.StatusInternalServerError)
		return
	}

	// 2. Encrypt Face Data
	encFaceData, err := encrypt(req.FaceData)
	if err != nil {
		http.Error(w, "Encryption failed", http.StatusInternalServerError)
		return
	}

	// 3. Encrypt Embedding
	embBytes, _ := json.Marshal(req.FaceEmbedding)
	encEmbedding, err := encrypt(string(embBytes))
	if err != nil {
		http.Error(w, "Embedding encryption failed", http.StatusInternalServerError)
		return
	}

	// Store
	user := User{
		Email:              req.Email,
		PasswordHash:       hash, // Stored Hash
		Mobile:             req.Mobile,
		PublicKey:          req.PublicKey,
		EncryptedFaceData:  encFaceData, // Stored Encrypted
		Signature:          req.Signature,
		EncryptedEmbedding: encEmbedding, // Stored Encrypted
	}

	users[req.Email] = user
	saveUsers()

	fmt.Printf("Registered User: %s (Secure)\n", req.Email)
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"message": "Registration successful"})
}

func loginHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var loginReq LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&loginReq); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	mutex.Lock()
	user, exists := users[loginReq.Email]
	// We do NOT Unlock yet because we might Modify the user (Update Public Key)
	// But defer is safer. We'll handle modification carefully.
	defer mutex.Unlock()

	if !exists {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	// 1. Verify Password Hash
	if !checkPasswordHash(loginReq.Password, user.PasswordHash) {
		fmt.Println("Password mismatch")
		http.Error(w, "Invalid Password", http.StatusUnauthorized)
		return
	}

	// 2. Verify Signature
	// Note: We should ideally verify signature against the NEW Public Key if it's a new device,
	// BUT we need to trust the new key first.
	// We trust it via Password + Face.
	// So we can skip signature check against OLD key if we are updating it.
	// However, for security, the signature proves possession of the private key corresponding to the sent Public Key.
	// So we verify: VerifySignature(loginReq.PublicKey, loginReq.Challenge, loginReq.Signature)
	// If loginReq.PublicKey is missing, use user.PublicKey.

	pubKeyToVerify := loginReq.PublicKey
	if pubKeyToVerify == "" {
		pubKeyToVerify = user.PublicKey
	}

	validSig, err := crypto.VerifySignature(pubKeyToVerify, loginReq.Challenge, loginReq.Signature)
	if err != nil || !validSig {
		fmt.Printf("Signature Invalid. Err: %v\n", err)
		http.Error(w, "Invalid Signature/Key", http.StatusUnauthorized)
		return
	}

	// 3. Verify Face (Decrypt -> Metric -> Compare)
	decEmbStr, err := decrypt(user.EncryptedEmbedding)
	if err != nil {
		fmt.Printf("Decryption Error: %v\n", err)
		http.Error(w, "Server Data Corruption", http.StatusInternalServerError)
		return
	}

	var storedEmbedding []float64
	json.Unmarshal([]byte(decEmbStr), &storedEmbedding)

	distance := euclideanDistance(storedEmbedding, loginReq.FaceEmbedding)
	fmt.Printf("User: %s, Face Distance: %f\n", loginReq.Email, distance)

	if distance > 1.1 {
		http.Error(w, fmt.Sprintf("Face Verification Failed. Distance: %.3f", distance), http.StatusUnauthorized)
		return
	}

	// --- AUTH SUCCEEDED ---

	// 4. Multi-Device Logic: Update Public Key if different
	if loginReq.PublicKey != "" && loginReq.PublicKey != user.PublicKey {
		fmt.Printf("Rotating Public Key for user %s to new device key.\n", user.Email)
		user.PublicKey = loginReq.PublicKey
		users[user.Email] = user // Update map
		saveUsers()              // Persist
	}

	fmt.Printf("User %s logged in successfully.\n", loginReq.Email)
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "logged_in", "distance": fmt.Sprintf("%f", distance)})
}

// --- Helpers ---

func hashPassword(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), 14)
	return string(bytes), err
}

func checkPasswordHash(password, hash string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
	return err == nil
}

func encrypt(plaintext string) (string, error) {
	block, err := aes.NewCipher(MASTER_KEY)
	if err != nil {
		return "", err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err = io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}

	ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return hex.EncodeToString(ciphertext), nil
}

func decrypt(encryptedHex string) (string, error) {
	data, err := hex.DecodeString(encryptedHex)
	if err != nil {
		return "", err
	}

	block, err := aes.NewCipher(MASTER_KEY)
	if err != nil {
		return "", err
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	nonceSize := gcm.NonceSize()
	if len(data) < nonceSize {
		return "", fmt.Errorf("ciphertext too short")
	}

	nonce, ciphertext := data[:nonceSize], data[nonceSize:]
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return "", err
	}

	return string(plaintext), nil
}

func euclideanDistance(a, b []float64) float64 {
	if len(a) != len(b) || len(a) == 0 {
		return 999.0
	}
	var sum float64
	for i := range a {
		diff := a[i] - b[i]
		sum += diff * diff
	}
	return math.Sqrt(sum)
}

func saveUsers() {
	file, _ := json.MarshalIndent(users, "", " ")
	_ = os.WriteFile(dbFile, file, 0644)
	fmt.Println("💾 Database saved to users.json (Secure)")
}

func loadUsers() {
	file, err := os.ReadFile(dbFile)
	if err == nil {
		_ = json.Unmarshal(file, &users)
		fmt.Printf("📂 Loaded %d users\n", len(users))
	}
}

// --- Cloud Broker Logic ---

var (
	// Map[RequestID]RequestData
	agentRequests = make(map[string]AgentRequest)
	// Map[RequestID]ResponseData
	agentResponses = make(map[string]AgentResponse)
	brokerMutex    = &sync.Mutex{}
)

type AgentRequest struct {
	RequestID string `json:"requestId"`
	Service   string `json:"service"` // e.g. "gmail", "netflix"
	Source    string `json:"source"`  // e.g. "laptop_1"
	Status    string `json:"status"`  // "pending", "fulfilled"
	Timestamp int64  `json:"timestamp"`
	PublicKey string `json:"publicKey"` // Device's Public Key for E2EE
}

type AgentResponse struct {
	RequestID   string `json:"requestId"`
	Credentials string `json:"credentials"` // Encrypted ideally, simple string for simulation
}

func agentRequestHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req AgentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid Body", http.StatusBadRequest)
		return
	}
	req.Status = "pending"

	brokerMutex.Lock()
	agentRequests[req.RequestID] = req
	brokerMutex.Unlock()

	log.Printf("☁️ Broker: New Request from %s for %s (ID: %s)\n", req.Source, req.Service, req.RequestID)
	w.WriteHeader(http.StatusOK)
}

func agentPendingHandler(w http.ResponseWriter, r *http.Request) {
	// Mobile App calls this to see if anyone needs help
	brokerMutex.Lock()
	defer brokerMutex.Unlock()

	var pending []AgentRequest
	for _, req := range agentRequests {
		if req.Status == "pending" {
			pending = append(pending, req)
		}
	}
	json.NewEncoder(w).Encode(pending)
}

func agentRespondHandler(w http.ResponseWriter, r *http.Request) {
	// Mobile App calls this to PROVIDE the password
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var resp AgentResponse
	if err := json.NewDecoder(r.Body).Decode(&resp); err != nil {
		http.Error(w, "Invalid Body", http.StatusBadRequest)
		return
	}

	brokerMutex.Lock()
	if _, exists := agentRequests[resp.RequestID]; exists {
		agentResponses[resp.RequestID] = resp
		// Update status
		req := agentRequests[resp.RequestID]
		req.Status = "fulfilled"
		agentRequests[resp.RequestID] = req
		log.Printf("☁️ Broker: Received Response for %s\n", resp.RequestID)
	}
	brokerMutex.Unlock()

	w.WriteHeader(http.StatusOK)
}

func agentPollHandler(w http.ResponseWriter, r *http.Request) {
	// Laptop calls this to wait for the password
	id := r.URL.Query().Get("requestId")
	if id == "" {
		http.Error(w, "Missing requestId", http.StatusBadRequest)
		return
	}

	brokerMutex.Lock()
	resp, exists := agentResponses[id]
	brokerMutex.Unlock()

	if exists {
		json.NewEncoder(w).Encode(resp)
	} else {
		// Long polling simulation: Return 404/Empty if not ready, Client retries.
		// For simplicity, we just say "Wait".
		w.WriteHeader(http.StatusNoContent)
	}
}

func agentAlertHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var alert map[string]interface{}
	if err := json.NewDecoder(r.Body).Decode(&alert); err != nil {
		http.Error(w, "Invalid Body", http.StatusBadRequest)
		return
	}

	log.Printf("🚨 ALERT RECEIVED: %v\n", alert)
	w.WriteHeader(http.StatusOK)
}
