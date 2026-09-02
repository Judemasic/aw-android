package net.activitywatch.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SyncSettingsActivity"

class SyncSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AWPreferences

    private lateinit var switchSyncEnabled: SwitchCompat
    private lateinit var tvSyncDirStatus: TextView
    private lateinit var btnChooseDir: Button
    private lateinit var btnSyncNow: Button
    private lateinit var tvSyncStatus: TextView

    // Guards against the switch listener firing when we set isChecked programmatically
    private var isUpdatingSwitch = false

    // Guards against a second tap while a manual sync is still running. SyncInterface also
    // refuses concurrent syncs, but that arrives as a "skipped" result rather than as nothing
    // happening, which reads like a failure.
    private var isSyncing = false

    private val openDocumentTree =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri = result.data?.data ?: return@registerForActivityResult

                // Only persist the subset of flags the provider actually granted — passing
                // modes the provider didn't offer causes SecurityException.
                val grantedFlags = result.data?.flags ?: 0
                val persistableFlags = grantedFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                // If no persistable flags were granted at all, the URI won't survive a reboot.
                // Abort so the previously-working directory config remains intact.
                if (persistableFlags == 0) {
                    Log.w(TAG, "Document provider granted no persistable flags — aborting directory update")
                    Toast.makeText(this, "Could not secure persistent access to selected directory", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // Take the NEW grant BEFORE releasing the old one. If takePersistableUriPermission
                // fails, we abort early so the previously-working grant remains intact.
                try {
                    contentResolver.takePersistableUriPermission(uri, persistableFlags)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not take persistable permission: ${e.message}")
                    Toast.makeText(this, "Could not secure persistent access to selected directory", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // New grant secured — now release a different old URI to stay within Android's
                // bounded persisted-grant allowance. Reselecting the current directory must not
                // release the grant we just took for that same URI.
                val oldUriStr = prefs.getSyncDirUri()
                if (oldUriStr != null) {
                    val oldUri = Uri.parse(oldUriStr)
                    if (oldUri != uri) {
                        try {
                            val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            contentResolver.releasePersistableUriPermission(oldUri, releaseFlags)
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Could not release old URI grant: ${e.message}")
                        }
                    }
                }

                prefs.setSyncDirUri(uri.toString())
                updateSyncDirStatus()
                Toast.makeText(this, "Sync directory configured", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_settings)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Sync Settings"
        }

        prefs = AWPreferences(this)

        switchSyncEnabled = findViewById(R.id.switch_sync_enabled)
        tvSyncDirStatus = findViewById(R.id.tv_sync_dir_status)
        btnChooseDir = findViewById(R.id.btn_choose_sync_dir)
        btnSyncNow = findViewById(R.id.btn_sync_now)
        tvSyncStatus = findViewById(R.id.tv_sync_status)

        refreshUI()

        switchSyncEnabled.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener
            prefs.setSyncEnabled(isChecked)
            // Notify the running BackgroundService so the scheduler starts/stops immediately
            // rather than waiting for the next service restart.
            startService(Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_SYNC_ENABLED_CHANGED
            })
        }

        btnChooseDir.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Suggest a sensible starting location (Downloads if available)
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownloads"))
            }
            openDocumentTree.launch(intent)
        }

        btnSyncNow.setOnClickListener { syncNow() }
    }

    /**
     * Run one sync immediately and report what it did.
     *
     * Sync is otherwise purely time-driven, so verifying a change meant toggling the switch off
     * and on and waiting out a 60-second timer with no in-app feedback - a cost paid on every
     * iteration of the multi-device work (roadmap 1.4/1.5).
     *
     * This deliberately does not require the sync switch to be on: an explicit tap is explicit
     * intent, and it makes a single sync testable without also arming the 15-minute scheduler.
     * A configured directory *is* required, because without one a sync would report success
     * while sharing nothing.
     */
    private fun syncNow() {
        if (isSyncing) return
        if (prefs.getSyncDirUri() == null) {
            tvSyncStatus.text = "Choose a sync directory first."
            return
        }

        isSyncing = true
        btnSyncNow.isEnabled = false
        tvSyncStatus.text = "Syncing\u2026"

        lifecycleScope.launch {
            try {
                // Both halves have to stay off the main thread: the constructor loads the native
                // library and initialises JNI logging, and the sync entry point reads this
                // device's id over HTTP before it returns. SyncScheduler starts on IO for the
                // same reason. The sync itself runs on SyncInterface's own executor, and the
                // result callback is posted back to the main looper from there.
                withContext(Dispatchers.IO) {
                    SyncInterface(this@SyncSettingsActivity)
                        .syncBothMultiDeviceAsync { success, message ->
                            onSyncFinished(success, message)
                        }
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "aw-sync native library unavailable", e)
                onSyncFinished(false, "aw-sync native library unavailable")
            } catch (e: Exception) {
                Log.e(TAG, "Could not start sync", e)
                onSyncFinished(false, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun onSyncFinished(success: Boolean, message: String) {
        isSyncing = false
        btnSyncNow.isEnabled = true
        tvSyncStatus.text = if (success) "Sync complete: $message" else "Sync failed: $message"
        Log.i(TAG, "Manual sync finished: success=$success, message=$message")
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) refreshUI()
    }

    private fun refreshUI() {
        isUpdatingSwitch = true
        switchSyncEnabled.isChecked = prefs.isSyncEnabled()
        isUpdatingSwitch = false
        updateSyncDirStatus()
    }

    private fun updateSyncDirStatus() {
        val uriStr = prefs.getSyncDirUri()
        tvSyncDirStatus.text = if (uriStr != null) {
            val displayName = resolveDisplayName(Uri.parse(uriStr)) ?: uriStr
            "Directory: $displayName"
        } else {
            "No sync directory configured. Tap \"Choose Directory\" to select one accessible to Syncthing or other sync tools."
        }
    }

    // Resolve a content:// tree URI to a human-readable path like "Downloads" or "Documents/aw-sync"
    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
