package com.example.xcommandcenter.data.backup

/**
 * Ideas read from a file and not yet added (§2.80): the network, the name a new idea above them
 * would have and whether that is suggested, and what the reader noted about the file.
 */
data class IdeaImportPreview(
    val fileName: String?,
    val network: BackupData,
    val rootTitle: String,
    val wrap: Boolean,
    val notes: List<String> = emptyList(),
    val skipped: Int = 0,
) {
    /** How many ideas, and how many levels deep. */
    val count: Int get() = network.ideas.size
    val levels: Int get() = (IdeaNetwork.outline(network).maxOfOrNull { it.depth } ?: -1) + 1
}
