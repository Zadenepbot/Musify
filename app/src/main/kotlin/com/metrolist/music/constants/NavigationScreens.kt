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

// Max items pinned in navigation bar before items set to AUTOMATIC position are moved to the top bar
const val MAX_ITEMS_IN_NAV_BAR = 5

enum class NavigationItemPosition {
    NAV_BAR,
    TOP_BAR,
    AUTOMATIC,
    HIDDEN
}

enum class NavigationItemType {
    CORE,
    LIBRARY,
    OTHER
}

enum class NavigationScreens(
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
        NavigationItemPosition.AUTOMATIC
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
        fun getNavbarItems(): List<NavigationScreens> {
            // Get count of items manually pinned to the navigation bar
            val manualCount = enumEntries<NavigationScreens>().count {
                it.position() == NavigationItemPosition.NAV_BAR
            }

            // Calculate count of AUTOMATIC items that appear in the navigation bar
            var autoCount = maxOf(0, MAX_ITEMS_IN_NAV_BAR - manualCount)

            // Build list
            val list = buildList {
                for(item in NavigationScreens.entries) {
                    // Show manually pinned items
                    if(item.position() == NavigationItemPosition.NAV_BAR) {
                        add(item)
                    }

                    // Show AUTOMATIC items up to MAX_ITEMS_IN_NAV_BAR
                    if(item.position() == NavigationItemPosition.AUTOMATIC && autoCount > 0) {
                        add(item)
                        autoCount--
                    }
                }
            }

            return list
        }

        @Composable
        fun getTopbarItems(includeHidden: Boolean = false): List<NavigationScreens> {
            // Get count of items manually pinned to the navigation bar
            val manualCount = enumEntries<NavigationScreens>().count {
                it.position() == NavigationItemPosition.NAV_BAR
            }

            // Calculate count of AUTOMATIC items that appear in the navigation bar (they won't show in top bar)
            var autoCount = maxOf(0, MAX_ITEMS_IN_NAV_BAR - manualCount)

            // Build list
            val list = buildList {
                for(item in NavigationScreens.entries) {
                    // Don't show library items in top bar
                    if(item.type == NavigationItemType.LIBRARY) {
                        break
                    }

                    // Show manually pinned items
                    if(item.position() == NavigationItemPosition.TOP_BAR) {
                        add(item)
                    }

                    // Show hidden items (if applicable)
                    if(item.position() == NavigationItemPosition.HIDDEN && includeHidden) {
                        add(item)
                    }

                    // Show AUTOMATIC items above MAX_ITEMS_IN_NAV_BAR
                    if(item.position() == NavigationItemPosition.AUTOMATIC) {
                        if(autoCount > 0)   autoCount--
                        else                add(item)
                    }
                }
            }

            return list
        }
    }
}
