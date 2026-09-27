# TGx101: переход на новую версию Telegram X

Все правки TGx101 — это обычные git-коммиты поверх тега официального Telegram X.
Чтобы перенести их на новую версию, коммиты «пересаживаются» (`rebase`) на новый тег,
конфликты разбираются по одному, затем идёт сборка и проверка по списку ниже.

Текущая основа: **Telegram X 0.29.0.1814** (коммит `9291ce1` «Version bump to `1814`»; тега на GitHub пока нет).
Основа записана в трёх местах, при переходе менять все три:
- здесь (и `OLD=` в разделе 2);
- первая строка `MOD_CHANGES.md` («Основа: …») + строка «Основа обновлена до …» в разделе «Прочее»;
- `README.md` (строки «Base» и «on top of»).

Номер сборки (`0.1.N`) менять не нужно: он считается автоматически по числу коммитов мода
(автор 1vank0), от основы не зависит и после переноса продолжает расти.

## 1. Подготовка

```sh
cd ~/Developer/Telegram-X
git status                               # дерево должно быть чистым
git branch backup/tgx101-$(date +%F)     # страховка: к ней всегда можно вернуться
git fetch origin --tags
git tag -l 'v0.*' --sort=-v:refname | head -5   # найти новый тег, например v0.29.1.1820
git log --oneline origin/main -- version.properties | head -3   # или коммит «Version bump to `NNNN`», если тега ещё нет
```

## 2. Перенос правок

```sh
NEW=v0.29.1.1820            # новый тег Telegram X (или хеш коммита «Version bump»)
OLD=9291ce1                 # текущая основа (0.29.0.1814)
git rebase --onto $NEW $OLD main
git submodule update --init --recursive   # обязательно сразу после rebase
```

**Важно:** если не обновить подмодули (`tdlib` и др.), следующий `git add -A` запишет в коммит
старую версию TDLib и тихо откатит обновление из новой версии Telegram X. Проверка:
`git diff $NEW HEAD -- tdlib` должен быть пустым.

Если git остановился на конфликте:
1. `git status` покажет файлы с конфликтами.
2. В каждом файле оставить **и** новый код Telegram X, **и** нашу правку. Нашу правку легко узнать
   по комментарию рядом (каждая правка мода подписана комментарием, зачем она нужна).
3. `git add <файл>` → `git rebase --continue`.
4. Если непонятно, как совместить, — `git rebase --abort` вернёт всё как было (ветка backup тоже есть).

Коммиты идут по одной функции, поэтому конфликт почти всегда касается одной понятной правки.
Название коммита (`git log`) подскажет, какую функцию он содержит.

### Где конфликты вероятнее всего

Файлы, которые Telegram X часто меняет сам и в которые мы тоже вносили правки:

