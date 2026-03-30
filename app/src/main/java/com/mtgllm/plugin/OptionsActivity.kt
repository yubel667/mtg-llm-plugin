package com.mtgllm.plugin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.mtgllm.plugin.databinding.ActivityOptionsBinding

class OptionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOptionsBinding
    private val viewModel: DeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup Initial Values
        binding.autoShareCheckBox.isChecked = viewModel.autoShareEnabled
        binding.askBeforeDeleteCheckBox.isChecked = viewModel.askBeforeDeleteEnabled
        binding.autoGameChangerCheckBox.isChecked = viewModel.autoGameChangerEnabled
        binding.historyLimitEditText.setText(viewModel.historyLimit.toString())

        viewModel.cachedCardCount.observe(this) { count ->
            binding.cachedCardsTextView.text = "Cached Cards: $count"
        }

        viewModel.lastGameChangerFetch.observe(this) { timestamp ->
            if (timestamp > 0) {
                val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                binding.lastGameChangerFetchTextView.text = "Last fetched: $date"
            } else {
                binding.lastGameChangerFetchTextView.text = "Last fetched: Never"
            }
        }

        // Observe ViewModel state for re-fetch feedback
        viewModel.state.observe(this) { state ->
            when (state) {
                is DeckProcessState.Processing -> {
                    if (state.message.contains("Game Changers")) {
                        binding.fetchGameChangersButton.isEnabled = false
                        binding.fetchGameChangersButton.text = "Fetching..."
                    }
                }
                is DeckProcessState.Idle, is DeckProcessState.Success -> {
                    val wasFetching = !binding.fetchGameChangersButton.isEnabled
                    binding.fetchGameChangersButton.isEnabled = true
                    binding.fetchGameChangersButton.text = "Re-fetch"
                    
                    if (wasFetching && state is DeckProcessState.Idle) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Fetch Successful")
                            .setMessage("The Commander Game Changers list has been updated from Scryfall.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
                is DeckProcessState.Error -> {
                    binding.fetchGameChangersButton.isEnabled = true
                    binding.fetchGameChangersButton.text = "Re-fetch"
                    if (state.message.contains("Game Changers")) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Fetch Error")
                            .setMessage(state.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        // Listeners
        binding.fetchGameChangersButton.setOnClickListener {
            viewModel.fetchGameChangers()
        }

        binding.previewGameChangersButton.setOnClickListener {
            val list = viewModel.gameChangers.value ?: emptyList()
            if (list.isEmpty()) {
                Toast.makeText(this, "List is empty. Please fetch first.", Toast.LENGTH_SHORT).show()
            } else {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Commander Game Changers (${list.size})")
                    .setMessage(list.joinToString("\n"))
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        binding.saveSettingsButton.setOnClickListener {
            viewModel.autoShareEnabled = binding.autoShareCheckBox.isChecked
            viewModel.askBeforeDeleteEnabled = binding.askBeforeDeleteCheckBox.isChecked
            viewModel.autoGameChangerEnabled = binding.autoGameChangerCheckBox.isChecked
            val limit = binding.historyLimitEditText.text.toString().toIntOrNull() ?: 100
            viewModel.historyLimit = limit
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }

        binding.clearCacheButton.setOnClickListener {
            viewModel.clearCache()
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
        }

        binding.clearHistoryButton.setOnClickListener {
            viewModel.clearHistory()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
    }
}
