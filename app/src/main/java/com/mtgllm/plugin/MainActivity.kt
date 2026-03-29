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
    private var lastReceivedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resetButton.setOnClickListener {
            resetUI()
        }

        setupObservers()
        handleIntent(intent)
    }

    private fun resetUI() {
        binding.configLayout.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.statusTextView.text = "Waiting for decklist..."
        binding.messageTextView.text = ""
        binding.resetButton.visibility = View.GONE
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action) {
            binding.statusTextView.text = "Receiving share..."
            
            // Case 1: Shared as raw text
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                prepareForConversion(sharedText)
                return
            }

            // Case 2: Shared as a file (URI)
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            }

            uri?.let { 
                try {
                    binding.statusTextView.text = "Reading file..."
                    val content = contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                    if (content != null) {
                        prepareForConversion(content)
                    } else {
                        showError("File is empty or could not be read.")
                    }
                } catch (e: Exception) {
                    showError("Error reading file: ${e.localizedMessage}")
                }
                return
            }
            
            showError("No text or file found in the shared content.")
        }
    }

    private fun showError(message: String) {
        binding.configLayout.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        binding.statusTextView.text = "Error"
        binding.messageTextView.text = message
        binding.resetButton.visibility = View.VISIBLE
    }

    private fun prepareForConversion(text: String) {
        lastReceivedText = text
        binding.statusTextView.text = "Analyzing list..."
        
        val deckInfo = com.mtgllm.plugin.utils.DeckParser.parse(text)
        
        binding.configLayout.visibility = View.VISIBLE
        binding.progressLayout.visibility = View.GONE
        binding.deckNameEditText.setText(deckInfo.name)
        
        binding.processButton.setOnClickListener {
            val finalName = binding.deckNameEditText.text.toString().trim()
            val useTimestamp = binding.timestampCheckBox.isChecked
            
            binding.configLayout.visibility = View.GONE
            binding.progressLayout.visibility = View.VISIBLE
            binding.resetButton.visibility = View.GONE
            
            viewModel.processDeck(text, if (finalName.isNotEmpty()) finalName else null, useTimestamp)
        }
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is DeckProcessState.Idle -> {
                    // Handled by resetUI/prepare
                }
                is DeckProcessState.Processing -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = state.progress
                    binding.statusTextView.text = "Processing..."
                    binding.messageTextView.text = state.message
                    binding.resetButton.visibility = View.GONE
                }
                is DeckProcessState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Success!"
                    binding.messageTextView.text = "Generated: ${state.fileName}"
                    binding.resetButton.visibility = View.VISIBLE
                }
                is DeckProcessState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Error"
                    binding.messageTextView.text = state.message
                    binding.resetButton.visibility = View.VISIBLE
                }
            }
        }
    }
}
