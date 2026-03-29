package com.mtgllm.plugin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mtgllm.plugin.data.DeckRecordEntity
import com.mtgllm.plugin.databinding.ItemDeckRecordBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onPreview: (DeckRecordEntity) -> Unit,
    private val onShare: (DeckRecordEntity) -> Unit,
    private val onSave: (DeckRecordEntity) -> Unit,
    private val onDelete: (DeckRecordEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items = listOf<DeckRecordEntity>()

    fun submitList(newItems: List<DeckRecordEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemDeckRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeckRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        
        holder.binding.deckNameTextView.text = item.name
        holder.binding.deckDetailsTextView.text = "$date • ${item.cardCount} cards"
        
        holder.binding.previewRecordButton.setOnClickListener { onPreview(item) }
        holder.binding.shareRecordButton.setOnClickListener { onShare(item) }
        holder.binding.saveRecordButton.setOnClickListener { onSave(item) }
        holder.binding.deleteRecordButton.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size
}
