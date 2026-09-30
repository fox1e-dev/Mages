package org.mlm.mages.activities

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import co.touchlab.kermit.Logger
import androidx.appcompat.app.AppCompatActivity
import org.mlm.mages.R
import org.mlm.mages.push.PREF_INSTANCE
import org.unifiedpush.android.connector.UnifiedPush

class DistributorPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val distributors = UnifiedPush.getDistributors(this)
        val saved = UnifiedPush.getSavedDistributor(this)

        Logger.i("DistributorPicker: distributors=$distributors, saved=$saved")

        when {
            distributors.isEmpty() -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.distributor_none_title)
                    .setMessage(R.string.distributor_none_msg)
                    .setPositiveButton(R.string.dialog_ok) { _, _ -> finish() }
                    .show()
            }
            distributors.size == 1 -> {
                val dist = distributors.first()
                UnifiedPush.saveDistributor(this, dist)
                UnifiedPush.register(this, PREF_INSTANCE)

                val name = if (dist.contains(packageName)) getString(R.string.distributor_fcm) else dist
                AlertDialog.Builder(this)
                    .setTitle(R.string.distributor_title)
                    .setMessage(getString(R.string.distributor_using, name))
                    .setPositiveButton(R.string.dialog_ok) { _, _ -> finish() }
                    .show()
            }
            else -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.distributor_select_title)
                    .setMessage(R.string.distributor_select_msg)
                    .setPositiveButton(R.string.dialog_continue) { _, _ -> launchPicker() }
                    .setNegativeButton(R.string.dialog_cancel) { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun launchPicker() {
        UnifiedPush.tryPickDistributor(this) { success ->
            if (success) UnifiedPush.register(this, PREF_INSTANCE)
            finish()
        }
    }
}
