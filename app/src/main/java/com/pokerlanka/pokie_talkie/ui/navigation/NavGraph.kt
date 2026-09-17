package com.pokerlanka.pokie_talkie.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.di.AppContainer
import com.pokerlanka.pokie_talkie.ui.call.CallScreen
import com.pokerlanka.pokie_talkie.ui.call.CallViewModel
import com.pokerlanka.pokie_talkie.ui.chat.ChatScreen
import com.pokerlanka.pokie_talkie.ui.chat.ChatViewModel
import com.pokerlanka.pokie_talkie.ui.dev.DevConfig
import com.pokerlanka.pokie_talkie.ui.dev.DevMockCallScreen
import com.pokerlanka.pokie_talkie.ui.dev.DevPreviewHubScreen
import com.pokerlanka.pokie_talkie.ui.home.HomeScreen
import com.pokerlanka.pokie_talkie.ui.home.HomeViewModel

import com.pokerlanka.pokie_talkie.ui.settings.SettingsScreen
import com.pokerlanka.pokie_talkie.ui.settings.SettingsViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Chat : Screen("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/$peerId"
    }
    object Call : Screen("call/{peerId}/{callType}") {
        fun createRoute(peerId: String, callType: CallType) = "call/$peerId/${callType.name}"
    }
    object DevHub : Screen("dev_hub")
    object DevMockCall : Screen("dev_mock_call/{peerName}/{callType}/{callState}") {
        fun createRoute(peerName: String, callType: CallType, callState: CallState) =
            "dev_mock_call/$peerName/${callType.name}/${callState.name}"
    }
}

@Composable
fun PokieNavGraph(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(appContainer)
            )

            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToChat = { peerId ->
                    navController.navigate(Screen.Chat.createRoute(peerId))
                },
                onNavigateToCall = { peerId, callType ->
                    navController.navigate(Screen.Call.createRoute(peerId, callType))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToDevHub = {
                    navController.navigate(Screen.DevHub.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(appContainer)
            )

            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("peerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.provideFactory(
                    peerId = peerId,
                    myUserId = appContainer.currentUserId,
                    chatRepository = appContainer.chatRepository,
                    contactRepository = appContainer.contactRepository,
                    fcmSignalingClient = appContainer.fcmSignalingClient,
                    telecomCallManager = appContainer.telecomCallManager,
                    connectivityMonitor = appContainer.connectivityMonitor
                )
            )

            ChatScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToCall = { targetPeerId, callType ->
                    navController.navigate(Screen.Call.createRoute(targetPeerId, callType))
                },
                onChatOpened = { appContainer.onChatOpened(peerId) },
                onChatClosed = { appContainer.onChatClosed(peerId) }
            )
        }

        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType }
            )
        ) {
            val callViewModel: CallViewModel = viewModel(
                factory = CallViewModel.provideFactory(
                    callRepository = appContainer.callRepository,
                    webRtcManager = appContainer.webRtcManager,
                    fcmSignalingClient = appContainer.fcmSignalingClient,
                    telecomCallManager = appContainer.telecomCallManager,
                    bluetoothVoiceManager = appContainer.bluetoothVoiceManager,
                    ringtoneVibrationManager = appContainer.ringtoneVibrationManager
                )
            )

            CallScreen(
                viewModel = callViewModel,
                onCallEnded = { navController.popBackStack() }
            )
        }

        if (DevConfig.IS_DEV_MODE) {
            composable(Screen.DevHub.route) {
                DevPreviewHubScreen(
                    appContainer = appContainer,
                    onBack = { navController.popBackStack() },
                    onOpenMockCall = { peerName, callType, callState ->
                        navController.navigate(Screen.DevMockCall.createRoute(peerName, callType, callState))
                    },
                    onOpenChat = { peerId ->
                        navController.navigate(Screen.Chat.createRoute(peerId))
                    }
                )
            }

            composable(
                route = Screen.DevMockCall.route,
                arguments = listOf(
                    navArgument("peerName") { type = NavType.StringType },
                    navArgument("callType") { type = NavType.StringType },
                    navArgument("callState") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val peerName = backStackEntry.arguments?.getString("peerName") ?: "Family Member"
                val callTypeStr = backStackEntry.arguments?.getString("callType") ?: CallType.VOICE.name
                val callStateStr = backStackEntry.arguments?.getString("callState") ?: CallState.CONNECTED.name

                val callType = try { CallType.valueOf(callTypeStr) } catch (e: Exception) { CallType.VOICE }
                val callState = try { CallState.valueOf(callStateStr) } catch (e: Exception) { CallState.CONNECTED }

                DevMockCallScreen(
                    peerName = peerName,
                    initialCallType = callType,
                    initialCallState = callState,
                    webRtcManager = appContainer.webRtcManager,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
