package com.pokerlanka.pokie_talkie.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.di.AppContainer
import com.pokerlanka.pokie_talkie.lan.ActiveTransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val contacts: List<Contact> = emptyList(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val myDeviceName: String = "",
    val myUserId: String = "",
    val activeTransport: ActiveTransportMode = ActiveTransportMode.WIFI,
    val connectionMode: ConnectionMode = ConnectionMode.WIFI,
    val isScanningLan: Boolean = true,
    val showAddContactDialog: Boolean = false
)

class HomeViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val contactRepository = appContainer.contactRepository
    private val chatRepository = appContainer.chatRepository
    private val telecomCallManager = appContainer.telecomCallManager
    private val userPrefs = appContainer.userPreferencesManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showAddContactDialog = MutableStateFlow(false)
    val showAddContactDialog: StateFlow<Boolean> = _showAddContactDialog.asStateFlow()

    private val userAndTransportFlow = combine(
        userPrefs.userNameFlow,
        appContainer.connectivityMonitor.activeTransport,
        userPrefs.connectionModeFlow
    ) { name, transport, mode -> Triple(name, transport, mode) }

    val uiState: StateFlow<HomeUiState> = combine(
        contactRepository.getContacts(),
        chatRepository.getUnreadCounts(userPrefs.userId),
        _searchQuery,
        userAndTransportFlow,
        _showAddContactDialog
    ) { contacts, unreadMap, query, userAndTransport, showAddDialog ->
        val userName = userAndTransport.first
        val transport = userAndTransport.second
        val mode = userAndTransport.third
        val filtered = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter { it.name.contains(query, ignoreCase = true) || it.ipAddress.contains(query) }
        }
        HomeUiState(
            contacts = filtered,
            unreadCounts = unreadMap,
            searchQuery = query,
            myDeviceName = userName,
            myUserId = userPrefs.userId,
            activeTransport = transport,
            connectionMode = mode,
            isScanningLan = true,
            showAddContactDialog = showAddDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            myDeviceName = userPrefs.getUserName(),
            myUserId = userPrefs.userId,
            activeTransport = appContainer.connectivityMonitor.activeTransport.value,
            connectionMode = userPrefs.getConnectionMode()
        )
    )

    fun toggleConnectionMode() {
        val nextMode = if (userPrefs.getConnectionMode() == ConnectionMode.WIFI) {
            ConnectionMode.BLUETOOTH
        } else {
            ConnectionMode.WIFI
        }
        appContainer.setConnectionMode(nextMode)

        val ctx = appContainer.context
        val message = when (nextMode) {
            ConnectionMode.WIFI -> {
                val transport = appContainer.connectivityMonitor.activeTransport.value
                val hasNet = transport == ActiveTransportMode.WIFI ||
                    appContainer.connectivityMonitor.hasInternet()
                if (hasNet) {
                    "Switched to Wi-Fi Mode"
                } else {
                    "Wi-Fi Mode (Turn on Wi-Fi or Internet)"
                }
            }
            ConnectionMode.BLUETOOTH -> {
                if (appContainer.bluetoothTransportManager.isBluetoothEnabled) {
                    "Switched to Bluetooth Mode"
                } else {
                    "Bluetooth Mode (Turn on Bluetooth)"
                }
            }
        }
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun openAddContactDialog() {
        _showAddContactDialog.value = true
    }

    fun closeAddContactDialog() {
        _showAddContactDialog.value = false
    }

    fun addContact(name: String, ipAddress: String) {
        viewModelScope.launch {
            appContainer.addManualContact(name, ipAddress)
            _showAddContactDialog.value = false
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            contactRepository.deleteContact(contactId)
        }
    }

    fun initiateCall(contact: Contact, callType: CallType): Boolean {
        val ctx = appContainer.context
        val isBtMode = userPrefs.getConnectionMode() == ConnectionMode.BLUETOOTH
        if (isBtMode && callType == CallType.VIDEO) {
            Toast.makeText(ctx, "Video calls require Wi-Fi mode", Toast.LENGTH_SHORT).show()
            return false
        }
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (callType == CallType.VIDEO && ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(ctx, "Microphone and Camera permissions are required to make calls", Toast.LENGTH_LONG).show()
            return false
        }
        val callId = UUID.randomUUID().toString()
        return telecomCallManager.startOutgoingCall(
            recipientId = contact.id,
            recipientName = contact.name,
            callType = if (isBtMode) CallType.VOICE else callType,
            callId = callId,
            isBluetoothVoice = isBtMode
        )
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(appContainer) as T
            }
        }
    }
}
