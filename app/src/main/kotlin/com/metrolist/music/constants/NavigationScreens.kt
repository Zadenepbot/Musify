/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.utils.rememberEnumPreference
import kotlin.enums.enumEntries

// Max items pinned in navigation bar before items set to "auto" are moved to the top bar
val MAX_ITEMS_IN_NAV_BAR = 6

enum class NavigationItemPosition {
    NAV_BAR,
    TOP_BAR,
    AUTO,
    HIDDEN
}

enum class NavigationItemType {
    CORE,
    LIBRARY,
    OTHER
}

enum class NavigationPreferences(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
    val key: Preferences.Key<String>,
    val type: NavigationItemType,
    val default_position: NavigationItemPosition
) {
    HOME(
        R.string.home,
        R.drawable.home_outlined,
        R.drawable.home_filled,
        "home",
        stringPreferencesKey("nav_home_position"),
        NavigationItemType.CORE,
        NavigationItemPosition.NAV_BAR
    ),
    SEARCH(
        R.string.search,
        R.drawable.search,
        R.drawable.search,
        "search_input",
        stringPreferencesKey("nav_home_position"),
        NavigationItemType.OTHER,
        NavigationItemPosition.AUTO
    ),
    HISTORY(
        R.string.history,
        R.drawable.history,
        R.drawable.history,
        "history",
        stringPreferencesKey("nav_history_position"),
        NavigationItemType.OTHER,
        NavigationItemPosition.TOP_BAR
    ),
    STATS(
        R.string.stats,
        R.drawable.stats,
        R.drawable.stats,
        "stats",
        stringPreferencesKey("nav_stats_position"),
        NavigationItemType.OTHER,
        NavigationItemPosition.TOP_BAR
    ),
    LISTEN_TOGETHER(
        R.string.together,
        R.drawable.group_outlined,
        R.drawable.group_filled,
        "listen_together",
        stringPreferencesKey("nav_listen_together_position"),
        NavigationItemType.OTHER,
        NavigationItemPosition.TOP_BAR
    ),
    LIBRARY(
        R.string.filter_library,
        R.drawable.library_music_outlined,
        R.drawable.library_music_filled,
        "library",
        stringPreferencesKey("nav_library_position"),
        NavigationItemType.CORE,
        NavigationItemPosition.NAV_BAR
    );

    @Composable
    fun position(): NavigationItemPosition {
        return rememberEnumPreference(this.key, this.default_position).value
    }

    companion object {
        @Composable
        fun getNavbarItems(): List<NavigationPreferences> {
            // Get count of items manually pinned to the navigation bar
            val manualCount = enumEntries<NavigationPreferences>().count {
                it.position() == NavigationItemPosition.NAV_BAR
            }

            // Calculate count of AUTO items that should be shown too
            var autoCount = maxOf(0, MAX_ITEMS_IN_NAV_BAR - manualCount)

            // Build list
            val list = buildList {
                for(item in NavigationPreferences.entries) {
                    // Show manually pinned items
                    if(item.position() == NavigationItemPosition.NAV_BAR) {
                        add(item)
                    }

                    // Show AUTO items up to MAX_ITEMS_IN_NAV_BAR
                    if(item.position() == NavigationItemPosition.AUTO && autoCount > 0) {
                        add(item)
                        autoCount++
                    }
                }
            }

            return list
        }
    }
}