| Файл | Наши правки |
|---|---|
| `ui/MessagesController.java` | «Прикрепить» (вложение над полем), подпись из поля ввода, звонок в меню, теги Избранного, кнопка тегов в шапке |
| `unsorted/Settings.java` | новые настройки: флаги `SETTING_FLAG_*` с `1 << 21` по `1 << 25`; флаг прокси `Tgx101Proxies.PROXY_FLAG_RETURN_DIRECT = 1 << 6` (проверить, что `PROXY_FLAG_*` в `Settings.java` его не заняли), качество и стабилизация кружков, поиск свайпом, счётчик реакций |
| `ui/SettingsDataController.java` | экран «101» (`MODE_TGX101`) со всеми пунктами мода |
| `ui/SettingsController.java`, `navigation/DrawerController.java` | пункт «101» в настройках; боковое меню без «Пригласить друзей» и «Помощь» |
| `core/Lang.java` | русские тексты мода (`Tgx101Strings`) |
| `mediaview/MediaCellView.java` | двойное нажатие в видеоплеере |
| `AndroidManifest.xml` | разрешения (`USE_FULL_SCREEN_INTENT`), сервисы, обработчик контактов, `queries` для распознавания речи |
| `res/values/strings.xml`, `ids.xml` | строки и id мода — всегда в конце файла |
| `component/chat/MessagesLoader.java` | поиск по тегам Избранного |
| `data/TGMessage.java`, `data/TGReactions.java` | порядок реакций по частоте |
| `player/RoundVideoRecorder.java` | качество кружков, стабилизация |
| `util/text/Text.java` | задержка подсветки цитат и ссылок |
| `ui/SettingsProxyController.java`, `telegram/TdlibUi.java` (addNewProxy), `telegram/TdlibManager.java` (смена сети) | вызовы `Tgx101Proxies`; в экране прокси число строк блока автопереключения — константа `AUTO_SWITCH_ITEM_COUNT` |
| `data/TD.java` (getDownloadedFile, saveFiles, saveFile), `component/chat/MessageView.java` | сохранение голосовых и кружков |
| `ui/ChatsController.java` (`list()`) | фильтр `Tgx101Proxies.chatListFilter` — скрытие канала спонсора прокси |
| `data/TGMessageText.java` (setText), `data/TGMessageMedia.java` (setCaption) | вызов `Tgx101Text.displayText` — скрытие «Подписаться» |
| `data/TGMessage.java` (getHeaderPadding, draw) | `getPostGap()` — отступ и линия между постами каналов |
| `core/Lang.java` (getStringImpl ×2) | `AppName` не берётся из облачного перевода |
| `app/build.gradle.kts` (`resValue AppName`) | отображаемое имя TGx1Ø1 |
| `ui/MainController.java` (shareIntentImpl) | `Tgx101ChatImport.tryHandle` — импорт выгрузки WhatsApp |
| `ui/MessagesController.java` (меню ⋮) | «Экспорт чата» → `Tgx101ChatExport` |
| `ui/MessagesController.java` (меню ⋮), `component/chat/MessagesManager.java` (openChat, getUsedTranslateStyleMode), `data/TGMessage.java` (onAttachedToView → applyWholeChatTranslation) | «Перевести чат»; язык хранится в `Settings.getWholeChatTranslateLanguage` |
| `data/TGMessage.java` (valueOf: MessageInvoice, MessagePaidMedia; replaceMessageContent), `data/TGInlineKeyboard.java` (Buy), `telegram/TdlibUi.java` (InternalLinkTypeInvoice), `component/chat/MessageView.java` + `ui/MessagesController.java` (меню «Оплатить»), `ui/SettingsController.java` (пункт «Звёзды») | звёзды → `Tgx101Stars`, `Tgx101StarsController` |
| `ui/CallController.java` (onCreateView: фон, фото круг / на весь экран / нет, текст по центру; updateControlsAlpha без сдвига кнопок) | экран звонка → `Tgx101CallBackground`, `Settings.getCallPhotoMode/getCallPattern` |
| `res/values/colors.xml` (`splash`) | заставка всегда тёмная |
| `ui/MessagesController.java` (updateBottomBar), `ui/SettingsDataController.java` | `Settings.showChannelMuteButton` — кнопка уведомлений в каналах |
| `kotlin/tgx/td/TdExt.kt` (`isUnsupported` → false), `data/TGReaction.java` (конструктор для ⭐), `telegram/Tdlib.java` (getReaction: ReactionTypePaid), `data/TGReactions.java` (toggleReaction) | звёздные реакции → `Tgx101Stars.sendPaidReaction` |
| `app/jni/tgvoip/CMakeLists.txt` (tgx101_group.cpp в tgcallsjni + ссылка на FFmpeg), `telegram/TdlibUi.java` (openVoiceChatInvitation / openVoiceChat), `MainActivity.java` (handleIntent: ACTION_OPEN голосового чата), `ui/MessagesController.java` (меню ⋮), `AndroidManifest.xml` (Tgx101GroupCallService) | голосовые чаты → `Tgx101GroupCall`, `tgx101_group.cpp` |
| `MainActivity.java` (onResume) | `Tgx101Updates.checkIfNeeded` — уведомление о новой версии |
| `ui/MessageOptionsPagerController.java` (высота подзаголовка, getContentOffset), `ui/MessagesController.java` (updateBottomWrapOffset, onFocus) | отступ 16 dp снизу при навигации жестами, `Settings.isGestureNavigation` |
| `data/TGMessage.java` (valueOf: unsupported), `component/chat/MessageView.java` | тип неподдерживаемого сообщения; `MessageRichMessage` → `Tgx101RichMessage` (убран из списка unsupported), «Открыть пост» |
| `app/jni/CMakeLists.txt` | путь к `libtdjni.so` (обход опечатки в `tdlib/source/install.sh`; можно убрать, когда Telegram X её исправит) |

**Важно про флаги настроек.** Если в новой версии Telegram X в `Settings.java` появятся свои
`SETTING_FLAG_* = 1 << 21` (или 22–25), наши флаги нужно сдвинуть на свободные номера, иначе
настройки перепутаются. Проверить: `grep -n "SETTING_FLAG_.* = 1 <<" app/src/main/java/org/thunderdog/challegram/unsorted/Settings.java`.

