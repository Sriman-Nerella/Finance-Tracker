package com.example.financetracker.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.financetracker.data.prefs.SessionMode
import com.example.financetracker.data.prefs.SessionStore
import com.example.financetracker.domain.session.SyncController
import com.example.financetracker.domain.session.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit

class WorkManagerSyncController(
    context: Context,
    private val store: SessionStore,
) : SyncController {

    private val wm = WorkManager.getInstance(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    init {
        // Keep periodic work in step with sign-in state (also cancels everything on sign-out).
        scope.launch {
            store.data.map { it.mode == SessionMode.SIGNED_IN }.distinctUntilChanged().collect { signedIn ->
                if (signedIn) {
                    wm.enqueueUniquePeriodicWork(
                        PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                            .setConstraints(constraints).build(),
                    )
                } else {
                    wm.cancelUniqueWork(PERIODIC)
                    wm.cancelUniqueWork(NOW)
                    wm.cancelUniqueWork(DEBOUNCED)
                }
            }
        }
    }

    override val status: Flow<SyncStatus> = combine(
        store.data,
        wm.getWorkInfosForUniqueWorkFlow(NOW),
        wm.getWorkInfosForUniqueWorkFlow(DEBOUNCED),
        wm.getWorkInfosForUniqueWorkFlow(PERIODIC),
    ) { s, now, debounced, periodic ->
        when {
            s.mode != SessionMode.SIGNED_IN -> SyncStatus.Disabled
            (now + debounced + periodic).any { it.state == WorkInfo.State.RUNNING } -> SyncStatus.Running
            s.lastSyncError != null ->
                SyncStatus.Failed(s.lastSyncError, Instant.ofEpochMilli(s.lastSyncErrorAt ?: 0L))
            s.lastSyncAt != null -> SyncStatus.Done(Instant.ofEpochMilli(s.lastSyncAt))
            else -> SyncStatus.Idle
        }
    }.distinctUntilChanged()

    override fun syncNow() {
        scope.launch {
            if (store.snapshot().mode != SessionMode.SIGNED_IN) return@launch
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }
    }

    override fun requestSync() {
        scope.launch {
            if (store.snapshot().mode != SessionMode.SIGNED_IN) return@launch
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .build()
            wm.enqueueUniqueWork(DEBOUNCED, ExistingWorkPolicy.REPLACE, req)
        }
    }

    private companion object {
        const val PERIODIC = "sync_periodic"
        const val NOW = "sync_now"
        const val DEBOUNCED = "sync_debounced"
        const val DEBOUNCE_SECONDS = 10L
    }
}
