package com.example.bili2media.cache.scanner

import java.util.Locale

class BiliCacheEntryFileSelector {
    fun <T> select(candidates: Iterable<T>, nameOf: (T) -> String): T? {
        return candidates
            .filter { nameOf(it).equals(ENTRY_FILE_NAME, ignoreCase = true) }
            .minWithOrNull(
                compareBy<T> { if (nameOf(it) == ENTRY_FILE_NAME) 0 else 1 }
                    .thenBy { nameOf(it).lowercase(Locale.ROOT) }
                    .thenBy { nameOf(it) }
            )
    }

    private companion object {
        const val ENTRY_FILE_NAME = "entry.json"
    }
}
