package com.example.zejioscafese.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.zejioscafese.R

class DashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Quick action buttons navigate via the activity
        view.findViewById<View>(R.id.btnQuickPos)?.setOnClickListener {
            (activity as? NavigationHost)?.navigateTo(Screen.POS)
        }
        view.findViewById<View>(R.id.btnQuickInventory)?.setOnClickListener {
            (activity as? NavigationHost)?.navigateTo(Screen.INVENTORY)
        }
        view.findViewById<View>(R.id.btnQuickReports)?.setOnClickListener {
            (activity as? NavigationHost)?.navigateTo(Screen.REPORTS)
        }
    }
}