## 3. Сборка

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=~/Library/Android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleLatestUniversalRelease
```

Если Telegram X поменял версии SDK/NDK в `version.properties`, их нужно доустановить
(`sdkmanager "platforms;android-XX" "ndk;…"`).

Проверить, что нативный TDLib в APK совпадает с Java-частью (иначе возможны падения):

```sh
unzip -o -j app/build/outputs/apk/latestUniversal/release/TGx101-0.1.apk lib/arm64-v8a/libtdjni.so -d /tmp/tdcheck
strings /tmp/tdcheck/libtdjni.so | grep -c "$(cat tdlib/version.txt)"   # должно быть больше 0
```

## 4. Проверка на телефоне

Пройти весь список. Галочка — только после проверки на живом телефоне.

**Звонки и уведомления**
- [ ] Сообщение приходит, когда приложение свёрнуто (фоновое соединение выключено).
- [ ] Входящий звонок при заблокированном экране: экран загорается, показывается звонок, есть звук/вибрация.
- [ ] Ответ на звонок — собеседник слышит.

**Фото и кружки**
- [ ] HD-фото: отправляется, у себя в чате чёткое (не размытое).
- [ ] Кружок записывается в выбранном качестве, уходит кружком.
- [ ] Стабилизация «Гироскоп»: кружок не раскачивается сильнее, чем с «Выкл».
- [ ] Музыка ставится на паузу при записи и прослушивании голосового.

**Сообщения**
- [ ] «Прикрепить»: файл встаёт над полем, текст уходит подписью, нажатие — замена.
- [ ] Скрепка с набранным текстом: текст становится подписью.
- [ ] Расшифровка голосового (Transcribe).
- [ ] «Выделить текст» → ответ цитатой.
- [ ] Быстрые реакции: частые — первыми.
- [ ] Звёзды: Настройки → «Звёзды Telegram» показывает баланс и историю; счёт бота открывает подтверждение с ценой.
- [ ] Звонок: круглое чёткое фото под именем, тёмный фон с самолётиками; после звонка шапка главного экрана на месте.
- [ ] Заставка при запуске тёмная.
- [ ] 1Ø1 → «Кнопка уведомлений и колокольчик» выкл.: в канале внизу нет кнопки, звук в ⋮.
- [ ] «⋮ → Перевести чат»: сообщения на другом языке переводятся в пузырях, «Показать оригинал» возвращает.
- [ ] Теги в Избранном: кнопка в шапке, фильтр показывает только сообщения с тегом.

**Интерфейс**
- [ ] Свайп между папками сразу после прокрутки.
- [ ] Свайп вниз в самом верху списка чатов открывает поиск.
- [ ] Закреплённые: нажатие ведёт к сообщению и листает к следующему.
- [ ] Видео: двойное нажатие слева/справа — ±5 с, по центру — пауза.
- [ ] Цитаты не мигают при прокрутке канала.
- [ ] Меню чата: «Позвонить» первым, «Прикрепить» последним.

**Прочее**
- [ ] Контакты в телефонной книге, кнопка «Telegram» у контакта открывает чат.
- [ ] Канал с «Подписаться» в конце постов: строка скрыта, в тексте без неё ничего не пропало.
- [ ] Голосовое и кружок: пункт «Сохранить», файл появляется в «Музыке» / галерее.
- [ ] Прокси: «Скрывать канал спонсора» — после перезапуска канала спонсора нет в списке.
- [ ] Прокси: вставка одной и нескольких ссылок из буфера, «Скопировать все», «Удалить неработающие», QR-код.
- [ ] Имя TGx1Ø1 на рабочем столе и в уведомлениях; «1Ø1» первым пунктом настроек.
- [ ] Канал в плоском виде: между постами отступ с линией.
- [ ] Пост нового формата (например t.me/rrozetka/18478): текст в ленте, «Открыть пост» показывает его целиком.
- [ ] Экран «Настройки → 1Ø1»: все пункты на месте, на русском при русском языке приложения.
- [ ] Каналы: переключатели кнопки «Обсудить» и кнопки комментариев работают.

## 5. Выпуск

1. Дописать в `MOD_CHANGES.md` изменения этой версии.
2. Закоммитить и отправить: `git push tgx101 main:tgx101`.
3. Публичная сборка всех файлов (Android 7+, 6, 5, 4.1–4.4) в `~/Desktop/TGX/Версии/TGx101-<версия>/`:
   `scripts/tgx101/build-public.sh` (с `--no-old` — только Android 7+).
4. GitHub-релиз с тегом `tgx101-<версия>` и всеми APK — **обязательно**: по нему приложения
   узнают о новой версии, а universal-APK всегда должен лежать в последнем релизе:
   `gh release create tgx101-<версия> ~/Desktop/TGX/Версии/TGx101-<версия>/*.apk -R Ivan-k0/tgx101-android --title "TGx101 <версия>" --notes-file <список изменений>`
5. 4PDA: новый пост с изменениями и файлами (APK прикрепляются вручную), затем шапка:
   файлы — ссылками на вложения поста, по спойлерам «Android 7.0 и выше», «Android 6», «Android 5»,
   «Android 4.1–4.4»; прошлая версия — в спойлер «Прошлые версии».
