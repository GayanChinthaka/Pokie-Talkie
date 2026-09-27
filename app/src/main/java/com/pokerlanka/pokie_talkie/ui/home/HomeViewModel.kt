package com.pokerlanka.pokie_talkie.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

import com.pokerlanka.pokie_talkie.lan.ActiveTransportMode
import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
import com.pokerlanka.pokie_talkie.di.AppContainer

data class HomeUiState(
    val contacts: List<Contact> = emptyList(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val myDeviceName: String = "",
    val myUserId: String = "",
    val activeTransport: ActiveTransportMode = ActiveTransportMode.WIFI,
    val isScanningLan: Boolean = true,
    val showEditProfileDialog: Boolean = false,
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

    private val _showEditProfileDialog = MutableStateFlow(false)
    val showEditProfileDialog: StateFlow<Boolean> = _showEditProfileDialog.asStateFlow()

    private val _showAddContactDialog = MutableStateFlow(false)
    val showAddContactDialog: StateFlow<Boolean> = _showAddContactDialog.asStateFlow()

    init {
        viewModelScope.launch {
            appContainer.bluetoothTransportManager.syncPairedDevicesToContacts()
        }
    }

    private val dialogsFlow = combine(_showEditProfileDialog, _showAddContactDialog) { edit, add -> Pair(edit, add) }
    private val userAndTransportFlow = combine(userPrefs.userNameFlow, appContainer.connectivityMonitor.activeTransport) { name, transport -> Pair(name, transport) }

    val uiState: StateFlow<HomeUiState> = combine(
        contactRepository.getContacts(),
        chatRepository.getUnreadCounts(userPrefs.userId),
        _searchQuery,
        userAndTransportFlow,
        dialogsFlow
    ) { contacts, unreadMap, query, userAndTransport, dialogs ->
        val userName = userAndTransport.first
        val transport = userAndTransport.second
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
            isScanningLan = true,
            showEditProfileDialog = dialogs.first,
            showAddContactDialog = dialogs.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            myDeviceName = userPrefs.getUserName(),
            myUserId = userPrefs.userId,
            activeTransport = appContainer.connectivityMonitor.activeTransport.value
        )
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun openEditProfileDialog() {
        _showEditProfileDialog.value = true
    }

    fun closeEditProfileDialog() {
        _showEditProfileDialog.value = false
    }

    fun saveProfileName(newName: String) {
        appContainer.updateMyProfileName(newName)
        _showEditProfileDialog.value = false
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
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (callType == CallType.VIDEO && ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(ctx, "Microphone and Camera permissions are required to make calls", Toast.LENGTH_LONG).show()
            return false
        }
        val callId = UUID.randomUUID().toString()
        val isBt = !contact.isOnlineLan || appContainer.connectivityMonitor.activeTransport.value == ActiveTransportMode.BLUETOOTH
        return telecomCallManager.startOutgoingCall(
            recipientId = contact.id,
            recipientName = contact.name,
            callType = if (isBt) CallType.VOICE else callType,
            callId = callId,
            isBluetoothVoice = isBt
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
