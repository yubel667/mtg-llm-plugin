package com.mtgllm.plugin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.mtgllm.plugin.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                viewModel.processDeck(sharedText)
            }
        }
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is DeckProcessState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Waiting for decklist..."
                }
                is DeckProcessState.Processing -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = state.progress
                    binding.statusTextView.text = "Processing..."
                    binding.messageTextView.text = state.message
                }
                is DeckProcessState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Success!"
                    binding.messageTextView.text = "Generated: ${state.fileName}"
                }
                is DeckProcessState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Error"
                    binding.messageTextView.text = state.message
                }
            }
        }
    }
}
