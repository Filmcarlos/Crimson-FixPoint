package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CoordinateFormat
import com.example.model.EarningsGoals
import com.example.model.FuelEstimateState
import com.example.model.MeasurementUnit
import com.example.model.PrivacyPreferences
import com.example.model.RiderProfileConfig
import com.example.model.UpdateFrequency
import com.example.model.VehicleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.riderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "crimson_fixpoint_datastore_v2"
)

data class StoredRiderPreferences(
    val legacyMigrated: Boolean = false,
    val profile: RiderProfileConfig = RiderProfileConfig(),
    val privacy: PrivacyPreferences = PrivacyPreferences(),
    val fuel: FuelEstimateState = FuelEstimateState(),
    val goals: EarningsGoals = EarningsGoals(),
    val activePinId: String? = null,
    val zoneRadiusKm: Float = 5.0f,
    val gpsBufferMeters: Int = 20,
    val alertVibrationEnabled: Boolean = true,
    val measurementUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val updateFrequency: UpdateFrequency = UpdateFrequency.STANDARD,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val keepScreenOn: Boolean = true,
    val lockEnabled: Boolean = false
)

class RiderPreferencesDataStore(context: Context) {
    private val dataStore = context.applicationContext.riderDataStore

    val preferencesFlow: Flow<StoredRiderPreferences> = dataStore.data.map { prefs ->
        StoredRiderPreferences(
            legacyMigrated = prefs[KEY_LEGACY_MIGRATED] ?: false,
            profile = RiderProfileConfig(
                riderName = prefs[KEY_RIDER_NAME] ?: "Rider FixPoint",
                vehicleModel = prefs[KEY_VEHICLE_MODEL] ?: "Honda Wave 125i",
                vehicleType = prefs[KEY_VEHICLE_TYPE]
                    ?.let { runCatching { VehicleType.valueOf(it) }.getOrNull() }
                    ?: VehicleType.MOTORCYCLE,
                licensePlate = prefs[KEY_LICENSE_PLATE] ?: "",
                fuelPricePerLiterBaht = prefs[KEY_FUEL_PRICE_PER_LITER] ?: 38.5f,
                maintenanceBahtPerKm = prefs[KEY_MAINTENANCE_BAHT_PER_KM] ?: 0.65f,
                minAcceptableBahtPerKm = prefs[KEY_MIN_BAHT_PER_KM] ?: 12.0f,
                minAcceptableBahtPerHour = prefs[KEY_MIN_BAHT_PER_HOUR] ?: 150.0f
            ),
            privacy = PrivacyPreferences(
                localOnlyMode = prefs[KEY_LOCAL_ONLY_MODE] ?: true,
                autoDeleteScreenshots = prefs[KEY_AUTO_DELETE_SCREENSHOTS] ?: true,
                storeRawOcrText = prefs[KEY_STORE_RAW_OCR] ?: false,
                screenReaderConsentAccepted = prefs[KEY_SCREEN_READER_CONSENT] ?: false,
                lowFuelAlertEnabled = prefs[KEY_LOW_FUEL_ALERT_ENABLED] ?: true,
                sessionReminderEnabled = prefs[KEY_SESSION_REMINDER_ENABLED] ?: true
            ),
            fuel = FuelEstimateState(
                tankCapacityLiters = (prefs[KEY_FUEL_TANK_LITERS] ?: 5.5f).coerceAtLeast(0.5f),
                consumptionKmPerLiter = (prefs[KEY_FUEL_KM_PER_LITER] ?: 38.0f).coerceAtLeast(1.0f),
                distanceDrivenSinceRefuelKm = (prefs[KEY_FUEL_DIST_DRIVEN_KM] ?: 0.0).coerceAtLeast(0.0),
                lowFuelWarningLiters = (prefs[KEY_FUEL_LOW_WARN_LITERS] ?: 1.0f).coerceAtLeast(0.1f),
                lowFuelAlertTriggered = false
            ),
            goals = EarningsGoals(
                dailyGoalBaht = (prefs[KEY_GOAL_DAILY] ?: 1000.0).coerceAtLeast(0.0),
                weeklyGoalBaht = (prefs[KEY_GOAL_WEEKLY] ?: 6000.0).coerceAtLeast(0.0),
                monthlyGoalBaht = (prefs[KEY_GOAL_MONTHLY] ?: 24000.0).coerceAtLeast(0.0)
            ),
            activePinId = prefs[KEY_ACTIVE_PIN_ID]?.takeIf { it.isNotBlank() },
            zoneRadiusKm = (prefs[KEY_ZONE_RADIUS_KM] ?: 5.0f).coerceIn(0.0f, 30.0f),
            gpsBufferMeters = (prefs[KEY_GPS_BUFFER_METERS] ?: 20).coerceIn(0, 100),
            alertVibrationEnabled = prefs[KEY_ALERT_VIBRATION] ?: true,
            measurementUnit = prefs[KEY_MEASUREMENT_UNIT]
                ?.let { runCatching { MeasurementUnit.valueOf(it) }.getOrNull() }
                ?: MeasurementUnit.METRIC,
            updateFrequency = prefs[KEY_UPDATE_FREQUENCY]
                ?.let { runCatching { UpdateFrequency.valueOf(it) }.getOrNull() }
                ?: UpdateFrequency.STANDARD,
            coordinateFormat = prefs[KEY_COORDINATE_FORMAT]
                ?.let { runCatching { CoordinateFormat.valueOf(it) }.getOrNull() }
                ?: CoordinateFormat.DECIMAL,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: true,
            lockEnabled = prefs[KEY_LOCK_ENABLED] ?: false
        )
    }

