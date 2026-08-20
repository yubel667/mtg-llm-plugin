package com.mtgllm.plugin.data

/**
 * A default prompt definition loaded from prompts.json.
 *
 * To ship additional defaults in a future release, add them to the JSON with an
 * introducedInVersion greater than the current maximum. PromptDatabase will append
 * every unseen version without replacing prompts that already have the same name.
 */
internal data class DefaultPromptTemplate(
    val name: String,
    val content: String,
    val position: Int,
    val introducedInVersion: Int
) {
    fun toEntity(position: Int = this.position): PromptEntity {
        return PromptEntity(
            name = name,
            content = content,
            isDefault = true,
            position = position
        )
    }
}
