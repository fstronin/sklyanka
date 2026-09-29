package ru.stohastika.sklyanka.ui.preview

import androidx.compose.runtime.Composable

/**
 * Превью каталога: по одному на компонент, каждое — в трёх темах и двух масштабах
 * шрифта (см. [SklyankaPreviews] и [PreviewEnvironments]).
 *
 * Превью намеренно повторяют состав каталога, а не показывают «типичный случай»:
 * состояние, которого нет в превью, не проверяется глазами вообще, а на устройстве
 * оно всё равно появится (критерий 10).
 */
@SklyankaPreviews
@Composable
internal fun TypographyPreview() = PreviewEnvironments { TypographyGallery() }

@SklyankaPreviews
@Composable
internal fun IntakeCardPreview() = PreviewEnvironments { IntakeCardGallery() }

@SklyankaPreviews
@Composable
internal fun ChipPreview() = PreviewEnvironments { ChipGallery() }

@SklyankaPreviews
@Composable
internal fun ButtonsPreview() = PreviewEnvironments { ButtonGallery() }

@SklyankaPreviews
@Composable
internal fun TextFieldPreview() = PreviewEnvironments { TextFieldGallery() }

@SklyankaPreviews
@Composable
internal fun SwitchPreview() = PreviewEnvironments { SwitchGallery() }

@SklyankaPreviews
@Composable
internal fun BannerPreview() = PreviewEnvironments { BannerGallery() }

@SklyankaPreviews
@Composable
internal fun ProgressPreview() = PreviewEnvironments { ProgressGallery() }

@SklyankaPreviews
@Composable
internal fun EmptyStatePreview() = PreviewEnvironments { EmptyStateGallery() }

@SklyankaPreviews
@Composable
internal fun ScreenHeaderPreview() = PreviewEnvironments { ScreenHeaderGallery() }

@SklyankaPreviews
@Composable
internal fun ProfileStripePreview() = PreviewEnvironments { ProfileStripeGallery() }

@SklyankaPreviews
@Composable
internal fun MarkPreview() = PreviewEnvironments { MarkGallery() }

@SklyankaPreviews
@Composable
internal fun LockupPreview() = PreviewEnvironments { LockupGallery() }
