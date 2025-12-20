package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	mrand "math/rand" // Aliased to prevent conflict
)

// Simple random generator for ID since we might not have uuid lib installed in go.mod yet
func generateID() string {
	return fmt.Sprintf("req-%d", mrand.Int63())
}

const BROKER_URL = "https://unexempt-danial-unousted.ngrok-free.dev"

func main() {
	fmt.Println("💻 Guardian Laptop Agent Started (E2EE Enabled)")
	fmt.Println("---------------------------------------------")
	fmt.Println("Simulating a Browser Extension...")

	service := "gmail" // Default
	fmt.Print("Enter Service to login (default: gmail): ")
	fmt.Scanln(&service)

	reqID := generateID()
	source := "MacBook_Pro_16"

	// 1. Generate Ephemeral RSA Keys
	privKey, pubKeyPEM, err := generateRSAKeys()
	if err != nil {
		log.Fatal("Failed to generate keys:", err)
	}
	fmt.Println("🔐 Ephemeral RSA Key Pair generated.")

	// 2. Send Request with Public Key
	requestPayload := map[string]interface{}{
		"requestId": reqID,
		"service":   service,
		"source":    source,
		"status":    "pending",
		"timestamp": time.Now().Unix(),
		"publicKey": pubKeyPEM, // Send Public Key to Phone
	}
	body, _ := json.Marshal(requestPayload)

	fmt.Printf("--> Requesting credentials for '%s' from Mobile...\n", service)
	resp, err := http.Post(BROKER_URL+"/agent/request", "application/json", bytes.NewBuffer(body))
	if err != nil {
		log.Fatal("Failed to connect to Broker:", err)
	}
	resp.Body.Close()

	// 3. Poll for Response
	fmt.Println("... Waiting for approval (Risk Check + Encryption on Phone) ...")

	for {
		time.Sleep(2 * time.Second)
		pollResp, err := http.Get(fmt.Sprintf("%s/agent/poll?requestId=%s", BROKER_URL, reqID))
		if err != nil {
			fmt.Print("x")
			continue
		}

		if pollResp.StatusCode == http.StatusOK {
			var agentResp map[string]interface{}
			json.NewDecoder(pollResp.Body).Decode(&agentResp)
			pollResp.Body.Close()

			encryptedPayload := agentResp["credentials"].(string)
			fmt.Println("\n📦 Received Encrypted Payload.")

			// 4. Decrypt Payload
			decryptedBytes, err := decryptRSA(privKey, encryptedPayload)
			if err != nil {
				fmt.Printf("\n❌ Decryption Failed: %v\n", err)
				break
			}
			rawPayload := string(decryptedBytes)

			// Try to parse as a list of credentials
			type Credential struct {
				Username string `json:"username"`
				Password string `json:"password"`
				AppName  string `json:"appName"`
			}
			var creds []Credential

			// It might be a simple string (backward compatibility) or a JSON list
			if err := json.Unmarshal([]byte(rawPayload), &creds); err != nil {
				// Fallback: It's just a raw password string
				fmt.Println("✅ Decrypted! Single Credential Received.")
				fmt.Printf("--> Auto-Filling: %s\n", rawPayload)
			} else {
				// It is a LIST of credentials
				fmt.Printf("✅ Decrypted! Received %d Credentials.\n", len(creds))
				if len(creds) == 0 {
					fmt.Println("Warning: List was empty.")
				} else if len(creds) == 1 {
					c := creds[0]
					fmt.Printf("--> Auto-Filling [%s]: %s / %s\n", c.AppName, c.Username, c.Password)
				} else {
					// Multiple Accounts - Ask User
					fmt.Println("Multiple accounts found. Please select:")
					for i, c := range creds {
						fmt.Printf("  [%d] %s (%s)\n", i+1, c.AppName, c.Username)
					}
					fmt.Print("Select Account (Enter Number): ")
					var selection int
					fmt.Scanln(&selection)
					if selection > 0 && selection <= len(creds) {
						c := creds[selection-1]
						fmt.Printf("--> Using: %s / %s\n", c.Username, c.Password)
					} else {
						fmt.Println("Invalid selection. Using first one.")
						c := creds[0]
						fmt.Printf("--> Using: %s / %s\n", c.Username, c.Password)
					}
				}
			}

			fmt.Println("LOGIN SUCCESSFUL.")
			break
		}
		pollResp.Body.Close()
		fmt.Print(".")
	}
}

// --- Crypto Helpers ---

func generateRSAKeys() (*rsa.PrivateKey, string, error) {
	privKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, "", err
	}

	pubKeyBytes, err := x509.MarshalPKIXPublicKey(&privKey.PublicKey)
	if err != nil {
		return nil, "", err
	}

	pubKeyPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "PUBLIC KEY",
		Bytes: pubKeyBytes,
	})

	return privKey, string(pubKeyPEM), nil
}

func decryptRSA(privKey *rsa.PrivateKey, encryptedBase64 string) ([]byte, error) {
	encryptedBytes, err := base64.StdEncoding.DecodeString(encryptedBase64)
	if err != nil {
		return nil, err
	}

	decryptedBytes, err := rsa.DecryptOAEP(
		sha256.New(),
		rand.Reader,
		privKey,
		encryptedBytes,
		nil,
	)
	if err != nil {
		return nil, err
	}

	return decryptedBytes, nil
}
