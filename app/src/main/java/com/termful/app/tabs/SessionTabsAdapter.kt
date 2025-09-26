package com.termful.app.tabs

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.termful.app.tabs.fragments.*

/**
 * ViewPager2 adapter for the 4-tab system
 */
class SessionTabsAdapter(
    fragment: Fragment,
    private val sessionHandle: String
) : FragmentStateAdapter(fragment) {
    
    companion object {
        const val TAB_COUNT = 4
        const val TAB_TERMINAL = 0
        const val TAB_FILE_MANAGER = 1
        const val TAB_EDITOR = 2
        const val TAB_AGENT = 3
    }
    
    override fun getItemCount(): Int = TAB_COUNT
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_TERMINAL -> TerminalTabFragment.newInstance(sessionHandle)
            TAB_FILE_MANAGER -> FileManagerTabFragment.newInstance(sessionHandle)
            TAB_EDITOR -> EditorTabFragment.newInstance(sessionHandle)
            TAB_AGENT -> AgentTabFragment.newInstance(sessionHandle)
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }
    
    /**
     * Get tab type for position
     */
    fun getTabType(position: Int): TabType {
        return when (position) {
            TAB_TERMINAL -> TabType.TERMINAL
            TAB_FILE_MANAGER -> TabType.FILE_MANAGER
            TAB_EDITOR -> TabType.EDITOR
            TAB_AGENT -> TabType.AGENT
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }
    
    /**
     * Get position for tab type
     */
    fun getPositionForTabType(tabType: TabType): Int {
        return when (tabType) {
            TabType.TERMINAL -> TAB_TERMINAL
            TabType.FILE_MANAGER -> TAB_FILE_MANAGER
            TabType.EDITOR -> TAB_EDITOR
            TabType.AGENT -> TAB_AGENT
        }
    }
}