package com.mtgllm.plugin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtgllm.plugin.databinding.ActivityMainBinding
import io.noties.markwon.Markwon

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DeckViewModel by viewModels()
    private var lastReceivedText: String? = null
    private lateinit var markwon: Markwon

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { viewModel.saveFileToUri(this, it) }
    }

    private val historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val input = result.data?.getStringExtra("reprocess_input")
            val name = result.data?.getStringExtra("reprocess_name")
            if (input != null) {
                prepareForConversion(input, name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        markwon = Markwon.create(this)
        setupClickListeners()
        setupObservers()
        handleIntent(intent)
    }

    private fun setupClickListeners() {
        binding.resetButton.setOnClickListener {
            resetUI()
        }

        binding.shareFileButton.setOnClickListener {
            viewModel.shareLatestFile()
        }

        binding.saveToLocalButton.setOnClickListener {
            val fileName = viewModel.getLatestFileName() ?: "deck.txt"
            saveFileLauncher.launch(fileName)
        }

        binding.pasteButton.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasteData = item?.text?.toString()
            if (!pasteData.isNullOrEmpty()) {
                binding.moxfieldUrlEditText.setText(pasteData)
            }
        }

        binding.urlInputLayout.setEndIconOnClickListener {
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

        binding.historyButton.setOnClickListener {
            historyLauncher.launch(Intent(this, HistoryActivity::class.java))
        }

        binding.optionsButton.setOnClickListener {
            startActivity(Intent(this, OptionsActivity::class.java))
        }

        binding.manaBoxGuideButton.setOnClickListener {
            showManaBoxGuide()
        }
    }

    private fun showManaBoxGuide() {
        val guide = """
            ### Importing from Mana Box
            
            1. Open your deck in **Mana Box**.
            2. Tap the **Menu** (three dots) in the top right.
            3. Tap **Share**.
            4. Select **File** (recommended for best results).
            5. Find and tap **MTG Deck to Oracle** in the system share menu.
            
            ---
            
            *Tip: You can also choose 'Text' instead of 'File' and share it to this app.*
        """.trimIndent()

        val padding = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(this).apply {
            setPadding(padding, padding, padding, 0)
        }
        markwon.setMarkdown(textView, guide)

        MaterialAlertDialogBuilder(this)
            .setTitle("Mana Box Import Guide")
            .setView(textView)
            .setPositiveButton("Got it", null)
            .show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun resetUI() {
        binding.configCard.visibility = View.GONE
        binding.urlCard.visibility = View.VISIBLE
        binding.statusContainer.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.statusTextView.text = ""
        binding.messageTextView.text = ""
        binding.resetButton.visibility = View.GONE
        binding.successActionsLayout.visibility = View.GONE
        binding.previewCard.visibility = View.GONE
        binding.moxfieldUrlEditText.text?.clear()
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action) {
            binding.statusContainer.visibility = View.VISIBLE
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
        binding.configCard.visibility = View.GONE
        binding.statusContainer.visibility = View.VISIBLE
        binding.statusTextView.text = "Error"
        binding.messageTextView.text = message
        binding.resetButton.visibility = View.VISIBLE
        binding.successActionsLayout.visibility = View.GONE
        binding.previewCard.visibility = View.GONE
    }

    private fun prepareForConversion(text: String, defaultName: String?) {
        lastReceivedText = text
        binding.statusContainer.visibility = View.VISIBLE
        binding.statusTextView.text = "Analyzing list..."
        
        val deckInfo = com.mtgllm.plugin.utils.DeckParser.parse(text, defaultName)
        showConfig(deckInfo)
    }

    private fun showConfig(deckInfo: com.mtgllm.plugin.utils.DeckInfo) {
        binding.configCard.visibility = View.VISIBLE
        binding.urlCard.visibility = View.GONE
        binding.statusContainer.visibility = View.VISIBLE
        binding.statusTextView.text = "Analysis Complete"
        binding.deckNameEditText.setText(deckInfo.name)

        // Deck Summary and Preview
        val totalCards = deckInfo.cards.sumOf { it.quantity }
        val uniqueCards = deckInfo.cards.size
        binding.deckSummaryTextView.text = "Summary: $totalCards total cards ($uniqueCards unique)"
        binding.deckPreviewTextView.text = deckInfo.rawText

        // Auto-check Game Changers for Commander (e.g. 90+ cards)
        val isCommanderSize = totalCards >= 90
        if (viewModel.autoGameChangerEnabled && isCommanderSize) {
            binding.gameChangersCheckBox.isChecked = true
        } else {
            binding.gameChangersCheckBox.isChecked = false
        }
        
        binding.processButton.setOnClickListener {
            val finalName = binding.deckNameEditText.text.toString().trim()
            val useTimestamp = binding.timestampCheckBox.isChecked
            val includeSideboard = binding.sideboardCheckBox.isChecked
            val includeMaybeboard = binding.mayboardCheckBox.isChecked
            val includeGameChangers = binding.gameChangersCheckBox.isChecked
            
            if (includeGameChangers && (viewModel.gameChangers.value?.isEmpty() != false)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Commander Game Changers")
                    .setMessage("The 'Game Changers' list is a set of high-impact cards identified by Wizards of the Coast. Appending this list helps LLMs analyze your deck's power level.\n\nYou haven't fetched this list yet. Would you like to fetch it now from Scryfall?")
                    .setPositiveButton("Fetch Now") { _, _ ->
                        viewModel.fetchGameChangers()
                    }
                    .setNeutralButton("Options") { _, _ ->
                        startActivity(Intent(this, OptionsActivity::class.java))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }

            binding.configCard.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            binding.resetButton.visibility = View.GONE
            binding.successActionsLayout.visibility = View.GONE
            binding.previewCard.visibility = View.GONE
            
            val textToProcess = lastReceivedText ?: deckInfo.rawText
            
            viewModel.processDeck(
                textToProcess, 
                if (finalName.isNotEmpty()) finalName else null, 
                useTimestamp,
                includeSideboard,
                includeMaybeboard,
                includeGameChangers
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
                    // Check if we just finished fetching game changers
                    if (binding.gameChangersCheckBox.isChecked && viewModel.gameChangers.value?.isNotEmpty() == true && binding.configCard.visibility == View.VISIBLE) {
                        Toast.makeText(this, "Game Changers list fetched!", Toast.LENGTH_SHORT).show()
                    }
                }
                is DeckProcessState.Processing -> {
                    binding.statusContainer.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = state.progress
                    binding.statusTextView.text = "Processing..."
                    binding.messageTextView.text = state.message
                    binding.resetButton.visibility = View.GONE
                    binding.successActionsLayout.visibility = View.GONE
                    binding.previewCard.visibility = View.GONE
                }
                is DeckProcessState.Success -> {
                    binding.statusContainer.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = if (state.failedCards.isEmpty()) "Success!" else "Conversion Partial"
                    
                    val msg = buildString {
                        append("Generated: ${state.fileName}\n")
                        append("Total cards: ${state.cardCount}")
                        if (state.failedCards.isNotEmpty()) {
                            append("\n\nFailed to convert (${state.failedCards.size}):\n")
                            append(state.failedCards.joinToString(", "))
                        }
                    }
                    binding.messageTextView.text = msg
                    binding.resetButton.visibility = View.VISIBLE
                    binding.successActionsLayout.visibility = View.VISIBLE
                    
                    // Show preview
                    binding.previewCard.visibility = View.VISIBLE
                    binding.previewTextView.text = viewModel.getLatestResultText()
                }
                is DeckProcessState.Error -> {
                    binding.statusContainer.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Error"
                    binding.messageTextView.text = state.message
                    binding.resetButton.visibility = View.VISIBLE
                    binding.configCard.visibility = View.GONE
                    binding.successActionsLayout.visibility = View.GONE
                    binding.previewCard.visibility = View.GONE
                }
            }
        }
    }
}
