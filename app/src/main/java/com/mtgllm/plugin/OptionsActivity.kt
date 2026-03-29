package com.mtgllm.plugin

import android.os.Bundle
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
        binding.historyLimitEditText.setText(viewModel.historyLimit.toString())

        viewModel.cachedCardCount.observe(this) { count ->
            binding.cachedCardsTextView.text = "Cached Cards: $count"
        }

        // Listeners
        binding.saveSettingsButton.setOnClickListener {
            viewModel.autoShareEnabled = binding.autoShareCheckBox.isChecked
            viewModel.askBeforeDeleteEnabled = binding.askBeforeDeleteCheckBox.isChecked
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
