package com.example.data.repository

import com.example.data.local.FixPointDatabase
import com.example.data.local.RiderPreferencesDataStore
import com.example.model.CloudSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CloudSyncGateway {
    suspend fun evaluateReadiness(): CloudSyncState
    suspend fun requestCloudSync(): Result<CloudSyncState>
}

/**
 * Offline-first Cloud Sync readiness manager.
 * Prepares record counts, member flags, and backup timestamps locally without
 * fabricating a live cloud server upload when no backend project is provisioned.
 */
class OfflineCloudSyncPrepManager(
    private val database: FixPointDatabase,
    private val preferencesStore: RiderPreferencesDataStore
) : CloudSyncGateway {

    override suspend fun evaluateReadiness(): CloudSyncState = withContext(Dispatchers.IO) {
        val prefs = preferencesStore.getSnapshot()
        val totalRecords = database.savedPinDao().getAllPinsSnapshot().size +
            database.transactionDao().getAllTransactionsSnapshot().size +
            database.workSessionDao().getAllSessionsSnapshot().size +
            database.analyzedOrderDao().getAllOrdersSnapshot().size +
            database.jobOfferDao().getAllOffersSnapshot().size

        val statusLabel = when {
            prefs.cloudSyncOptIn && prefs.membershipProEnabled ->
                "คิวซิงก์พร้อมแล้ว (รอเชื่อมต่อ Cloud Backend)"
            prefs.cloudSyncOptIn ->
                "เปิดโหมดเตรียมซิงก์แล้ว (ออฟไลน์ในเครื่อง)"
            else ->
                "โหมด Offline-First ในเครื่อง 100%"
        }

        val detail = if (prefs.cloudSyncOptIn) {
            "ตรวจพบข้อมูลพร้อมซิงก์ $totalRecords รายการในฐานข้อมูล Room แต่ยังไม่ได้เชื่อมเซิร์ฟเวอร์ Cloud Backend ในบิลด์นี้ — ข้อมูลปลอดภัยในเครื่องและส่งออก JSON ได้ทันที"
        } else {
            "ข้อมูลทั้งหมด ($totalRecords รายการ) ถูกจัดเก็บถาวรในเครื่องผ่าน Room Database และสามารถสำรองเป็นไฟล์ JSON ได้ตลอดเวลา"
        }

        CloudSyncState(
            isMembershipProEnabled = prefs.membershipProEnabled,
            cloudSyncOptIn = prefs.cloudSyncOptIn,
            pendingSyncRecordsCount = totalRecords,
            lastLocalBackupMillis = prefs.lastLocalBackupMillis.takeIf { it > 0L },
            lastCloudSyncAttemptMillis = null,
            backendProvisioned = false,
            syncStatusLabelTh = statusLabel,
            syncDetailTh = detail
        )
    }

    override suspend fun requestCloudSync(): Result<CloudSyncState> {
        val state = evaluateReadiness()
        return Result.failure(
            IllegalStateException(
                "ระบบ Cloud Sync สมาชิกยังไม่ได้เชื่อมต่อเซิร์ฟเวอร์ปลายทางในบิลด์นี้ (Offline-first Room พร้อมซิงก์ ${state.pendingSyncRecordsCount} รายการ — ใช้การสำรอง JSON ชั่วคราวได้)"
            )
        )
    }
}
