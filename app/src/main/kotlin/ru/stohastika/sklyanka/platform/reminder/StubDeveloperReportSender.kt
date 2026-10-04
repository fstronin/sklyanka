package ru.stohastika.sklyanka.platform.reminder

import ru.stohastika.sklyanka.feature.reminder.DeveloperReportOutcome
import ru.stohastika.sklyanka.feature.reminder.DeveloperReportSender
import javax.inject.Inject

/**
 * Заглушка отправки отчёта разработчику (ОВ-127, ПМ-31/ПМ-32).
 *
 * Первая версия сети не имеет: заглушка всегда отвечает «отправлено», ничего не собирая
 * и никуда не обращаясь. Состав отчёта и реальная отправка — отдельная задача по ПМ-32;
 * сюда встанет сетевой адаптер, не меняя экранный шов.
 */
class StubDeveloperReportSender @Inject constructor() : DeveloperReportSender {

    override suspend fun send(comment: String): DeveloperReportOutcome = DeveloperReportOutcome.SENT
}
