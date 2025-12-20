package com.guardian.mesh.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guardian.mesh.R
import kotlinx.coroutines.*
import java.net.URL


class CredentialAdapter(private val credentials: List<Credential>) : RecyclerView.Adapter<CredentialAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgLogo: ImageView = view.findViewById(R.id.imgLogo)
        val textDomain: TextView = view.findViewById(R.id.textDomain)
        val textUsername: TextView = view.findViewById(R.id.textUsername)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_credential, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = credentials[position]
        holder.textDomain.text = item.serviceName
        holder.textUsername.text = item.username

        // Open Detail View on Click
        holder.itemView.setOnClickListener {
            val intent = android.content.Intent(holder.itemView.context, CredentialDetailActivity::class.java)
            intent.putExtra("credential", item)
            holder.itemView.context.startActivity(intent)
        }

        // Load Favicon in Background
        val faviconUrl = "https://www.google.com/s2/favicons?domain=${item.domain}&sz=64"
        
        holder.imgLogo.setImageResource(android.R.drawable.ic_menu_compass) // Placeholder
        
        // Simple Coroutine Image Loader (Avoids Glide dependency for now)
        // In a real app, use Coil/Glide for caching/recycling handling
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(faviconUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                withContext(Dispatchers.Main) {
                    // Ensure the holder hasn't been recycled for a different item (basic check)
                    if (holder.textDomain.text == item.serviceName) {
                        holder.imgLogo.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount() = credentials.size
}
