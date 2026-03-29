package com.mtgllm.plugin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mtgllm.plugin.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener {
            finish()
        }

        try {
            val helpText = assets.open("HELP.md").bufferedReader().use { it.readText() }
            binding.helpContentTextView.text = helpText
        } catch (e: Exception) {
            binding.helpContentTextView.text = "Error loading help file: ${e.localizedMessage}"
        }
    }
}
