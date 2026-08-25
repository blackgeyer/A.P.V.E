# Autonomous Potential Violation Eradicator (A.P.V.E)

> **Author / Автор:** blackgeyer/blackgeier | **Version / Версия:** 1.2.6 | **License / Лицензия:** GPLv3

---

## 🇬🇧 English

A Minecraft server plugin designed to keep your chat clean at all times without administration intervention. Powered by **PacketEvents**.


### 📌 This plugin can
* Automatic chat filtering and offline punishment issuance.
* Flexible configurations for punishment duration, toggles, and banned words in `config.yml`.
* Built-in text normalizer to handle chat bypasses.
* Target audience: English and Russian-speaking communities.

### 🛠 Installation
1. Download **PacketEvents** (Spigot version, NOT proxy like BungeeCord or Velocity) and place it in `/plugins/`.
2. Add a punishment plugin (e.g., **EssentialsX**) to `/plugins/`.
3. Place `A.P.V.E.jar` in `/plugins/`.
4. Restart your server.

### ⚙️ Core Compatibility
* **Cores:** Paper, Purpur, Spigot
* **Versions:** 1.20.5 – 26.2+ *(Recommended: 1.21.4+)*

### ⚠️ Important Notes
* Configure `config.yml` before starting the server to avoid false positives and crashes.
* Banned words in `config.yml` must be added in **Latin characters only** (e.g., `shlyuha`). See `documentation.txt` for details.

### Commands
* **/apve reload** —— Reloads a plugin's configuration. Needs `apve.reload` permission to execute (default: op).
* **/apve warns show {nickname}** —— Shows warns amount of a {player}. Needs `apve.warns.show` permission to execute (default: op).
* **/apve warns remove {nickname} {integer}** —— Removes a certain warns amount of a {player}. The removing warns amount equals the integer argument. Needs `apve.warns.remove` permission to execute (default: op)
* **/apve warns clear {nickname}** —— Clears all warn amount of the player. Needs `apve.warns.clear` permission to execute (default: op).
* **/apve help** —— Shows the available APVE commands and its description. Needs `apve.help` permission to execute. (default: op)

---

## 🇷🇺 Русский

Плагин для серверов Minecraft, поддерживающий чистоту чата в автоматическом режиме без участия администрации. Работает на базе **PacketEvents**.

### 📌 Возможности
* Автоматическая фильтрация чата и оффлайн-выдача наказаний.
* Гибкая настройка длительности, типов наказаний и запрещенных слов через `config.yml`.
* Встроенный нормализатор текста для предотвращения обхода фильтров.
* Создан для русскоязычной и англоязычной аудитории.

### 🛠 Установка
1. Поместите плагин **PacketEvents** (Spigot-версию, не Proxy/BungeeCord/Velocity) в папку `/plugins/`.
2. Поместите плагин на наказания (например, **EssentialsX**) в папку `/plugins/`.
3. Закиньте файл `A.P.V.E.jar` в папку `/plugins/`.
4. Перезапустите сервер.

### ⚙️ Совместимость
* **Ядра:** Paper, Purpur, Spigot
* **Версии:** от 1.20.5 до 26.2+ *(Рекомендуемая: 1.21.4+)*

### ⚠️ Важные предупреждения
* Обязательно настройте `config.yml` и синтаксис команд наказаний перед запуском, чтобы избежать ложных блокировок и сбоев.
* Из-за работы нормализатора все новые запрещенные слова в `config.yml` нужно вносить **строго латиницей** (пример: `шлюха` -> `shlyuha`). Полное описание — в `documentation.txt`.

### Commands
* **/apve reload** —— Перезагружает конфигурацию плагина. Нужно `apve.reload` право чтобы осуществлять (default: op).
* **/apve warns show {nickname}** —— Показывает число предупреждений {player}. Нужно `apve.warns.show` право чтобы осуществлять (default: op).
* **/apve warns remove {nickname} {integer}** —— Удаляет определённое количество предупреждений {player}. Удаляемое число предупреждении равна аргументу целого числа. Нужно `apve.warns.remove` право чтобы осуществлять (default: op).
* **/apve warns clear {nickname}** —— Очищает все предупреждения игрока. Нужно `apve.warns.clear` право чтобы осуществлять (default: op).
* **/apve help** —— Шоус доступные команды и их описание. Нужно `apve.help` право чтобы осуществлять (default: op)

---

## 📄 License / Лицензия
Distributed under the **GPLv3 License**. / Распространяется по лицензии **GPLv3**.

## Official channel of a creator in a youtube — @BlackGeyer2000
/ You can see advanced information about plugin in it.
