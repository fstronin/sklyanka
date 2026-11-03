# Правила R8 сетевого контура аналитики (SKL-165).
#
# Провайдер закрыт портом `AnalyticsTransport` (`core-domain`): за пределы этого модуля
# не выходит ни один тип SDK. Правила поэтому узкие — сохраняется сам SDK, а не форма
# наших классов.

-keep class com.my.tracker.** { *; }
-dontwarn com.my.tracker.**

# Классы, которых в сборке нет осознанно. SDK проверяет их наличие сам и гасит
# соответствующие ветки (см. комментарий у зависимости в build.gradle.kts):
#
# 1. GMS: рекламный идентификатор и App Set ID. Рекламные идентификаторы запрещены (ПМ-26),
#    а `play-services-appset` приезжает замыканием поставщика и снят исключением группы.
# 2. Клиент Google Play Billing: авто-отслеживание покупок продукту не нужно (Р-94),
#    и без него SDK не падает — `GooglePlayProductHelper` проверяет классы через Class.forName.
-dontwarn com.google.android.gms.**
-dontwarn com.android.billingclient.**
