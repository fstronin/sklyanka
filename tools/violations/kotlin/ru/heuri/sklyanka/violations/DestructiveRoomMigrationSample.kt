package ru.heuri.sklyanka.violations

// Нарушает НФТ-9: разрушающая миграция стирает историю приёмов.
// Правило — DestructiveRoomMigration.

object DestructiveRoomMigrationSample {
    fun build(builder: Any) = builder
        .fallbackToDestructiveMigration()
        .build()
}
