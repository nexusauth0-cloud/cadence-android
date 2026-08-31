package com.cadence.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Primary navigation destinations (§5/§6 IA): Home, Search, Library, Downloads.
 * These are persistent in the shell; every other entity is reached by drilling
 * down (push), never a new tab.
 */
enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Default.Home),
    Search("search", "Search", Icons.Default.Search),
    Library("library", "Library", Icons.Default.LibraryMusic),
    Downloads("downloads", "Downloads", Icons.Default.Download),
}