    suspend fun getSnapshot(): StoredRiderPreferences = preferencesFlow.first()

    suspend fun saveAll(prefs: StoredRiderPreferences) {
        dataStore.edit { p ->
            p[KEY_LEGACY_MIGRATED] = prefs.legacyMigrated
            p[KEY_RIDER_NAME] = prefs.profile.riderName
            p[KEY_VEHICLE_MODEL] = prefs.profile.vehicleModel
            p[KEY_VEHICLE_TYPE] = prefs.profile.vehicleType.name
            p[KEY_LICENSE_PLATE] = prefs.profile.licensePlate
            p[KEY_FUEL_PRICE_PER_LITER] = prefs.profile.fuelPricePerLiterBaht
            p[KEY_MAINTENANCE_BAHT_PER_KM] = prefs.profile.maintenanceBahtPerKm
            p[KEY_MIN_BAHT_PER_KM] = prefs.profile.minAcceptableBahtPerKm
            p[KEY_MIN_BAHT_PER_HOUR] = prefs.profile.minAcceptableBahtPerHour

            p[KEY_LOCAL_ONLY_MODE] = prefs.privacy.localOnlyMode
            p[KEY_AUTO_DELETE_SCREENSHOTS] = prefs.privacy.autoDeleteScreenshots
            p[KEY_STORE_RAW_OCR] = prefs.privacy.storeRawOcrText
            p[KEY_SCREEN_READER_CONSENT] = prefs.privacy.screenReaderConsentAccepted
            p[KEY_LOW_FUEL_ALERT_ENABLED] = prefs.privacy.lowFuelAlertEnabled
            p[KEY_SESSION_REMINDER_ENABLED] = prefs.privacy.sessionReminderEnabled

            p[KEY_FUEL_TANK_LITERS] = prefs.fuel.tankCapacityLiters
            p[KEY_FUEL_KM_PER_LITER] = prefs.fuel.consumptionKmPerLiter
            p[KEY_FUEL_DIST_DRIVEN_KM] = prefs.fuel.distanceDrivenSinceRefuelKm
            p[KEY_FUEL_LOW_WARN_LITERS] = prefs.fuel.lowFuelWarningLiters

            p[KEY_GOAL_DAILY] = prefs.goals.dailyGoalBaht
            p[KEY_GOAL_WEEKLY] = prefs.goals.weeklyGoalBaht
            p[KEY_GOAL_MONTHLY] = prefs.goals.monthlyGoalBaht

            if (prefs.activePinId != null) {
                p[KEY_ACTIVE_PIN_ID] = prefs.activePinId
            } else {
                p.remove(KEY_ACTIVE_PIN_ID)
            }
            p[KEY_ZONE_RADIUS_KM] = prefs.zoneRadiusKm
            p[KEY_GPS_BUFFER_METERS] = prefs.gpsBufferMeters
            p[KEY_ALERT_VIBRATION] = prefs.alertVibrationEnabled
            p[KEY_MEASUREMENT_UNIT] = prefs.measurementUnit.name
            p[KEY_UPDATE_FREQUENCY] = prefs.updateFrequency.name
            p[KEY_COORDINATE_FORMAT] = prefs.coordinateFormat.name
            p[KEY_KEEP_SCREEN_ON] = prefs.keepScreenOn
            p[KEY_LOCK_ENABLED] = prefs.lockEnabled
        }
    }

