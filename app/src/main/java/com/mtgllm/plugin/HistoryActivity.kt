package com.mtgllm.plugin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtgllm.plugin.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: DeckViewModel by viewModels { DeckViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = HistoryAdapter(
            onReprocess = { record ->
                val intent = Intent().apply {
                    putExtra("reprocess_input", record.rawInput)
                    putExtra("reprocess_name", record.name)
                }
                setResult(RESULT_OK, intent)
                finish()
            },
            onDelete = { record ->
                if (viewModel.askBeforeDeleteEnabled) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Deck History?")
                        .setMessage("Are you sure you want to delete '${record.name}'?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteRecord(record)
                        }
                        .show()
                } else {
                    viewModel.deleteRecord(record)
                }
            }
        )

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter

        binding.searchEditText.doAfterTextChanged { text ->
            viewModel.loadHistory(text?.toString() ?: "")
        }

        viewModel.history.observe(this) { records ->
            adapter.submitList(records)
            binding.emptyHistoryTextView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
