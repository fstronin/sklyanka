package ru.heuri.sklyanka.domain.violations

import android.os.Build
import androidx.room.Entity

// Нарушает НФТ-46: доменный слой не знает ни об Android, ни о Room.
// Правило — AndroidImportInDomain.

object AndroidImportInDomainSample {
    val sdk = Build.VERSION.SDK_INT
    val marker = Entity::class
}
