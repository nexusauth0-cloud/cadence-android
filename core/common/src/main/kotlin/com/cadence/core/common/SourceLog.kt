package com.cadence.core.common

/**
 * Single redaction gateway for diagnostics (spike spec section 15, ADR-0004).
 * Adapters and layers must route any URL/snippet through this before logging.
 */
object SourceLog {

    private val CREDENTIAL_KEYS = listOf(
        "token", "key", "secret", "signature", "sig", "auth", "credential", "session", "sapisid",
    )

    /** Strips the query component: `https://h/p?a=1` -> `https://h/p`. */
    fun redactUri(uri: String): String {
        if (uri.isBlank()) return "<blank>"
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd < 0) return uri.substringBefore('?').take(200)
        val afterScheme = uri.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        if (pathStart < 0) return uri.substringBefore('?').take(200)
        val authority = afterScheme.take(pathStart)
        val path = afterScheme.substring(pathStart).substringBefore('?')
        return "${uri.take(schemeEnd)}://$authority${path.take(200)}"
    }

    /**
     * Truncates a diagnostic snippet to [max] chars and neutralizes anything
     * that looks like a credential assignment (`key=value` pairs whose key is
     * credential-ish). Used before surfacing wire fragments in errors/logs.
     */
    fun redactSnippet(text: String, max: Int = 200): String {
        val scrubbed = scrubCredentialAssignments(text.replace(Regex("\\s+"), " ").trim())
        return if (scrubbed.length <= max) scrubbed else scrubbed.take(max) + "…"
    }

    /** Header names are safe to log; header VALUES never are. */
    fun safeHeaderNames(headers: Map<String, String>): Set<String> = headers.keys.toSet()

    private fun scrubCredentialAssignments(text: String): String {
        var out = text
        for (key in CREDENTIAL_KEYS) {
            // Matches optional surrounding quotes on both key and value:
            //   "token":"abc123"  token: abc123  api_key=xyz
            val regex = Regex("(?i)(\"?" + key + "\"?)\\s*[=:]\\s*(\"[^\"]*\"|[^,;&}\\s]+)")
            out = out.replace(regex) { m -> m.groupValues[1] + "=<redacted>" }
        }
        return out
    }
}
