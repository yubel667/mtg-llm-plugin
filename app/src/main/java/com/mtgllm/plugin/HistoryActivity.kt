package com.mtgllm.plugin

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtgllm.plugin.data.DeckRecordEntity
import com.mtgllm.plugin.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: DeckViewModel by viewModels()
    private var pendingRecordToSave: DeckRecordEntity? = null

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { uriValue ->
            pendingRecordToSave?.let { record ->
                viewModel.saveRecordToUri(this, record, uriValue)
            }
        }
        pendingRecordToSave = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = HistoryAdapter(
            onPreview = { record -> showPreview(record.name, record.resultText) },
            onShare = { viewModel.shareRecord(it) },
            onSave = { record ->
                pendingRecordToSave = record
                saveFileLauncher.launch(record.fileName)
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

    private fun showPreview(name: String, content: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setMessage(content)
            .setPositiveButton("Close", null)
            .show()
    }
}
