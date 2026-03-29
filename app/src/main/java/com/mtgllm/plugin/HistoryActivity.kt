package com.mtgllm.plugin

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtgllm.plugin.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: DeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = HistoryAdapter(
            onShare = { viewModel.shareRecord(it) },
            onPreview = { showPreview(it.name, it.resultText) }
        )

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter

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
