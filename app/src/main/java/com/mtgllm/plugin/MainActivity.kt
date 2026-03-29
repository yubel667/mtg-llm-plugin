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

        binding.pasteButton.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasteData = item?.text?.toString()
            if (!pasteData.isNullOrEmpty()) {
                binding.moxfieldUrlEditText.setText(pasteData)
            }
        }

        binding.clearButton.setOnClickListener {
            binding.moxfieldUrlEditText.text?.clear()
        }

        binding.loadMoxfieldButton.setOnClickListener {
            val url = binding.moxfieldUrlEditText.text.toString().trim()
            if (url.isNotEmpty()) {
                viewModel.fetchDeckFromUrl(url)
            } else {
                showError("Please enter a Moxfield or MTGTop8 URL")
            }
        }

        binding.helpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        setupObservers()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun resetUI() {
        binding.configScrollView.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.statusTextView.text = "Waiting for decklist..."
        binding.messageTextView.text = ""
        binding.resetButton.visibility = View.GONE
        binding.moxfieldUrlEditText.text?.clear()
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action) {
            binding.statusTextView.text = "Receiving share..."
            
            // Check if it's a supported URL being shared
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                if (sharedText.contains("moxfield.com/decks/") || sharedText.contains("mtgtop8.com/")) {
                    binding.moxfieldUrlEditText.setText(sharedText)
                    viewModel.fetchDeckFromUrl(sharedText)
                    return
                }
                binding.moxfieldUrlEditText.text?.clear()
                prepareForConversion(sharedText, null)
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
                    val fileName = getFileName(it)
                    val content = contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                    if (content != null) {
                        prepareForConversion(content, fileName?.substringBeforeLast("."))
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

    private fun getFileName(uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun showError(message: String) {
        binding.configScrollView.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        binding.statusTextView.text = "Error"
        binding.messageTextView.text = message
        binding.resetButton.visibility = View.VISIBLE
    }

    private fun prepareForConversion(text: String, defaultName: String?) {
        lastReceivedText = text
        binding.statusTextView.text = "Analyzing list..."
        
        val deckInfo = com.mtgllm.plugin.utils.DeckParser.parse(text, defaultName)
        showConfig(deckInfo)
    }

    private fun showConfig(deckInfo: com.mtgllm.plugin.utils.DeckInfo) {
        binding.configScrollView.visibility = View.VISIBLE
        binding.progressLayout.visibility = View.GONE
        binding.deckNameEditText.setText(deckInfo.name)
        
        binding.processButton.setOnClickListener {
            val finalName = binding.deckNameEditText.text.toString().trim()
            val useTimestamp = binding.timestampCheckBox.isChecked
            val includeSideboard = binding.sideboardCheckBox.isChecked
            val includeMayboard = binding.mayboardCheckBox.isChecked
            
            binding.configScrollView.visibility = View.GONE
            binding.progressLayout.visibility = View.VISIBLE
            binding.resetButton.visibility = View.GONE
            
            val textToProcess = lastReceivedText ?: deckInfo.rawText
            
            viewModel.processDeck(
                textToProcess, 
                if (finalName.isNotEmpty()) finalName else null, 
                useTimestamp,
                includeSideboard,
                includeMayboard
            )
        }
    }

    private fun setupObservers() {
        viewModel.moxfieldDeck.observe(this) { deckInfo ->
            deckInfo?.let {
                lastReceivedText = it.rawText
                showConfig(it)
            }
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is DeckProcessState.Idle -> {
                    // Ready
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
                    binding.messageTextView.text = "Generated: ${state.fileName}\nTotal cards: ${state.cardCount}"
                    binding.resetButton.visibility = View.VISIBLE
                }
                is DeckProcessState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Error"
                    binding.messageTextView.text = state.message
                    binding.resetButton.visibility = View.VISIBLE
                    binding.configScrollView.visibility = View.GONE
                }
            }
        }
    }
}
