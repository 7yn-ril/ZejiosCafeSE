package com.example.zejioscafese.ui

/**
 * Represents the navigable screens accessible from the sidebar.
 */
enum class Screen {
    DASHBOARD,
    POS,
    ORDERS,
    INVENTORY,
    REPORTS,
    STAFF,
    SETTINGS
}

/**
 * Interface for activities that host sidebar navigation.
 * Fragments can call this to trigger navigation from quick-action buttons.
 */
interface NavigationHost {
    fun navigateTo(screen: Screen)
}
