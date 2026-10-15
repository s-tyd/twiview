package dev.twiview.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.twiview.ui.channel.ChannelScreen
import dev.twiview.ui.common.dismissKeyboardOnTouch
import dev.twiview.ui.home.CategoryScreen
import dev.twiview.ui.home.HomeScreen
import dev.twiview.ui.home.Nav
import dev.twiview.ui.player.MultiScreen
import dev.twiview.ui.player.PlayerScreen
import dev.twiview.ui.settings.SettingsScreen
import dev.twiview.ui.settings.SettingsSubPage
import dev.twiview.ui.theme.C

@Composable
fun AppNav(deepLink: MutableState<String?>) {
    val nc = rememberNavController()
    val nav = remember(nc) {
        Nav(
            stream = { nc.navigate("player/$it") { launchSingleTop = true } },
            channel = { nc.navigate("channel/$it") { launchSingleTop = true } },
            category = { nc.navigate("category/${Uri.encode(it)}") },
            vod = { nc.navigate("vod/$it") { launchSingleTop = true } },
            clip = { nc.navigate("clip/$it") { launchSingleTop = true } },
            multiStart = { logins -> nc.navigate("multi/" + logins.joinToString(",")) { launchSingleTop = true } },
            settings = { nc.navigate("settings") { launchSingleTop = true } },
            account = { nc.navigate("settings/account") { launchSingleTop = true } },
            back = { if (!nc.popBackStack()) nc.navigate("home") },
        )
    }
    LaunchedEffect(deepLink.value) {
        deepLink.value?.let {
            nc.navigate(it) { launchSingleTop = true }
            deepLink.value = null
        }
    }
    Surface(color = C.Bg, contentColor = C.Text) {
        Box(Modifier.fillMaxSize().dismissKeyboardOnTouch()) {
            NavHost(
                navController = nc,
                startDestination = "home",
                enterTransition = { fadeIn(tween(120)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(120)) },
                popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable("home") { HomeScreen(nav) }
                composable("player/{login}") { PlayerScreen(it.arguments!!.getString("login")!!, nav) }
                composable("channel/{login}") { ChannelScreen(it.arguments!!.getString("login")!!, nav) }
                composable("vod/{id}") { PlayerScreen("", nav, vodId = it.arguments!!.getString("id")!!) }
        composable("clip/{slug}") { PlayerScreen("", nav, clipSlug = it.arguments!!.getString("slug")!!) }
                composable("multi/{logins}") { MultiScreen(it.arguments!!.getString("logins")!!, nav) }
                composable("category/{name}") { CategoryScreen(it.arguments!!.getString("name")!!, nav) }
                composable("settings") { SettingsScreen(nav) { page -> nc.navigate("settings/${page.key}") { launchSingleTop = true } } }
                composable("settings/{page}") { SettingsSubPage(it.arguments!!.getString("page")!!, onBack = { nc.popBackStack() }) }
            }
        }
    }
}
