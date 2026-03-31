package com.mtgllm.plugin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mtgllm.plugin.data.DeckRecordEntity
import com.mtgllm.plugin.databinding.ItemDeckRecordBinding
import java.text.SimpleDateFormat
import java.util.*

data class GroupedDeckRecord(
    val name: String,
    val latest: DeckRecordEntity,
    val allVersions: List<DeckRecordEntity>
)

class HistoryAdapter(
    private val onReprocess: (DeckRecordEntity) -> Unit,
    private val onDelete: (DeckRecordEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items = listOf<GroupedDeckRecord>()

    fun submitList(newItems: List<DeckRecordEntity>) {
        items = newItems.groupBy { it.name }.map { (name, versions) ->
            GroupedDeckRecord(name, versions.first(), versions)
        }
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemDeckRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeckRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val grouped = items[position]
        val item = grouped.latest
        val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        
        holder.binding.deckNameTextView.text = item.name
        
        val versionText = if (grouped.allVersions.size > 1) {
            " • ${grouped.allVersions.size} versions"
        } else {
            ""
        }
        holder.binding.deckDetailsTextView.text = "$date • ${item.cardCount} cards$versionText"
        
        holder.binding.reprocessRecordButton.setOnClickListener { onReprocess(item) }
        holder.binding.deleteRecordButton.setOnClickListener { onDelete(item) }

        if (grouped.allVersions.size > 1) {
            holder.binding.versionButton.visibility = android.view.View.VISIBLE
            holder.binding.versionButton.setOnClickListener { view ->
                val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                grouped.allVersions.forEachIndexed { index, version ->
                    val vDate = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(version.timestamp))
                    val title = if (index == 0) "v${grouped.allVersions.size} (Latest) - $vDate" 
                                else "v${grouped.allVersions.size - index} - $vDate"
                    popup.menu.add(0, index, index, title)
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    val selectedVersion = grouped.allVersions[menuItem.itemId]
                    updateViewHolderWithVersion(holder, selectedVersion, grouped.allVersions.size - menuItem.itemId)
                    true
                }
                popup.show()
            }
        } else {
            holder.binding.versionButton.visibility = android.view.View.GONE
        }
    }

    private fun updateViewHolderWithVersion(holder: ViewHolder, version: DeckRecordEntity, versionNumber: Int) {
        val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(version.timestamp))
        holder.binding.deckDetailsTextView.text = "$date • ${version.cardCount} cards (v$versionNumber)"
        holder.binding.reprocessRecordButton.setOnClickListener { onReprocess(version) }
        holder.binding.deleteRecordButton.setOnClickListener { onDelete(version) }
    }

    override fun getItemCount() = items.size
}
