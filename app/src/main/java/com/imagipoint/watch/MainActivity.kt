package com.imagipoint.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import com.imagipoint.watch.ui.ImagipointApp
import com.imagipoint.watch.ui.theme.ImagiPointTheme

class MainActivity : ComponentActivity() {
    private val permissionEpoch = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as ImagiPointApplication).graph
        setContent {
            ImagiPointTheme {
                ImagipointApp(
                    graph = graph,
                    externalPermissionEpoch = permissionEpoch.intValue,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch.intValue++
        val graph = (application as ImagiPointApplication).graph
        if (graph.hid.hasConnectPermission()) {
            graph.hid.initialize()
            graph.hid.refreshBondedDevices()
        }
    }
}
