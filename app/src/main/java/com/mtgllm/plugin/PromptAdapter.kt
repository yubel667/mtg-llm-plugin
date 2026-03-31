package com.mtgllm.plugin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mtgllm.plugin.data.PromptEntity
import com.mtgllm.plugin.databinding.ItemPromptBinding
import java.util.Collections

class PromptAdapter(
    private val onEdit: (PromptEntity) -> Unit,
    private val onDelete: (PromptEntity) -> Unit
) : RecyclerView.Adapter<PromptAdapter.ViewHolder>() {

    private var prompts = mutableListOf<PromptEntity>()

    fun setPrompts(newList: List<PromptEntity>) {
        prompts = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun getPrompts(): List<PromptEntity> = prompts

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(prompts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(prompts, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    class ViewHolder(private val binding: ItemPromptBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(prompt: PromptEntity, onEdit: (PromptEntity) -> Unit, onDelete: (PromptEntity) -> Unit) {
            binding.promptNameTextView.text = prompt.name
            binding.promptContentTextView.text = prompt.content
            binding.defaultLabel.visibility = if (prompt.isDefault) View.VISIBLE else View.GONE
            
            binding.editButton.setOnClickListener { onEdit(prompt) }
            binding.deleteButton.setOnClickListener { onDelete(prompt) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPromptBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = prompts.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(prompts[position], onEdit, onDelete)
    }
}
