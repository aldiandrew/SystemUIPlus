package com.aldiandrew.duos

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent
import androidx.annotation.RequiresApi
import android.os.Build

@RequiresApi(Build.VERSION_CODES.N)
class DuosTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!ShizukuManager.hasPermission()) {
            updateTile(Tile.STATE_UNAVAILABLE)
            return
        }

        val tile = qsTile
        tile?.state = Tile.STATE_UNAVAILABLE
        tile?.updateTile()

        if (CustomStatusBarService.isRunning) {
            ShizukuOverlayController.stop(
                this,
                restoreSystemBar = true
            ) {
                updateTile()
            }
        } else {
            ShizukuOverlayController.start(this) { success, _ ->
                updateTile()
                if (!success) {
                    tile?.state = Tile.STATE_INACTIVE
                    tile?.updateTile()
                }
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
    }

    private fun updateTile(forcedState: Int? = null) {
        val tile = qsTile ?: return

        tile.label = "Duos"

        if (Build.VERSION.SDK_INT >= 30) {
            tile.subtitle = "Custom Status Bar"
        }

        tile.icon = Icon.createWithResource(
            this,
            android.R.drawable.ic_menu_manage
        )

        tile.state = forcedState ?: when {
            !ShizukuManager.hasPermission() ->
                Tile.STATE_UNAVAILABLE
            CustomStatusBarService.isRunning ->
                Tile.STATE_ACTIVE
            else ->
                Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }
}
