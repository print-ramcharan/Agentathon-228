package com.guardian.mesh.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.mesh.GuardianService
import com.guardian.mesh.autofill.CredentialVault

class GuardianAccessibilityService : AccessibilityService() {

    private val fillCooldowns = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 10000L // 10 seconds cooldown

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GuardianAccess", "Service Connected! Ready to watch.")
        android.widget.Toast.makeText(this, "Guardian Accessibility Active", android.widget.Toast.LENGTH_LONG).show()
    }

    private val browsers = listOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val rootNode = rootInActiveWindow ?: return
        var packageName = rootNode.packageName?.toString() ?: return

        // Universal Access: We now try to help in ANY app
        // But we skip System UI to avoid annoyance
        if (packageName == "com.android.systemui" || packageName == "com.android.launcher3") return

        // Context Detection for Browsers (still useful for specific site credentials)
        val isBrowser = browsers.contains(packageName)
        if (isBrowser) {
            val detectedPackage = detectTargetPackage(rootNode)
            if (detectedPackage != null) {
                Log.d("GuardianAccess", "Browser Context Detected: $packageName -> $detectedPackage")
                packageName = detectedPackage
            }
        }

        // Traverse and find fields
        findAndFill(rootNode, packageName)
    }

    private fun detectTargetPackage(rootNode: AccessibilityNodeInfo): String? {
        // Get all known apps from Vault
        val knownApps = CredentialVault.getAllCredentials()
        
        // Scan text for App Names
        val textContent = getAllText(rootNode).lowercase()
        
        for (cred in knownApps) {
            if (textContent.contains(cred.appName.lowercase())) {
                return cred.packageName
            }
        }
        return null
    }

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                sb.append(getAllText(child))
                child.recycle()
            }
        }
        return sb.toString()
    }

    private var overlayDismissTime: Long = 0
    private val OVERLAY_COOLDOWN_MS = 60000L // 1 minute cooldown after dismiss/cancel

    private fun findAndFill(rootNode: AccessibilityNodeInfo, packageName: String) {
        val credentials = CredentialVault.getCredentials(packageName)
        
        // 1. Check Risk
        val riskEngine = GuardianService.activeRiskEngine
        if (riskEngine != null) {
            val trustScore = riskEngine.calculateTrustScore(0.0f).totalScore
            // Allow if trust is at least moderate (0.4 is max without face, so we allow 0.3+)
            if (trustScore < 0.3f) {
                Log.w("GuardianAccess", "Trust Low ($trustScore). Blocking fill.")
                return
            }
        }

        // 2. OTP Priority Check (Fix for "Overlay appearing during OTP")
        // If we have a fresh OTP, we prioritize filling it over showing the account switcher
        val otp = com.guardian.mesh.otp.OtpRepository.getValidOtp()
        if (otp != null) {
             Log.d("GuardianAccess", "Pending OTP found ($otp). Scanning for OTP fields first...")
             // Run a quick scan just for OTP
             if (scanAndFillOtp(rootNode, otp)) {
                 return // Exit if we successfully filled OTP (no need for Credentials overlay)
             }
        }

        // 3. Username Field Check
        // If no credentials, we check for universal filling (email/phone)
        // If credentials exist, we check if they are needed
        if (!hasEmptyUsernameField(rootNode)) {
            // Case: No username field, maybe OTP field? (Already checked above)
            return
        }

        // 4. Handle Credentials
        if (credentials.isNotEmpty()) {
            if (credentials.size > 1) {
                // Check Overlay Cooldown
                if (System.currentTimeMillis() - overlayDismissTime < OVERLAY_COOLDOWN_MS) {
                    Log.d("GuardianAccess", "Overlay on cooldown. Skipping.")
                    return
                }

                if (android.provider.Settings.canDrawOverlays(this)) {
                    showAccountPicker(credentials, rootNode)
                } else {
                    traverseNode(rootNode, Pair(credentials[0].username, credentials[0].password), packageName)
                }
            } else {
                traverseNode(rootNode, Pair(credentials[0].username, credentials[0].password), packageName)
            }
        } else {
            // 5. Universal Default Identity Fallback
            Log.d("GuardianAccess", "No app credentials found. Attempting Universal Fallback.")
            universalFill(rootNode)
        }
    }

    private fun scanAndFillOtp(node: AccessibilityNodeInfo, otp: String): Boolean {
        var filled = false
        if (node.isEditable) {
            val text = node.text?.toString()?.lowercase()
            val hint = node.hintText?.toString()?.lowercase()
            val desc = node.contentDescription?.toString()?.lowercase()
            val viewId = node.viewIdResourceName?.lowercase()
            val combined = "$hint $text $desc $viewId"

            if (isOtp(text, hint, desc, viewId) || combined.contains("code") || combined.contains("pin")) {
                if (node.text.isNullOrEmpty()) {
                    Log.d("GuardianAccess", "OTP Priority: Found Field. Filling: $otp")
                    fillNode(node, otp)
                    filled = true
                }
            }
        }

        for (i in 0 until node.childCount) {
             val child = node.getChild(i)
             if (child != null) {
                 if (scanAndFillOtp(child, otp)) filled = true
                 child.recycle()
             }
        }
        return filled
    }

    private fun universalFill(node: AccessibilityNodeInfo) {
        if (node.className == "android.widget.EditText") {
            val hint = (node.hintText?.toString() ?: "").lowercase()
            val text = (node.text?.toString() ?: "").lowercase()
            val contentDesc = (node.contentDescription?.toString() ?: "").lowercase()
            val combined = "$hint $text $contentDesc"

            // Email Detection
            if (combined.contains("email") || combined.contains("e-mail") || node.inputType == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                val email = CredentialVault.defaultEmail
                if (email != null && node.text.isNullOrEmpty()) {
                     Log.d("GuardianAccess", "Universal: Found Email Field. Filling.")
                     fillNode(node, email)
                }
            }
            
            // Phone Detection
            if (combined.contains("phone") || combined.contains("mobile") || combined.contains("number") || node.inputType == android.text.InputType.TYPE_CLASS_PHONE) {
                val phone = CredentialVault.defaultPhone
                if (phone != null && node.text.isNullOrEmpty()) {
                     Log.d("GuardianAccess", "Universal: Found Phone Field. Filling.")
                     fillNode(node, phone)
                }
            }

            // OTP Detection (Added Fix)
            if (combined.contains("code") || combined.contains("otp") || combined.contains("verification") || combined.contains("pin")) {
                val otp = com.guardian.mesh.otp.OtpRepository.getValidOtp()
                if (otp != null && node.text.isNullOrEmpty()) {
                    Log.d("GuardianAccess", "Universal: Found OTP Field. Filling: $otp")
                    fillNode(node, otp)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                universalFill(child)
                child.recycle()
            }
        }
    }

    private fun hasEmptyUsernameField(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) {
            val text = node.text?.toString()?.lowercase()
            val hint = node.hintText?.toString()?.lowercase()
            val desc = node.contentDescription?.toString()?.lowercase()
            val viewId = node.viewIdResourceName?.lowercase()
            
            if (isUsername(text, hint, desc, viewId)) {
                return node.text.isNullOrEmpty()
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (hasEmptyUsernameField(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    private var isOverlayShowing = false

    private fun showAccountPicker(credentials: List<com.guardian.mesh.autofill.Credential>, rootNode: AccessibilityNodeInfo) {
        if (isOverlayShowing) return
        
        val packageName = rootNode.packageName?.toString() ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val inflater = android.view.LayoutInflater.from(this)
        val view = inflater.inflate(com.guardian.mesh.R.layout.layout_account_picker, null)

        // Use a wider layout, but with margins
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.85).toInt()

        val params = android.view.WindowManager.LayoutParams(
            width,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND, // Dim background
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.dimAmount = 0.6f
        params.gravity = android.view.Gravity.CENTER

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(com.guardian.mesh.R.id.accountList)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        // Better Adapter
        recyclerView.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                // Create a nice item view
                val layout = android.widget.LinearLayout(parent.context)
                layout.orientation = android.widget.LinearLayout.VERTICAL
                layout.setPadding(32, 24, 32, 24)
                layout.setBackgroundResource(android.R.drawable.list_selector_background)
                
                val userText = android.widget.TextView(parent.context)
                userText.textSize = 18f
                userText.setTextColor(android.graphics.Color.WHITE)
                userText.setTypeface(null, android.graphics.Typeface.BOLD)
                userText.id = 1001
                
                val appText = android.widget.TextView(parent.context)
                appText.textSize = 14f
                appText.setTextColor(android.graphics.Color.LTGRAY)
                appText.id = 1002
                
                layout.addView(userText)
                layout.addView(appText)
                
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(layout) {}
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val cred = credentials[position]
                val layout = holder.itemView as android.widget.LinearLayout
                val userText = layout.findViewById<android.widget.TextView>(1001)
                val appText = layout.findViewById<android.widget.TextView>(1002)
                
                userText.text = cred.username
                appText.text = cred.appName
                
                holder.itemView.setOnClickListener {
                    // 1. Remove overlay first to restore focus to the app
                    removeOverlay(windowManager, view)
                    
                    // 2. Wait briefly for window transition, then refill
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val freshRoot = rootInActiveWindow
                        if (freshRoot != null) {
                            Log.d("GuardianAccess", "Refilling with fresh root after selection")
                            traverseNode(freshRoot, Pair(cred.username, cred.password), packageName)
                            freshRoot.recycle()
                        } else {
                             Log.e("GuardianAccess", "Could not get fresh root after overlay")
                        }
                    }, 200)
                }
            }

            override fun getItemCount() = credentials.size
        }

        view.findViewById<android.view.View>(com.guardian.mesh.R.id.btnCancel).setOnClickListener {
            removeOverlay(windowManager, view)
        }

        try {
            windowManager.addView(view, params)
            isOverlayShowing = true
        } catch (e: Exception) {
            Log.e("GuardianAccess", "Error showing overlay", e)
        }
    }

    private fun removeOverlay(wm: android.view.WindowManager, view: android.view.View) {
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            // Ignore if already removed
        } finally {
            isOverlayShowing = false
            overlayDismissTime = System.currentTimeMillis() // Start Cooldown
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, credentials: Pair<String, String>, packageName: String) {
        if (node.childCount == 0) {
            checkNode(node, credentials, packageName)
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, credentials, packageName)
                child.recycle()
            }
        }
    }

    private fun checkNode(node: AccessibilityNodeInfo, credentials: Pair<String, String>, packageName: String) {
        if (!node.isEditable) return

        val text = node.text?.toString()?.lowercase()
        val hint = node.hintText?.toString()?.lowercase()
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        val viewId = node.viewIdResourceName?.lowercase()
        val now = System.currentTimeMillis()

        // Check for Username
        if (isUsername(text, hint, contentDesc, viewId)) {
            // Only fill if empty AND cooldown passed
            if (node.text.isNullOrEmpty()) {
                val lastFill = fillCooldowns["$packageName:user"] ?: 0L
                if (now - lastFill > COOLDOWN_MS) {
                    Log.d("GuardianAccess", "Found Username Field. Filling...")
                    fillNode(node, credentials.first)
                    fillCooldowns["$packageName:user"] = now
                }
            }
        }

        // Check for Password
        if (isPassword(text, hint, contentDesc, viewId, node)) {
             if (node.text.isNullOrEmpty()) {
                val lastFill = fillCooldowns["$packageName:pass"] ?: 0L
                if (now - lastFill > COOLDOWN_MS) {
                    if (credentials.second.isNotEmpty()) {
                        Log.d("GuardianAccess", "Found Password Field. Filling...")
                        fillNode(node, credentials.second)
                        fillCooldowns["$packageName:pass"] = now
                    }
                }
            }
        }

        // Check for OTP/Code
        if (isOtp(text, hint, contentDesc, viewId)) {
            if (node.text.isNullOrEmpty()) {
                val otp = com.guardian.mesh.otp.OtpRepository.getValidOtp()
                if (otp != null) {
                    val lastFill = fillCooldowns["$packageName:otp"] ?: 0L
                    if (now - lastFill > COOLDOWN_MS) {
                        Log.d("GuardianAccess", "Found OTP Field. Filling: $otp")
                        fillNode(node, otp)
                        fillCooldowns["$packageName:otp"] = now
                    }
                }
            }
        }
    }

    private fun isOtp(text: String?, hint: String?, desc: String?, id: String?): Boolean {
        if (matches(text, "otp", "code", "pin", "verification")) return true
        if (matches(hint, "otp", "code", "pin", "verification")) return true
        if (matches(desc, "otp", "code", "pin", "verification")) return true
        if (matches(id, "otp", "code", "pin", "verification")) return true
        return false
    }

    private fun isUsername(text: String?, hint: String?, desc: String?, id: String?): Boolean {
        if (matches(text, "user", "email", "login")) return true
        if (matches(hint, "user", "email", "login")) return true
        if (matches(desc, "user", "email", "login")) return true
        if (matches(id, "user", "email", "login")) return true
        return false
    }

    private fun isPassword(text: String?, hint: String?, desc: String?, id: String?, node: AccessibilityNodeInfo): Boolean {
        if (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0) return true
        if (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0) return true
        
        if (matches(text, "pass", "secret", "pwd")) return true
        if (matches(hint, "pass", "secret", "pwd")) return true
        if (matches(desc, "pass", "secret", "pwd")) return true
        if (matches(id, "pass", "secret", "pwd")) return true
        return false
    }

    private fun matches(source: String?, vararg targets: String): Boolean {
        if (source == null) return false
        for (target in targets) {
            if (source.contains(target)) return true
        }
        return false
    }

    private fun fillNode(node: AccessibilityNodeInfo, value: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d("GuardianAccess", "Filled: $value")
        
        // Trigger Auto-Login search after a short delay to allow UI to update
        node.postDelayed({
            val root = rootInActiveWindow
            if (root != null) {
                findAndClickLogin(root)
            }
        }, 500)
    }

    private fun findAndClickLogin(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            val text = node.text?.toString()?.lowercase()
            val desc = node.contentDescription?.toString()?.lowercase()
            
            if (isLoginButton(text) || isLoginButton(desc)) {
                Log.d("GuardianAccess", "Found Login Button. Clicking...")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findAndClickLogin(child)
                child.recycle()
            }
        }
    }

    private fun isLoginButton(text: String?): Boolean {
        if (text == null) return false
        return text == "login" || text == "sign in" || text == "log in" || text == "submit" || text == "continue"
    }

    // Extension function for AccessibilityNodeInfo since it doesn't have postDelayed
    private fun AccessibilityNodeInfo.postDelayed(action: () -> Unit, delayMillis: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMillis)
    }

    override fun onInterrupt() {
        Log.d("GuardianAccess", "Service Interrupted")
    }
}
