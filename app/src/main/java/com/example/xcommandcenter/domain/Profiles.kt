package com.example.xcommandcenter.domain

/** Where a person's profile lives, worked out from its address. [short] labels it in a small badge. */
enum class Platform(val label: String, val short: String?, internal val hosts: List<String>, internal val keywords: List<String>) {
    X("X", "X", listOf("x.com", "twitter.com"), listOf("x", "twitter")),
    YOUTUBE("YouTube", "YT", listOf("youtube.com", "youtu.be"), listOf("youtube", "yt")),
    LINKEDIN("LinkedIn", "in", listOf("linkedin.com"), listOf("linkedin", "in")),
    INSTAGRAM("Instagram", "IG", listOf("instagram.com"), listOf("instagram", "insta", "ig")),
    FACEBOOK("Facebook", "FB", listOf("facebook.com", "fb.com"), listOf("facebook", "fb")),
    TIKTOK("TikTok", "TT", listOf("tiktok.com"), listOf("tiktok", "tt")),
    GITHUB("GitHub", "GH", listOf("github.com"), listOf("github", "gh")),
    THREADS("Threads", "Th", listOf("threads.net", "threads.com"), listOf("threads")),
    BLUESKY("Bluesky", "BS", listOf("bsky.app"), listOf("bluesky", "bsky")),
    /** Anything else: shown with a globe. */
    WEBSITE("Website", null, emptyList(), emptyList()),
}

/** What a person's item is, worked out from its text: shown as an icon. */
enum class PersonItemKind { IDEA, VIDEO, POST, LINK }

/** Reading and tidying a person's profile addresses and items. */
object Profiles {
    private val handle = Regex("""^(?:([A-Za-z]+)\s*[:\s]\s*)?@([\w.\-]+)$""")
    private val scheme = Regex("^https?://", RegexOption.IGNORE_CASE)

    /** Where each platform keeps a profile, from a bare handle. */
    private fun profileUrl(platform: Platform, name: String): String = when (platform) {
        Platform.YOUTUBE -> "youtube.com/@$name"
        Platform.LINKEDIN -> "linkedin.com/in/$name"
        Platform.INSTAGRAM -> "instagram.com/$name"
        Platform.FACEBOOK -> "facebook.com/$name"
        Platform.TIKTOK -> "tiktok.com/@$name"
        Platform.GITHUB -> "github.com/$name"
        Platform.THREADS -> "threads.net/@$name"
        Platform.BLUESKY -> "bsky.app/profile/$name"
        Platform.X, Platform.WEBSITE -> "x.com/$name"
    }

    /**
     * A profile as typed, ready to store: "@name" becomes an X profile, and "youtube @name" (or
     * "yt: @name", any platform's name or badge) that platform's; an address loses its "https://",
     * "www." and trailing slash. Blank stays blank.
     */
    fun normalize(input: String): String {
        val text = input.trim()
        handle.matchEntire(text)?.let { match ->
            val keyword = match.groupValues[1].lowercase()
            val platform = if (keyword.isEmpty()) Platform.X else Platform.entries.firstOrNull { keyword in it.keywords } ?: return text
            return profileUrl(platform, match.groupValues[2])
        }
        return text.replace(scheme, "").removePrefix("www.").removePrefix("WWW.").trimEnd('/')
    }

    /** The web address to open for a stored profile. */
    fun url(profile: String): String = if (scheme.containsMatchIn(profile)) profile else "https://${profile.trim()}"

    private fun host(address: String): String =
        address.trim().replace(scheme, "").substringBefore('/').substringBefore('?').lowercase().removePrefix("www.").removePrefix("m.")

    private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun platformOf(profile: String): Platform {
        val host = host(profile)
        return Platform.entries.firstOrNull { p -> p.hosts.any { matches(host, it) } } ?: Platform.WEBSITE
    }

    private val videoHosts = listOf("youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "twitch.tv", "rumble.com", "dailymotion.com")
    private val postHosts = listOf("x.com", "twitter.com", "threads.net", "threads.com", "bsky.app", "instagram.com", "facebook.com", "linkedin.com", "mastodon.social")

    /** A video for a link to a video site, a post for a social network's, another link, or an idea when there is no link. */
    fun kindOf(text: String): PersonItemKind {
        val link = WebLinks.find(text).firstOrNull() ?: return PersonItemKind.IDEA
        val host = host(link.url)
        return when {
            videoHosts.any { matches(host, it) } -> PersonItemKind.VIDEO
            postHosts.any { matches(host, it) } -> PersonItemKind.POST
            else -> PersonItemKind.LINK
        }
    }
}
