# Правила сохранения R8 для релизной сборки (SKL-22, задачи 9 и 33).
#
# R8 не только обфусцирует: он удаляет недостижимый код и переписывает имена,
# поэтому всё, к чему обращаются по имени строкой, обязано быть объявлено здесь.
# Список намеренно короткий: основную работу делают правила самих библиотек,
# которые приезжают как consumer-правила вместе с артефактами (проверено по их
# содержимому, а не предположено):
#
#   * Room 2.6.1        — `-keep class * extends androidx.room.RoomDatabase`
#                         (`SklyankaDatabase` строится рефлексией в `DataModule`);
#   * WorkManager 2.9.1 — `-keep class * extends androidx.work.Worker/ListenableWorker`
#                         (`DayBoundaryWorker`, `RescheduleWorker` называются в
#                         `WorkRequest` строками и создаются системой);
#   * Hilt 2.52         — `@dagger.hilt.EntryPoint` и ранние точки входа
#                         (`ReminderEntryPoint`, `DirectBootEntryPoint` и т. п.);
#   * kotlinx.serialization 1.7.3 — `serializer()` и `Companion` у `@Serializable`
#                         (формат файла переноса, DTO в `core-data`);
#   * Compose / activity / lifecycle — правила runtime и ui приезжают с библиотеками;
#   * компоненты манифеста (Application, Activity, Receiver, Provider) — AGP сам
#     генерирует правила из слитого манифеста.
#
# Ниже — единственное, что не отдаётся библиотекой, и то с оговоркой: разбор имени
# перечисления, пришедшего строкой извне.
#
# Именем строкой оперирует вход на экран разрешений: `PermissionsActivity`
# достаёт `OnboardingSource` через `valueOf` из extras намерения (SKL-14).
# Без правила R8 вправе выбросить `valueOf`/`values` как «никем не вызванные»
# и превратить вход из настроек в молчаливый откат на источник по умолчанию.
#
# Совсем без правила не обойтись на уровне гарантий: `proguard-android-optimize.txt`
# сейчас содержит такое же правило, но он версионируется вместе с AGP и может
# измениться между выпусками плагина. Путь «имя строкой → перечисление» относится
# к корректности входа, поэтому правило закреплено в проекте, а не оставлено
# на волю версии SDK-файла.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
