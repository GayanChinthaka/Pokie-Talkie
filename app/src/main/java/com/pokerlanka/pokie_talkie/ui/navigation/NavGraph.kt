package com.pokerlanka.pokie_talkie.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.di.AppContainer
import com.pokerlanka.pokie_talkie.ui.call.CallScreen
import com.pokerlanka.pokie_talkie.ui.call.CallViewModel
import com.pokerlanka.pokie_talkie.ui.chat.ChatScreen
import com.pokerlanka.pokie_talkie.ui.chat.ChatViewModel
import com.pokerlanka.pokie_talkie.ui.home.HomeScreen
import com.pokerlanka.pokie_talkie.ui.home.HomeViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/$peerId"
    }
    object Call : Screen("call/{peerId}/{callType}") {
        fun createRoute(peerId: String, callType: CallType) = "call/$peerId/${callType.name}"
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
                }
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
                    telecomCallManager = appContainer.telecomCallManager
                )
            )

            ChatScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToCall = { targetPeerId, callType ->
                    navController.navigate(Screen.Call.createRoute(targetPeerId, callType))
                }
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
                    telecomCallManager = appContainer.telecomCallManager
                )
            )

            CallScreen(
                viewModel = callViewModel,
                onCallEnded = { navController.popBackStack() }
            )
        }
    }
}
