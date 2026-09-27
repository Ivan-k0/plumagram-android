/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/tgx101-android)
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
    RU.put(R.string.ChatListSection, "Чаты");
    RU.put(R.string.PullToSearch, "Поиск свайпом вниз");
    RU.put(R.string.Tgx101BottomGap, "Отступ под полем ввода и меню");
    RU.put(R.string.PullToSearchHint, "Когда список чатов уже в самом верху, ещё один свайп вниз открывает поиск. Отступ под полем ввода и меню: поднимает их на 16 dp над нижним краем экрана, чтобы они не лежали на полоске жестов. Только при навигации жестами: с кнопками навигации отступа нет.");
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
    RU.put(R.string.Tgx101CallsSection, "Звонки");
    RU.put(R.string.Tgx101CallPhoto, "Фото собеседника");
    RU.put(R.string.Tgx101CallPhotoCircle, "Круг");
    RU.put(R.string.Tgx101CallPhotoFullScreen, "На весь экран (как раньше)");
    RU.put(R.string.Tgx101CallPhotoNone, "Не показывать");
    RU.put(R.string.Tgx101CallPattern, "Узор из самолётиков");
    RU.put(R.string.Tgx101CallPatternHint, "Круг — чёткое фото под именем. На весь экран — прежний дизайн Telegram X. Узор из бумажных самолётиков еле заметный и неподвижный; за фото на весь экран его не видно.");
    RU.put(R.string.ShowChannelMuteButton, "Кнопка уведомлений и колокольчик");
    RU.put(R.string.ShowCommentsButton, "Кнопка комментариев под постами");
    RU.put(R.string.HideSubscribeLink, "Скрывать «Подписаться» под постами");
    RU.put(R.string.SeparateChannelPosts, "Отступ и линия между постами");
    RU.put(R.string.ChannelButtonsHint, "Без кнопки «Обсудить» группа обсуждения открывается из меню канала ⋮. Без кнопки комментариев комментарии открываются из меню поста. Без кнопки уведомлений звук канала включается и выключается в меню ⋮. Чтобы изменения применились, откройте канал заново. «Подписаться» убирается, только если это последняя строка поста и ссылка ведёт на этот же канал.");
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
    RU.put(R.string.ChatExport, "Экспорт чата");
    RU.put(R.string.TranslateWholeChat, "Перевести чат");
    RU.put(R.string.TranslateWholeChatOff, "Показать оригинал");
    RU.put(R.string.InvoicePrice, "Счёт: %1$s");
    RU.put(R.string.InvoicePaid, "Оплачено: %1$s");
    RU.put(R.string.InvoicePay, "Оплатить %1$s");
    RU.put(R.string.PaidMediaLocked, "🔒 Платный пост: %1$s");
    RU.put(R.string.PaidMediaUnlock, "Открыть за %1$s");
    RU.put(R.string.PaidMediaPartial, "Показано: 1 из %1$d");
    RU.put(R.string.StarsPayConfirm, "Оплатить «%1$s» — %2$s?\nНа балансе: %3$s");
    RU.put(R.string.StarsSubscribeConfirm, "Подписка «%1$s»: %2$s в месяц. Оформить?\nНа балансе: %3$s");
    RU.put(R.string.StarsNotEnough, "Не хватает звёзд: нужно %1$s, на балансе %2$s. Купить звёзды можно на Fragment или в официальном Telegram.");
    RU.put(R.string.StarsOpenFragment, "Открыть Fragment");
    RU.put(R.string.StarsPaid, "Оплачено");
    RU.put(R.string.StarsPayFailed, "Не удалось оплатить: %1$s");
    RU.put(R.string.Tgx101Stars, "Звёзды Telegram");
    RU.put(R.string.Tgx101StarsBalance, "Баланс");
    RU.put(R.string.Tgx101StarsBuy, "Купить звёзды на Fragment");
    RU.put(R.string.Tgx101StarsBuyHint, "Внутри TGx101 звёзды купить нельзя: только на Fragment (за TON) или в официальном Telegram. Купленные звёзды появятся здесь, и их можно тратить в ботах и каналах.");
    RU.put(R.string.Tgx101StarsHistory, "История");
    RU.put(R.string.Tgx101StarsHistoryEmpty, "Операций пока нет");
    RU.put(R.string.Tgx101StarsMore, "Показать ещё");
    RU.put(R.string.Tgx101StarsRefund, "возврат");
    RU.put(R.string.Tgx101StarsKindDeposit, "пополнение");
    RU.put(R.string.Tgx101StarsKindWithdrawal, "вывод");
    RU.put(R.string.Tgx101StarsKindSubscription, "подписка");
    RU.put(R.string.Tgx101StarsKindGift, "подарок");
    RU.put(R.string.Tgx101StarsKindReaction, "реакция");
    RU.put(R.string.Tgx101StarsKindPaidMedia, "платный пост");
    RU.put(R.string.Tgx101StarsKindSale, "продажа");
    RU.put(R.string.Tgx101StarsKindPurchase, "покупка");
    RU.put(R.string.PaidReactionHint, "Поддержать пост звёздами. Их получит автор, под постом появится ваша звёздная реакция.\nНа балансе: %1$s");
    RU.put(R.string.PaidReactionSent, "Отправлено: %1$s");
    RU.put(R.string.StarsCardUnsupported, "Это оплата картой. TGx101 платит только звёздами: оплатите в официальном Telegram.");
    RU.put(R.string.ChatExportFormatHint, "Вся история чата сохранится в «Загрузки/TGx101» одним файлом. Только текст: фото, видео и голосовые отмечаются как [Фото] и т. п.");
    RU.put(R.string.ChatExportHtml, "Веб-страница (HTML) — для чтения");
    RU.put(R.string.ChatExportTxt, "Обычный текст (TXT)");
    RU.put(R.string.ChatExportProgress, "Загружено сообщений: %1$d");
    RU.put(R.string.ChatExportHeader, "Сообщений: %1$d · экспорт %2$s");
    RU.put(R.string.ChatExportForwarded, "Переслано от %1$s");
    RU.put(R.string.ChatExportDone, "Экспортировано сообщений: %1$d\n%2$s");
    RU.put(R.string.ChatExportFailed, "Не удалось экспортировать: %1$s");
    RU.put(R.string.ChatImport, "Импортировать чат");
    RU.put(R.string.ChatImportHint, "Это выгрузка чата из другого мессенджера. Её можно импортировать в чат Telegram.");
    RU.put(R.string.ChatImportPrivateHint, "Это выгрузка чата WhatsApp с %1$s. Её можно импортировать в ваш чат Telegram с этим человеком: сообщения появятся со своими датами и пометкой «импортировано».");
    RU.put(R.string.ChatImportGroupHint, "Это выгрузка группы WhatsApp «%1$s». Её можно импортировать в группу Telegram, где вы администратор: сообщения появятся со своими датами и пометкой «импортировано».");
    RU.put(R.string.ChatImportSendAsFiles, "Отправить как файлы");
    RU.put(R.string.ChatImportPick, "Импортировать в…");
    RU.put(R.string.ChatImportPickPrivate, "Выберите личный чат с этим человеком");
    RU.put(R.string.ChatImportPickGroup, "Выберите группу, где вы администратор");
    RU.put(R.string.ChatImportConfirm, "Импортировать в «%1$s»?");
    RU.put(R.string.ChatImportProgress, "Загружаю чат и файлов: %1$d… Это может занять время, не закрывайте приложение.");
    RU.put(R.string.ChatImportDone, "Чат импортирован в «%1$s»");
    RU.put(R.string.ChatImportFailed, "Не удалось импортировать: %1$s");
    RU.put(R.string.Tgx101Updates, "Обновления");
    RU.put(R.string.Tgx101CheckUpdates, "Сообщать о новых версиях");
    RU.put(R.string.Tgx101CheckUpdatesHint, "Раз в день при открытии приложение проверяет на GitHub, не вышла ли новая версия TGx1Ø1, и показывает уведомление со ссылками на GitHub и 4PDA. Ничего не скачивается и не устанавливается само.");
    RU.put(R.string.Tgx101UpdateChannel, "Обновления TGx1Ø1");
    RU.put(R.string.Tgx101UpdateTitle, "Вышла TGx1Ø1 %1$s");
    RU.put(R.string.Tgx101UpdateText, "Нажмите, чтобы скачать новую версию");
    RU.put(R.string.OpenInBrowser, "Открыть в браузере");
    RU.put(R.string.UnsupportedKindStory, "история");
    RU.put(R.string.UnsupportedKindPaidMedia, "платное фото или видео");
    RU.put(R.string.UnsupportedKindChecklist, "список задач");
    RU.put(R.string.UnsupportedKindStakeDice, "ставка на кубик");
    RU.put(R.string.UnsupportedKindGroupCall, "групповой звонок");
    RU.put(R.string.UnsupportedKindGift, "подарок");
    RU.put(R.string.UnsupportedKindGiveaway, "приз розыгрыша");
    RU.put(R.string.UnsupportedKindShared, "выбранные чаты или пользователи");
    RU.put(R.string.RichMessageOpen, "Открыть пост");
    RU.put(R.string.RichMessageOpenHint, "Пост с фото и кнопками. Нажмите, чтобы открыть.");
    RU.put(R.string.RichMessageOpenFailed, "Не удалось загрузить пост: %1$s");
    RU.put(R.string.RichMessageOpenUnsupported, "В посте есть элементы, которые просмотрщик пока не умеет показывать");
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
