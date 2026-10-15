package ru.stohastika.sklyanka.billing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.pro.ProStore
import javax.inject.Singleton

/**
 * Связка порта магазина с контуром AppGallery (SKL-122, `ADR-0010` п. 2).
 *
 * Единственное место, где домен встречается с вендором: в `core-domain`, `core-data`
 * и в экранах о Huawei не знает никто, и подменить магазин в прогоне можно одной этой
 * связкой. Приложение без контура (`app` без зависимости на модуль) получит отказ
 * на этапе сборки графа Hilt, а не тихую пустую витрину.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppGalleryBillingModule {

    @Binds
    @Singleton
    abstract fun bindProStore(impl: AppGalleryProStore): ProStore
}
