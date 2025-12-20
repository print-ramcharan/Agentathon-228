package com.guardian.mesh.autofill

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId

class StructureParser(private val structure: AssistStructure) {

    private var lastSeenTextField: AutofillId? = null
    private var foundUsernameId: AutofillId? = null
    private var foundPasswordId: AutofillId? = null

    fun parse(): Pair<AutofillId?, AutofillId?> {
        traverseStructure(structure)
        return Pair(foundUsernameId, foundPasswordId)
    }

    private fun traverseStructure(structure: AssistStructure) {
        val windowNodes = structure.windowNodeCount
        for (i in 0 until windowNodes) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            traverseNode(rootNode)
            if (foundUsernameId != null && foundPasswordId != null) return
        }
    }

    private fun traverseNode(node: AssistStructure.ViewNode) {
        if (foundUsernameId != null && foundPasswordId != null) return

        // Check for Password Field
        if (isPasswordField(node)) {
            foundPasswordId = node.autofillId
            // Heuristic: The username is likely the last text field we saw
            if (foundUsernameId == null) {
                foundUsernameId = lastSeenTextField
            }
            return
        }

        // Check for Username Field (Explicit)
        if (isUsernameField(node)) {
            foundUsernameId = node.autofillId
        }

        // Track potential text fields (for fallback)
        if (isPotentialTextField(node)) {
            lastSeenTextField = node.autofillId
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i))
        }
    }

    private fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
        val hints = node.autofillHints
        if (hints != null && hints.contains(android.view.View.AUTOFILL_HINT_PASSWORD)) return true
        
        val idEntry = node.idEntry
        if (idEntry != null && (idEntry.contains("pass", true) || idEntry.contains("pwd", true))) return true
        
        val hint = node.hint?.toString()
        if (hint != null && (hint.contains("pass", true) || hint.contains("secret", true))) return true
        
        // Chrome/WebView specific: InputType check (if we could access it, but we can't easily)
        // Instead check for "password" in content description
        val contentDesc = node.contentDescription?.toString()
        if (contentDesc != null && contentDesc.contains("password", true)) return true

        return false
    }

    private fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
        val hints = node.autofillHints
        if (hints != null && (hints.contains(android.view.View.AUTOFILL_HINT_USERNAME) || 
            hints.contains(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS))) return true
            
        val idEntry = node.idEntry
        if (idEntry != null && (idEntry.contains("user", true) || idEntry.contains("email", true) || idEntry.contains("login", true))) return true
        
        val hint = node.hint?.toString()
        if (hint != null && (hint.contains("user", true) || hint.contains("email", true) || hint.contains("login", true))) return true

        return false
    }

    private fun isPotentialTextField(node: AssistStructure.ViewNode): Boolean {
        val className = node.className
        if (className != null && (className.contains("EditText") || className.contains("TextInput"))) return true
        
        // WebViews often use generic Views for inputs, but they usually have hints or text
        if (node.hint != null || node.text != null) return true
        
        return false
    }

    // Deprecated methods kept for compatibility if needed, but we use parse() now
    fun findUsernameField(): AutofillId? = parse().first
    fun findPasswordField(): AutofillId? = parse().second
}
