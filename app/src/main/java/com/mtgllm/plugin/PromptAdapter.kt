package com.mtgllm.plugin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtgllm.plugin.data.PromptEntity
import com.mtgllm.plugin.databinding.ItemPromptBinding

class PromptAdapter(
    private val onEdit: (PromptEntity) -> Unit,
    private val onDelete: (PromptEntity) -> Unit
) : ListAdapter<PromptEntity, PromptAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemPromptBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(prompt: PromptEntity, onEdit: (PromptEntity) -> Unit, onDelete: (PromptEntity) -> Unit) {
            binding.promptNameTextView.text = prompt.name
            binding.promptContentTextView.text = prompt.content
            binding.defaultLabel.visibility = if (prompt.isDefault) View.VISIBLE else View.GONE
            
            binding.editButton.setOnClickListener { onEdit(prompt) }
            binding.deleteButton.setOnClickListener { onDelete(prompt) }
            
            // Disable delete for default prompts if you want, but user said "add, update or delete a prompt"
            // and "reset to default" is available, so maybe deleting default is okay?
            // Usually we keep them. Let's allow deletion for now as requested.
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPromptBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onEdit, onDelete)
    }

    object DiffCallback : DiffUtil.ItemCallback<PromptEntity>() {
        override fun areItemsTheSame(oldItem: PromptEntity, newItem: PromptEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PromptEntity, newItem: PromptEntity): Boolean =
            oldItem == newItem
    }
}
