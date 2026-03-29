package com.mtgllm.plugin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mtgllm.plugin.databinding.ActivityHelpBinding
import io.noties.markwon.Markwon

class HelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val markwon = Markwon.create(this)

        try {
            val helpText = assets.open("HELP.md").bufferedReader().use { it.readText() }
            markwon.setMarkdown(binding.helpContentTextView, helpText)
        } catch (e: Exception) {
            binding.helpContentTextView.text = "Error loading help file: ${e.localizedMessage}"
        }
    }
}
