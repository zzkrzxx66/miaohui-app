package com.miaohui.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.miaohui.app.ui.navigation.AppNavigation
import com.miaohui.app.ui.theme.MiaoHuiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiaoHuiTheme {
                AppNavigation()
            }
        }
    }
}
