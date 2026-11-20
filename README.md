# 📦 Chest Log

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/37aSnw7evS)

[**English**](#english) | [**Русский**](#russian)

---

<a name="english"></a>
## 🇺🇸 English

**Chest Log** is a client-side Fabric mod that remembers your containers. Open a chest and a log next to it shows what changed since you last looked inside: `+12 Iron Ingot`, `−3 Diamond`. Perfect for shared bases, farms that fill chests and finding out who took your stuff. It works on **vanilla servers**: nothing is needed on the server.

### ✨ Features

- **What changed**: next to an open container you see everything that came in or went out since your last visit, with item icons and "last opened 2 h ago".
- **Your changes, live**: while the chest is open the panel also shows what you are putting in or taking out right now.
- **History per container**: a list like `+64 [iron] Dalkco 2m`, `−32 [iron] kruZ0k 30s` — green for added, red for taken.
- **Who took it? (CoreProtect)**: on servers with CoreProtect, one button in the panel turns the inspector on (`/co i`), clicks the chest, reads who added or took what (several pages if needed), turns the inspector off again and reopens the chest — the history now shows player names. Needs permission for `/co i`.
- **Where is it?**: search all remembered containers by item name (`/chestlog` or a key). Results show the amount, coordinates, distance and when you last saw it. Click a result and the container is outlined through walls in the world.
- **Containers**: chests (double chests count as one), trapped chests, barrels, shulker boxes, your ender chest; hoppers and dispensers optionally.
- **Saved per world**: data is kept separately for every server and singleplayer world.
- **Settings** from [Mod Menu](https://modrinth.com/mod/modmenu) or `/chestlog config`: panel side, history length, highlight time, which containers to track, logging your own changes, the CoreProtect button and how many pages to read, forgetting a world.
- **English and Russian**: the mod follows your game language.

### ⚙️ How it works

A client only knows what is inside a container when you open it. Chest Log saves the contents when you close a container and compares them the next time you open it, so changes made while you were away show up as soon as you look inside again.

### 📝 Commands

| Command | Action |
|---|---|
| `/chestlog` | Open the search |
| `/chestlog find <item>` | Search for an item |
| `/chestlog config` | Open the settings |
| `/chestlog clear` | Remove the highlights |

### 📥 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/).
2. Download [Fabric API](https://modrinth.com/mod/fabric-api).
3. Put **Chest Log** and Fabric API into your `mods` folder. Mod Menu is optional.

### 🔗 Support & Community

Join my Discord server for chat, support and news: [**Join Discord Server**](https://discord.gg/37aSnw7evS)

### 👨‍💻 Author

**Dalkco**

---

<a name="russian"></a>
## 🇷🇺 Русский

**Chest Log** — клиентский Fabric-мод, который запоминает твои контейнеры. Открываешь сундук — рядом журнал: что изменилось с прошлого раза: `+12 железных слитков`, `−3 алмаза`. Идеально для общих баз, ферм, которые сбрасывают лут в сундуки, и чтобы понять, кто забрал твои вещи. Работает на **ванильных серверах**: на сервере ничего не нужно.

### ✨ Возможности

- **Что изменилось**: рядом с открытым контейнером видно всё, что пришло или ушло с прошлого раза, с иконками предметов и «открывал 2 ч назад».
- **Твои изменения вживую**: пока сундук открыт, панель показывает, что ты кладёшь или забираешь прямо сейчас.
- **История каждого контейнера**: список вида `+64 [железо] Dalkco 2м`, `−32 [железо] kruZ0k 30с` — плюс зелёный, минус красный.
- **Кто брал? (CoreProtect)**: на серверах с CoreProtect одна кнопка в панели включает инспектор (`/co i`), кликает по сундуку, читает, кто что положил или забрал (несколько страниц, если нужно), выключает инспектор и снова открывает сундук — в истории появляются ники. Нужны права на `/co i`.
- **Где лежит?**: поиск по всем запомненным контейнерам по названию предмета (`/chestlog` или клавиша). В результатах — количество, координаты, расстояние и когда ты его видел. Клик — и контейнер подсвечивается в мире сквозь стены.
- **Контейнеры**: сундуки (двойной сундук — один контейнер), сундуки-ловушки, бочки, шалкеры, твой эндер-сундук; по желанию — воронки и раздатчики.
- **Сохранение по мирам**: данные хранятся отдельно для каждого сервера и одиночного мира.
- **Настройки** — из [Mod Menu](https://modrinth.com/mod/modmenu) или командой `/chestlog config`: сторона панели, длина истории, время подсветки, какие контейнеры запоминать, писать ли свои изменения, кнопка CoreProtect и сколько страниц читать, забыть мир.
- **Английский и русский**: мод переключается вместе с языком игры.

### ⚙️ Как это работает

Клиент знает, что лежит в контейнере, только когда ты его открываешь. Chest Log сохраняет содержимое при закрытии и сравнивает его при следующем открытии — поэтому изменения, сделанные без тебя, видны, как только ты снова заглянешь внутрь.

### 📝 Команды

| Команда | Действие |
|---|---|
| `/chestlog` | Открыть поиск |
| `/chestlog find <предмет>` | Найти предмет |
| `/chestlog config` | Открыть настройки |
| `/chestlog clear` | Убрать подсветку |

### 📥 Установка

1. Установите [Fabric Loader](https://fabricmc.net/use/).
2. Скачайте [Fabric API](https://modrinth.com/mod/fabric-api).
3. Положите **Chest Log** и Fabric API в папку `mods`. Mod Menu — по желанию.

### 🔗 Поддержка и общение

Присоединяйтесь к моему Discord-серверу: [**Присоединиться к Discord**](https://discord.gg/37aSnw7evS)

### 👨‍💻 Автор

**Dalkco**
