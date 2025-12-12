package com.example.selftracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.selftracker.R
import com.example.selftracker.adapters.NotificationAdapter
import com.example.selftracker.database.SelfTrackerDatabase
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationBottomSheet : BottomSheetDialogFragment() {

    private lateinit var adapter: NotificationAdapter
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var database: SelfTrackerDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        database = SelfTrackerDatabase.getDatabase(requireContext())
        adapter = NotificationAdapter()

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_notifications)
        val emptyState = view.findViewById<TextView>(R.id.text_empty_state)
        val btnClear = view.findViewById<Button>(R.id.btn_clear_all)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Load Notifications
        scope.launch {
             database.notificationDao().getAllNotifications().collectLatest { list ->
                 if (list.isEmpty()) {
                     recyclerView.visibility = View.GONE
                     emptyState.visibility = View.VISIBLE
                     btnClear.visibility = View.GONE
                 } else {
                     recyclerView.visibility = View.VISIBLE
                     emptyState.visibility = View.GONE
                     btnClear.visibility = View.VISIBLE
                     adapter.submitList(list)
                 }
             }
        }
        
        // Mark all as read when opening
        markAsRead()

        // Clear All Action
        btnClear.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                database.notificationDao().clearAll()
            }
        }
    }
    
    private fun markAsRead() {
        scope.launch(Dispatchers.IO) {
             database.notificationDao().markAllAsRead()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel scope if needed, but DialogFragment handles lifecycle well usually
    }
}
