package com.asiainfo.satellite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.asiainfo.satellite.ui.screens.ARScreen
import com.asiainfo.satellite.ui.screens.ConstellationScreen
import com.asiainfo.satellite.ui.screens.MainScreen
import com.asiainfo.satellite.ui.screens.ShareScreen
import com.asiainfo.satellite.ui.screens.UserFormScreen

object Routes {
    const val MAIN = "main"
    const val CONSTELLATION = "constellation"
    const val AR = "ar"
    const val SHARE = "share"
    const val FORM = "form"
}

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onConstellation = { nav.navigate(Routes.CONSTELLATION) },
                onAR = { nav.navigate(Routes.AR) },
                onShare = { nav.navigate(Routes.SHARE) }
            )
        }
        composable(Routes.CONSTELLATION) { ConstellationScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.AR) { ARScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SHARE) {
            ShareScreen(onBack = { nav.popBackStack() }, onScan = { nav.navigate(Routes.FORM) })
        }
        composable(Routes.FORM) {
            UserFormScreen(onBack = { nav.popBackStack() }, onDone = {
                nav.navigate(Routes.CONSTELLATION) {
                    popUpTo(Routes.MAIN)
                }
            })
        }
    }
}