    suspend fun updateProfile(profile: RiderProfileConfig) {
        dataStore.edit { p ->
            p[KEY_RIDER_NAME] = profile.riderName
            p[KEY_VEHICLE_MODEL] = profile.vehicleModel
            p[KEY_VEHICLE_TYPE] = profile.vehicleType.name
            p[KEY_LICENSE_PLATE] = profile.licensePlate
            p[KEY_FUEL_PRICE_PER_LITER] = profile.fuelPricePerLiterBaht
            p[KEY_MAINTENANCE_BAHT_PER_KM] = profile.maintenanceBahtPerKm
            p[KEY_MIN_BAHT_PER_KM] = profile.minAcceptableBahtPerKm
            p[KEY_MIN_BAHT_PER_HOUR] = profile.minAcceptableBahtPerHour
        }
    }

    suspend fun updatePrivacy(privacy: PrivacyPreferences) {
        dataStore.edit { p ->
            p[KEY_LOCAL_ONLY_MODE] = privacy.localOnlyMode
            p[KEY_AUTO_DELETE_SCREENSHOTS] = privacy.autoDeleteScreenshots
            p[KEY_STORE_RAW_OCR] = privacy.storeRawOcrText
            p[KEY_SCREEN_READER_CONSENT] = privacy.screenReaderConsentAccepted
            p[KEY_LOW_FUEL_ALERT_ENABLED] = privacy.lowFuelAlertEnabled
            p[KEY_SESSION_REMINDER_ENABLED] = privacy.sessionReminderEnabled
        }
    }

    suspend fun updateFuel(fuel: FuelEstimateState) {
        dataStore.edit { p ->
            p[KEY_FUEL_TANK_LITERS] = fuel.tankCapacityLiters
            p[KEY_FUEL_KM_PER_LITER] = fuel.consumptionKmPerLiter
            p[KEY_FUEL_DIST_DRIVEN_KM] = fuel.distanceDrivenSinceRefuelKm
            p[KEY_FUEL_LOW_WARN_LITERS] = fuel.lowFuelWarningLiters
        }
    }

    suspend fun updateGoals(goals: EarningsGoals) {
        dataStore.edit { p ->
            p[KEY_GOAL_DAILY] = goals.dailyGoalBaht
            p[KEY_GOAL_WEEKLY] = goals.weeklyGoalBaht
            p[KEY_GOAL_MONTHLY] = goals.monthlyGoalBaht
        }
    }

    suspend fun updateZoneSettings(activePinId: String?, radiusKm: Float, bufferMeters: Int) {
        dataStore.edit { p ->
            if (activePinId != null) p[KEY_ACTIVE_PIN_ID] = activePinId else p.remove(KEY_ACTIVE_PIN_ID)
            p[KEY_ZONE_RADIUS_KM] = radiusKm
            p[KEY_GPS_BUFFER_METERS] = bufferMeters
        }
    }

