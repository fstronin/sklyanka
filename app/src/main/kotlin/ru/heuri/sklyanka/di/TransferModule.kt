package ru.heuri.sklyanka.di

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import ru.heuri.sklyanka.domain.transfer.AppStamp
import ru.heuri.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.heuri.sklyanka.domain.transfer.BuildTransferPlan
import ru.heuri.sklyanka.domain.transfer.ExportDataUseCase
import ru.heuri.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.heuri.sklyanka.domain.transfer.TransferCodec
import ru.heuri.sklyanka.domain.transfer.TransferFiles
import ru.heuri.sklyanka.domain.transfer.TransferIdSource
import ru.heuri.sklyanka.domain.transfer.TransferReplanner
import ru.heuri.sklyanka.domain.transfer.TransferSettingsStore
import ru.heuri.sklyanka.domain.transfer.TransferStore
import ru.heuri.sklyanka.domain.transfer.UuidIdSource
import ru.heuri.sklyanka.domain.transfer.ValidateSnapshot
import ru.heuri.sklyanka.platform.transfer.ContentResolverTransferFiles
import ru.heuri.sklyanka.platform.transfer.PlanningTransferReplanner
import javax.inject.Singleton

/**
 * Сборка переноса данных (SKL-19).
 *
 * Сами сценарии живут в домене и об Hilt не знают: домен не видит ни `javax.inject`,
 * ни `dagger` (НФТ-46), поэтому объекты собираются здесь. Платформенные части —
 * системный выбор места, внутреннее хранилище, настройки, планировщик — подставляются
 * реализациями из `app`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    @Singleton
    abstract fun bindTransferFiles(impl: ContentResolverTransferFiles): TransferFiles

    @Binds
    @Singleton
    abstract fun bindTransferReplanner(impl: PlanningTransferReplanner): TransferReplanner

    companion object {

        @Provides
        @Singleton
        fun provideIdSource(): TransferIdSource = UuidIdSource

        /**
         * Метаданные выгрузки (ПЕР-5).
         *
         * Версия читается у установленного пакета, а не набирается руками: файл, в котором
         * версия соврала, невозможно разобрать при жалобе «перенос потерял историю».
         */
        @Provides
        @Singleton
        @Suppress("DEPRECATION")
        fun provideAppStamp(@ApplicationContext context: Context): AppStamp {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // longVersionCode появился в API 28, а minSdk у проекта 26 (ПЛ-1).
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
            return AppStamp(versionName = info.versionName.orEmpty(), versionCode = code)
        }

        @Provides
        @Singleton
        fun provideValidateSnapshot(): ValidateSnapshot = ValidateSnapshot()

        @Provides
        @Singleton
        fun provideBuildTransferPlan(ids: TransferIdSource): BuildTransferPlan = BuildTransferPlan(ids)

        @Provides
        @Singleton
        @Suppress("LongParameterList")
        fun provideExportData(
            store: TransferStore,
            settings: TransferSettingsStore,
            codec: TransferCodec,
            files: TransferFiles,
            time: TimeSource,
            log: OperationLog,
            app: AppStamp,
        ): ExportDataUseCase = ExportDataUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            time = time,
            log = log,
            app = app,
        )

        @Provides
        @Singleton
        fun provideReadTransferFile(
            files: TransferFiles,
            codec: TransferCodec,
            validate: ValidateSnapshot,
            log: OperationLog,
        ): ReadTransferFileUseCase = ReadTransferFileUseCase(
            files = files,
            codec = codec,
            validate = validate,
            log = log,
        )

        @Provides
        @Singleton
        @Suppress("LongParameterList")
        fun provideApplyTransfer(
            store: TransferStore,
            settings: TransferSettingsStore,
            codec: TransferCodec,
            files: TransferFiles,
            activeProfile: ActiveProfileRepository,
            replanner: TransferReplanner,
            log: OperationLog,
            buildPlan: BuildTransferPlan,
        ): ApplyTransferUseCase = ApplyTransferUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            activeProfile = activeProfile,
            replanner = replanner,
            log = log,
            buildPlan = buildPlan,
        )
    }
}
