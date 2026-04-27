package org.righteffort.ourgrocerylist.client

import android.content.Context
import java.io.File

enum class FirebaseEnvironmentChoice {
    PRODUCTION,
    EMULATOR_ADB_REVERSE,  // 127.0.0.1 — physical device + adb reverse
    EMULATOR_AVD,          // 10.0.2.2  — Android Virtual Device
}

// Stored in noBackupFilesDir so it is not included in Auto Backup and survives
// neither across device restores nor reinstalls — intentional for a debug-only
// routing preference.
class EnvironmentChoiceRepository(private val file: File) {

    constructor(context: Context) : this(File(context.noBackupFilesDir, "firebase_env_choice"))

    fun get(): FirebaseEnvironmentChoice? =
        file.takeIf { it.exists() }
            ?.readText()
            ?.let { FirebaseEnvironmentChoice.valueOf(it) }

    fun save(choice: FirebaseEnvironmentChoice) = file.writeText(choice.name)

    fun clear() { file.delete() }
}
