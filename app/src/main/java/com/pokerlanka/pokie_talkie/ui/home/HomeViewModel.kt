package com.pokerlanka.pokie_talkie.ui.home

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

import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
import com.pokerlanka.pokie_talkie.di.AppContainer

data class HomeUiState(
    val contacts: List<Contact> = emptyList(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val myDeviceName: String = "",
    val myUserId: String = "",
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

    val uiState: StateFlow<HomeUiState> = combine(
        contactRepository.getContacts(),
        chatRepository.getUnreadCounts(userPrefs.userId),
        _searchQuery,
        userPrefs.userNameFlow,
        combine(_showEditProfileDialog, _showAddContactDialog) { edit, add -> Pair(edit, add) }
    ) { contacts, unreadMap, query, userName, dialogs ->
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
            isScanningLan = true,
            showEditProfileDialog = dialogs.first,
            showAddContactDialog = dialogs.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(myDeviceName = userPrefs.getUserName(), myUserId = userPrefs.userId)
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

    fun initiateCall(contact: Contact, callType: CallType) {
        val callId = UUID.randomUUID().toString()
        telecomCallManager.startOutgoingCall(
            recipientId = contact.id,
            recipientName = contact.name,
            callType = callType,
            callId = callId
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
