# Autonomous Potential Violation Eradicator (A.P.V.E)

> **Author / Автор:** blackgeyer/blackgeier | **Version / Версия:** 1.2.0 | **License / Лицензия:** GPLv3

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

---

## 📄 License / Лицензия
Distributed under the **GPLv3 License**. / Распространяется по лицензии **GPLv3**.
