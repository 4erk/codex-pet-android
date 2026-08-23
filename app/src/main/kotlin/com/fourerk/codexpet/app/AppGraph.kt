package com.fourerk.codexpet.app

import android.app.Application
import android.annotation.SuppressLint
import com.fourerk.codexpet.diagnostics.DiagnosticsRepository
import com.fourerk.codexpet.notification.NotificationParser
import com.fourerk.codexpet.pet.PetAssetProvider
import com.fourerk.codexpet.pet.PetRepository
import com.fourerk.codexpet.settings.SettingsRepository
import com.fourerk.codexpet.task.TaskRepository
import com.fourerk.codexpet.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@SuppressLint("StaticFieldLeak") // Every retained Context is the process-lifetime Application.
object AppGraph {
    lateinit var application: Application
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var tasks: TaskRepository
        private set
    lateinit var diagnostics: DiagnosticsRepository
        private set
    lateinit var petAssets: PetAssetProvider
        private set
    lateinit var pets: PetRepository
        private set
    lateinit var notificationParser: NotificationParser
        private set
    lateinit var updates: AppUpdateManager
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Synchronized
    fun initialize(app: Application) {
        if (::application.isInitialized) return
        application = app
        settings = SettingsRepository(app, applicationScope)
        tasks = TaskRepository(applicationScope)
        diagnostics = DiagnosticsRepository()
        petAssets = PetAssetProvider(app)
        pets = PetRepository(app, settings, applicationScope)
        notificationParser = NotificationParser()
        updates = AppUpdateManager(app, settings, applicationScope)
        pets.loadCached()
        updates.checkIfDue()
    }
}
