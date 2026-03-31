package com.mtgllm.plugin

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtgllm.plugin.data.PromptEntity
import com.mtgllm.plugin.databinding.ActivityPromptManagementBinding

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class PromptManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromptManagementBinding
    private lateinit var viewModel: DeckViewModel
    private lateinit var adapter: PromptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, DeckViewModel.Factory(application))[DeckViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.resetPromptsButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Prompts?")
                .setMessage("This will delete all your custom prompts and restore the default ones. Continue?")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.resetPromptsToDefault()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        adapter = PromptAdapter(
            onEdit = { showPromptDialog(it) },
            onDelete = { showDeleteConfirmation(it) }
        )

        binding.promptsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.promptsRecyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Save new order to database
                val updatedPrompts = adapter.getPrompts().mapIndexed { index, prompt ->
                    prompt.copy(position = index)
                }
                viewModel.updatePrompts(updatedPrompts)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.promptsRecyclerView)

        binding.addPromptFab.setOnClickListener {
            showPromptDialog(null)
        }
    }

    private fun observeViewModel() {
        viewModel.prompts.observe(this) {
            adapter.setPrompts(it)
        }
    }

    private fun showPromptDialog(prompt: PromptEntity?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_prompt, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.promptNameEditText)
        val contentEditText = dialogView.findViewById<EditText>(R.id.promptContentEditText)

        prompt?.let {
            nameEditText.setText(it.name)
            contentEditText.setText(it.content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (prompt == null) "Add New Prompt" else "Edit Prompt")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val content = contentEditText.text.toString().trim()
                if (name.isNotEmpty() && content.isNotEmpty()) {
                    if (prompt == null) {
                        val maxPos = adapter.getPrompts().maxOfOrNull { it.position } ?: -1
                        viewModel.insertPrompt(PromptEntity(name = name, content = content, position = maxPos + 1))
                    } else {
                        viewModel.updatePrompt(prompt.copy(name = name, content = content))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(prompt: PromptEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Prompt?")
            .setMessage("Are you sure you want to delete '${prompt.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePrompt(prompt)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