    suspend fun updateTelemetryPreferences(
        alertVibrationEnabled: Boolean,
        measurementUnit: MeasurementUnit,
        updateFrequency: UpdateFrequency,
        coordinateFormat: CoordinateFormat,
        keepScreenOn: Boolean,
        lockEnabled: Boolean
    ) {
        dataStore.edit { p ->
            p[KEY_ALERT_VIBRATION] = alertVibrationEnabled
            p[KEY_MEASUREMENT_UNIT] = measurementUnit.name
            p[KEY_UPDATE_FREQUENCY] = updateFrequency.name
            p[KEY_COORDINATE_FORMAT] = coordinateFormat.name
            p[KEY_KEEP_SCREEN_ON] = keepScreenOn
            p[KEY_LOCK_ENABLED] = lockEnabled
        }
    }

    companion object {
        private val KEY_LEGACY_MIGRATED = booleanPreferencesKey("legacy_migrated_v2")
        private val KEY_RIDER_NAME = stringPreferencesKey("rider_name")
        private val KEY_VEHICLE_MODEL = stringPreferencesKey("vehicle_model")
        private val KEY_VEHICLE_TYPE = stringPreferencesKey("vehicle_type")
        private val KEY_LICENSE_PLATE = stringPreferencesKey("license_plate")
        private val KEY_FUEL_PRICE_PER_LITER = floatPreferencesKey("fuel_price_per_liter")
        private val KEY_MAINTENANCE_BAHT_PER_KM = floatPreferencesKey("maintenance_baht_per_km")
        private val KEY_MIN_BAHT_PER_KM = floatPreferencesKey("min_baht_per_km")
        private val KEY_MIN_BAHT_PER_HOUR = floatPreferencesKey("min_baht_per_hour")

        private val KEY_LOCAL_ONLY_MODE = booleanPreferencesKey("local_only_mode")
        private val KEY_AUTO_DELETE_SCREENSHOTS = booleanPreferencesKey("auto_delete_screenshots")
        private val KEY_STORE_RAW_OCR = booleanPreferencesKey("store_raw_ocr")
        private val KEY_SCREEN_READER_CONSENT = booleanPreferencesKey("screen_reader_consent")
        private val KEY_LOW_FUEL_ALERT_ENABLED = booleanPreferencesKey("low_fuel_alert_enabled")
        private val KEY_SESSION_REMINDER_ENABLED = booleanPreferencesKey("session_reminder_enabled")

        private val KEY_FUEL_TANK_LITERS = floatPreferencesKey("fuel_tank_liters")
        private val KEY_FUEL_KM_PER_LITER = floatPreferencesKey("fuel_km_per_liter")
        private val KEY_FUEL_DIST_DRIVEN_KM = doublePreferencesKey("fuel_dist_driven_km")
        private val KEY_FUEL_LOW_WARN_LITERS = floatPreferencesKey("fuel_low_warn_liters")

        private val KEY_GOAL_DAILY = doublePreferencesKey("goal_daily")
        private val KEY_GOAL_WEEKLY = doublePreferencesKey("goal_weekly")
        private val KEY_GOAL_MONTHLY = doublePreferencesKey("goal_monthly")

        private val KEY_ACTIVE_PIN_ID = stringPreferencesKey("active_pin_id")
        private val KEY_ZONE_RADIUS_KM = floatPreferencesKey("zone_radius_km")
        private val KEY_GPS_BUFFER_METERS = intPreferencesKey("gps_buffer_meters")
        private val KEY_ALERT_VIBRATION = booleanPreferencesKey("alert_vibration")
        private val KEY_MEASUREMENT_UNIT = stringPreferencesKey("measurement_unit")
        private val KEY_UPDATE_FREQUENCY = stringPreferencesKey("update_frequency")
        private val KEY_COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val KEY_LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
    }
}
