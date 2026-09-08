# Autonomous Potential Violation Eradicator (A.P.V.E)

![License](https://img.shields.io/badge/License-GPLv3-blue.svg)
![API](https://img.shields.io/badge/Dependency-PacketEvents-orange.svg)

> **Author / Автор:** blackgeyer | **Version / Версия:** 1.3.1 | **License / Лицензия:** GPLv3  
> 📹 **Plugin Showcase / Видео с демонстрацией:** [Watch on YouTube](https://youtu.be/tICOrnjpwYc?si=7kcUQCJTbV0Q8HvM)

---

## 🇬🇧 English

High-performance, fully asynchronous Minecraft chat moderation plugin. Designed to keep your server chat clean with zero main-thread overhead. Powered by **PacketEvents** and **Aho-Corasick** pattern matching.

### 🚀 Key Features & Architecture
* **$O(N)$ Performance:** Uses Aho-Corasick string matching instead of heavy Regex, allowing instant processing of massive text streams.
* **100% Asynchronous Netty Interception:** Intercepts and drops chat packets at the network layer before they reach the server's main thread.
* **Smart Text Normalization:** Strips homoglyphs, obfuscated characters, and leetspeak bypasses automatically.
* **Offline Punishment Pipeline:** Issues warnings and executes configurable punishment commands automatically.

### 🛠 Installation
1. Install **PacketEvents** (Spigot/Paper version, NOT Bungee/Velocity proxy version) into `/plugins/`.
2. Install your primary punishment engine (e.g., **EssentialsX**, **AdvancedBan**, etc.) into `/plugins/`.
3. Place `A.P.V.E.jar` into `/plugins/`.
4. Restart the server.

### ⚙️ Technical Compatibility
* **Cores:** Paper, Purpur, Spigot etc. any normal bukkit-based core.
* **Minecraft Versions:** 1.20.5 – 1.21.x+ *(Recommended: 1.21.4+)*
* **Java:** 21

### ⚠️ Important Usage Notes
* Configure `config.yml` before deploying to production to set up punishment command templates and avoid false positives.
* Due to the internal normalization pipeline, all banned word patterns in `config.yml` must be entered using **Latin characters only** (e.g., use `shlyuha` instead of `шлюха`). Refer to `documentation.txt` for details.

### 💻 Commands & Permissions
| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/apve reload` | Reloads plugin configuration. | `apve.reload` | OP |
| `/apve warns show {player}` | Displays current warning count for a player. | `apve.warns.show` | OP |
| `/apve warns remove {player} {amount}` | Removes a specified number of warnings from a player. | `apve.warns.remove` | OP |
| `/apve warns clear {player}` | Resets all warnings for a player. | `apve.warns.clear` | OP |
| `/apve notify toggle` | Toggles staff alerts for detected violations. | `apve.notify.toggle` | OP |
| `/apve check {string}` | Normalizes and tests a string against filter rules. | `apve.check` | OP |
| `/apve help` | Displays available commands and usage info. | `apve.help` | OP |

---

## 🇷🇺 Русский

Высокопроизводительный асинхронный плагин модерации чата. Поддерживает чистоту сервера в автоматическом режиме с нулевой нагрузкой на главный поток сервера. Работает на базе **PacketEvents** и алгоритма **Aho-Corasick**.

### 🚀 Архитектурные преимущества
* **Скорость работы $O(N)$:** Использование алгоритма Ахо-Корасик вместо тяжелых регулярных выражений (Regex) обеспечивает мгновенный поиск совпадений.
* **Асинхронный перехват Netty:** Фильтрация и сброс запрещённых пакетов происходят на сетевом уровне до их обработки главным потоком сервера.
* **Встроенный нормализатор:** Автоматически нейтрализует обходы через замену букв (символы-омоглифы, цифры, спецсимволы).
* **Автоматические оффлайн-наказания:** Автоматический учёт предупреждений и исполнение команд блокировки.

### 🛠 Установка
1. Поместите плагин **PacketEvents** (Spigot/Paper версию, НЕ Proxy/Bungee/Velocity) в папку `/plugins/`.
2. Поместите плагин наказаний (например, **EssentialsX**) в папку `/plugins/`.
3. Загрузите файл `A.P.V.E.jar` в папку `/plugins/`.
4. Перезапустите сервер.

### ⚙️ Совместимость
* **Ядра:** Paper, Purpur, Spigot или любое другое ядро, основанное на Bukkit.
* **Версии Minecraft:** от 1.20.5 до 1.21.x+ *(Рекомендуемая: 1.21.4+)*
* **Java:** 21

### ⚠️ Важные предупреждения
* Перед запуском настройте `config.yml` и шаблоны команд наказаний, чтобы исключить ложные срабатывания.
* Из-за работы нормализатора все паттерны запрещённых слов в `config.yml` вносятся **строго латиницей** (пример: `шлюха` -> `shlyuha`). Подробный разбор — в `documentation.txt`.

### 💻 Команды и права
| Команда | Описание | Право | По умолчанию |
| :--- | :--- | :--- | :--- |
| `/apve reload` | Перезагружает конфигурацию плагина. | `apve.reload` | OP |
| `/apve warns show {player}` | Показывает количество предупреждений игрока. | `apve.warns.show` | OP |
| `/apve warns remove {player} {число}` | Снимает указанное количество предупреждений. | `apve.warns.remove` | OP |
| `/apve warns clear {player}` | Полностью очищает предупреждения игрока. | `apve.warns.clear` | OP |
| `/apve notify toggle` | Переключает получение уведомлений о нарушениях. | `apve.notify.toggle` | OP |
| `/apve check {строка}` | Проверяет и нормализует строку на предмет нарушений. | `apve.check` | OP |
| `/apve help` | Выводит список команд и их описание. | `apve.help` | OP |

---

## 📄 License / Лицензия
Distributed under the **GPLv3 License**. / Распространяется по лицензии **GPLv3**.
