/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.core;

import android.util.SparseArray;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;

/**
 * Russian texts for TGx101's own strings.
 *
 * Telegram's cloud language packs don't know the mod's keys, so without this every mod
 * string would stay English. English (res/values/strings.xml) is used for any other
 * language picked in the app.
 */
final class Tgx101Strings {
  private static final SparseArray<String> RU = new SparseArray<>();

  static {
    RU.put(R.string.Tgx101Settings, "1Ø1");
    RU.put(R.string.RecordingAndPhotos, "Запись и фото");
    RU.put(R.string.PauseMediaOnRecord, "Пауза музыки при записи и голосовых");
    RU.put(R.string.SendPhotosInHD, "Отправлять фото в HD");
    RU.put(R.string.WriteContactsToPhoneBook, "Контакты Telegram в телефонной книге");
    RU.put(R.string.TextSelection, "Выделение текста");
    RU.put(R.string.SelectTextToQuoteSetting, "Выделение текста для цитаты в ответе");
    RU.put(R.string.SelectText, "Выделить текст");
    RU.put(R.string.SelectTextToQuoteHint, "Выделите часть сообщения и нажмите «Ответить», чтобы процитировать только её");
    RU.put(R.string.BackgroundConnection, "Фоновое соединение");
    RU.put(R.string.KeepAliveConnectionSetting, "Держать соединение в фоне");
    RU.put(R.string.KeepAliveConnectionHint, "Приложение остаётся на связи с Telegram, даже когда закрыто, и сообщения приходят сразу, без push-сервисов Google. Чтобы скрыть уведомление службы, зажмите его и отключите.");
    RU.put(R.string.KeepAliveChannel, "Фоновое соединение");
    RU.put(R.string.KeepAliveNotification, "Подключено к Telegram");
    RU.put(R.string.RoundVideoQuality, "Качество кружков");
    RU.put(R.string.RoundVideoQualitySd, "Стандарт (240p)");
    RU.put(R.string.RoundVideoQualityHq, "HQ (320p)");
    RU.put(R.string.RoundVideoQualityHigh, "Высокое (480p)");
    RU.put(R.string.RoundVideoQualityMax, "Максимум (640p)");
    RU.put(R.string.RoundVideoQualityHint, "Примерный вес минуты: Стандарт 3 МБ, HQ 5 МБ, Высокое 9 МБ, Максимум 19 МБ. 640p — максимум, который Telegram принимает для кружков, поэтому при любом выборе кружок уходит кружком.");
    RU.put(R.string.RoundStabilization, "Стабилизация кружков");
    RU.put(R.string.RoundStabilizationOff, "Выкл");
    RU.put(R.string.RoundStabilizationSystem, "Системная");
    RU.put(R.string.RoundStabilizationGyro, "Гироскоп");
    RU.put(R.string.RoundStabilizationCamera2, "Camera2");
    RU.put(R.string.RoundStabilizationHint, "Телефоны ведут себя по-разному — выберите, что лучше работает на вашем. Системная: встроенная стабилизация камеры, на многих телефонах эффекта нет. Гироскоп: приложение гасит дрожание рук по гироскопу, слегка приближая картинку; работает на любом телефоне. Camera2: собственная стабилизация превью телефона на Android 13+; если телефон её не поддерживает, включается «Гироскоп».");
    RU.put(R.string.Transcribe, "Расшифровать");
    RU.put(R.string.TranscriptionClose, "Закрыть");
    RU.put(R.string.TranscriptionInProgress, "Распознаю речь…");
    RU.put(R.string.TranscriptionEmpty, "Речь не распознана.");
    RU.put(R.string.TranscriptionFailed, "Не удалось распознать речь.");
    RU.put(R.string.TranscriptionUnavailable, "Telegram не смог расшифровать сообщение (нужен Premium или исчерпан недельный лимит), а распознавание на телефоне недоступно (нужен Android 13+ и сервис распознавания речи).");
    RU.put(R.string.TranscriptionLanguageUnavailable, "Распознаватель речи на телефоне не поддерживает этот язык. Скачайте его в системных настройках голосового ввода.");
    RU.put(R.string.ChatListSection, "Список чатов");
    RU.put(R.string.PullToSearch, "Поиск свайпом вниз");
    RU.put(R.string.PullToSearchHint, "Когда список чатов уже в самом верху, ещё один свайп вниз открывает поиск.");
    RU.put(R.string.AttachFromMenu, "Прикрепить");
    RU.put(R.string.PendingAttachment, "Вложение");
    RU.put(R.string.SavedTags, "Теги");
    RU.put(R.string.SavedTagSubtitle, "тег %1$s");
    RU.put(R.string.SavedTagsInfo, "Реакции в Избранном работают как теги. Выберите тег, чтобы увидеть только отмеченные им сообщения.");
    RU.put(R.string.SavedTagsInfoPartial, "Реакции в Избранном работают как теги. Показаны теги из последних 3000 сообщений.");
    RU.put(R.string.SavedTagsEmpty, "В Избранном нет сообщений с реакциями. Поставьте реакцию на сообщение, чтобы отметить его тегом.");
    RU.put(R.string.SavedTagsScanning, "Ищу теги в Избранном…");
    RU.put(R.string.ChannelsSection, "Каналы");
    RU.put(R.string.ShowDiscussButton, "Кнопка «Обсудить»");
    RU.put(R.string.ShowCommentsButton, "Кнопка комментариев под постами");
    RU.put(R.string.HideSubscribeLink, "Скрывать «Подписаться» под постами");
    RU.put(R.string.SeparateChannelPosts, "Отступ и линия между постами");
    RU.put(R.string.ChannelButtonsHint, "Без кнопки «Обсудить» группа обсуждения открывается из меню канала ⋮. Без кнопки комментариев комментарии открываются из меню поста. Чтобы изменения применились, откройте канал заново. «Подписаться» убирается, только если это последняя строка поста и ссылка ведёт на этот же канал.");
    RU.put(R.string.ProxyPaste, "Вставить из буфера");
    RU.put(R.string.ProxyPasteNothing, "В буфере обмена нет ссылок на прокси");
    RU.put(R.string.ProxyPasteResult, "Добавлено прокси: %1$d, уже были в списке: %2$d");
    RU.put(R.string.ProxyCopyAll, "Скопировать все прокси");
    RU.put(R.string.ProxyCopyAllNothing, "Нет прокси, которыми можно поделиться ссылкой");
    RU.put(R.string.ProxyCopyAllResult, "Скопировано ссылок на прокси: %1$d");
    RU.put(R.string.ProxyCopyAllResultHttp, "Скопировано ссылок на прокси: %1$d. HTTP-прокси (%2$d) нельзя передать ссылкой");
    RU.put(R.string.ProxyRemoveUnavailable, "Удалить неработающие");
    RU.put(R.string.ProxyRemoveUnavailableConfirm, "Не ответили при повторной проверке: %1$d. Удалить их?");
    RU.put(R.string.ProxyCheckingAll, "Проверяю прокси…");
    RU.put(R.string.ProxyCheckNoNetwork, "Ничего не отвечает. Проверьте подключение к интернету");
    RU.put(R.string.ProxyAllAvailable, "Все прокси отвечают");
    RU.put(R.string.ProxyShowQr, "QR-код");
    RU.put(R.string.ProxyReturnDirect, "Возвращаться на прямое соединение");
    RU.put(R.string.ProxyHideSponsor, "Скрывать канал спонсора прокси");
    RU.put(R.string.ProxyAutoSwitchHintTgx101, "Переключать автоматически: если приложение долго не может подключиться, оно пробует другие прокси и прямое соединение. Возвращаться на прямое соединение: после смены сети (Wi-Fi, мобильная), если Telegram работает без прокси, прокси выключается. Не включайте, если провайдер замедляет Telegram: проверка этого не заметит. Скрывать канал спонсора прокси: канал, который владелец прокси закрепляет в списке чатов, не показывается. Применится после перезапуска приложения.");
  }

  /** Russian text for a mod string when the app language is Russian, otherwise null. */
  static @Nullable String localized (@Nullable TdApi.LanguagePackInfo languagePackInfo, int resId) {
    String text = RU.get(resId);
    if (text == null || !isRussian(languagePackInfo)) {
      return null;
    }
    return text;
  }

  private static boolean isRussian (@Nullable TdApi.LanguagePackInfo info) {
    return info != null && (startsWithRu(info.id) || startsWithRu(info.baseLanguagePackId) || startsWithRu(info.pluralCode));
  }

  private static boolean startsWithRu (@Nullable String code) {
    return code != null && (code.equals("ru") || code.startsWith("ru-") || code.startsWith("ru_"));
  }
}
