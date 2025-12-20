package com.guardian.mesh.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.guardian.mesh.R
import com.guardian.mesh.autofill.Credential

class VaultAdapter(
    private val credentials: List<Credential>,
    private val onDeleteClick: (Credential) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.textDomain)
        val username: TextView = view.findViewById(R.id.textUsername)
        // val btnCopy: Button = view.findViewById(R.id.btnCopy) // Removed
        // val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete) // Removed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_credential, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val credential = credentials[position]
        holder.appName.text = credential.appName
        holder.username.text = credential.username
        
        holder.itemView.setOnClickListener {
             val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Password", credential.password)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(holder.itemView.context, "Password Copied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = credentials.size
}


