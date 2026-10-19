package com.rn.library.parser

import com.rn.library.ui.Language

object GenreTagTranslator {

    private val ACRONYMS = setOf(
        "scp", "rpg", "vr", "vrmmo", "mmo", "mmorpg", "ai", "npc",
        "bl", "gl", "ntr", "pvp", "pve", "ui", "dnd", "d&d", "18+", "pov",
        "cg", "amv", "ost", "va", "mc", "op", "ed", "wip", "bd"
    )

    private fun normalizeKey(s: String): String =
        s.trim().lowercase().replace('ё', 'е').replace(Regex("""[-_\s]+"""), " ")

    fun cleanTag(raw: String): String =
        raw.trim()
            .trimStart('#', '@')
            .trim(',', ';', '.', '!', '?', ':', '"', '\'', ' ', '\t', '\n')

    // Dictionary of English/International terms to canonical Russian terms
    private val EN_TO_RU_BASE: Map<String, String> = mapOf(
        "action" to "Экшен",
        "action adventure" to "Приключенческий экшен",
        "adventure" to "Приключения",
        "adventures" to "Приключения",
        "comedy" to "Комедия",
        "drama" to "Драма",
        "fantasy" to "Фэнтези",
        "horror" to "Ужасы",
        "mystery" to "Детектив",
        "mysticism" to "Мистика",
        "psychological" to "Психология",
        "romance" to "Романтика",
        "sci fi" to "Фантастика",
        "scifi" to "Фантастика",
        "science fiction" to "Фантастика",
        "slice of life" to "Повседневность",
        "supernatural" to "Сверхъестественное",
        "thriller" to "Триллер",
        "tragedy" to "Трагедия",
        "historical" to "Исторический",
        "history" to "История",
        "martial arts" to "Боевые искусства",
        "harem" to "Гарем",
        "reverse harem" to "Реверс-гарем",
        "ecchi" to "Этти",
        "mecha" to "Меха",
        "school" to "Школа",
        "school life" to "Школьная жизнь",
        "sports" to "Спорт",
        "sport" to "Спорт",
        "music" to "Музыка",
        "military" to "Военное",
        "magic" to "Магия",
        "isekai" to "Попаданцы",
        "litrpg" to "ЛитРПГ",
        "game" to "Игры",
        "games" to "Игры",
        "video games" to "Игры",
        "board games" to "Настольные игры",
        "card games" to "Карточные игры",
        "virtual reality" to "Виртуальная реальность",
        "virtual world" to "Виртуальная реальность",
        "reincarnation" to "Реинкарнация",
        "transmigration" to "Попаданцы",
        "time travel" to "Путешествия во времени",
        "cultivation" to "Культивация",
        "cultivator" to "Культиватор",
        "xianxia" to "Сянься",
        "wuxia" to "Уся",
        "xuanhuan" to "Сюаньхуань",
        "monsters" to "Монстры",
        "monster" to "Монстры",
        "demons" to "Демоны",
        "demon" to "Демоны",
        "vampires" to "Вампиры",
        "vampire" to "Вампиры",
        "zombies" to "Зомби",
        "zombie" to "Зомби",
        "ghosts" to "Призраки",
        "ghost" to "Призраки",
        "spirits" to "Духи",
        "spirit" to "Духи",
        "gods" to "Боги",
        "god" to "Боги",
        "deity" to "Боги",
        "deities" to "Боги",
        "goddess" to "Богиня",
        "mythology" to "Мифология",
        "myth" to "Мифология",
        "myths" to "Мифология",
        "post apocalyptic" to "Постапокалипсис",
        "post apocalypse" to "Постапокалипсис",
        "postapocalyptic" to "Постапокалипсис",
        "apocalypse" to "Апокалипсис",
        "cyberpunk" to "Киберпанк",
        "steampunk" to "Стимпанк",
        "dystopia" to "Антиутопия",
        "dystopian" to "Антиутопия",
        "utopia" to "Утопия",
        "utopian" to "Утопия",
        "space" to "Космос",
        "outer space" to "Космос",
        "aliens" to "Пришельцы",
        "alien" to "Пришельцы",
        "space opera" to "Космическая опера",
        "superpowers" to "Суперспособности",
        "superpower" to "Суперспособности",
        "super power" to "Суперспособности",
        "super powers" to "Суперспособности",
        "superhero" to "Супергерои",
        "superheroes" to "Супергерои",
        "antihero" to "Антигерой",
        "anti hero" to "Антигерой",
        "overpowered protagonist" to "Сильный главный герой",
        "op protagonist" to "Сильный главный герой",
        "overpowered" to "Сильный главный герой",
        "strong to stronger" to "От сильного к сильнейшему",
        "weak to strong" to "От слабого к сильному",
        "male lead" to "ГГ мужчина",
        "female lead" to "ГГ женщина",
        "male protagonist" to "ГГ мужчина",
        "female protagonist" to "ГГ женщина",
        "smart male lead" to "Умный главный герой",
        "smart protagonist" to "Умный главный герой",
        "clever protagonist" to "Умный главный герой",
        "calm protagonist" to "Спокойный главный герой",
        "cautious protagonist" to "Осторожный главный герой",
        "ruthless protagonist" to "Беспощадный главный герой",
        "cold protagonist" to "Хладнокровный главный герой",
        "charismatic protagonist" to "Харизматичный главный герой",
        "evil protagonist" to "Злой главный герой",
        "anti social protagonist" to "Асоциальный герой",
        "politics" to "Политика",
        "political" to "Политика",
        "kingdom building" to "Развитие королевства",
        "nation building" to "Развитие королевства",
        "survival" to "Выживание",
        "dungeon" to "Подземелья",
        "dungeons" to "Подземелья",
        "dungeon crawler" to "Исследование подземелий",
        "tower climbing" to "Покорение башни",
        "crafting" to "Крафтинг",
        "alchemy" to "Алхимия",
        "alchemist" to "Алхимия",
        "summoning" to "Призыв",
        "summoner" to "Призыв",
        "swordsman" to "Мечник",
        "swordmaster" to "Мечник",
        "swordsmanship" to "Фехтование",
        "swordplay" to "Фехтование",
        "sword and magic" to "Меч и магия",
        "brotherhood" to "Братство",
        "revenge" to "Месть",
        "vengeance" to "Месть",
        "betrayal" to "Предательство",
        "evolution" to "Эволюция",
        "system" to "Система",
        "level up" to "Прокачка уровня",
        "level system" to "Система уровней",
        "leveling" to "Прокачка уровня",
        "guilds" to "Гильдии",
        "guild" to "Гильдии",
        "nobles" to "Аристократия",
        "aristocracy" to "Аристократия",
        "nobility" to "Аристократия",
        "royalty" to "Королевская семья",
        "war" to "Война",
        "warfare" to "Война",
        "crime" to "Криминал",
        "detective" to "Детектив",
        "police" to "Полиция",
        "mafia" to "Мафия",
        "gangs" to "Банды",
        "gang" to "Банды",
        "modern day" to "Современность",
        "modern" to "Современность",
        "modern world" to "Современность",
        "urban fantasy" to "Городское фэнтези",
        "dark fantasy" to "Тёмное фэнтези",
        "high fantasy" to "Высокое фэнтези",
        "low fantasy" to "Низкое фэнтези",
        "epic fantasy" to "Эпическое фэнтези",
        "progression fantasy" to "Прогрессивное фэнтези",
        "progression" to "Прогрессивное фэнтези",
        "romantic subplot" to "Романтическая линия",
        "slow romance" to "Медленная романтика",
        "enemies to lovers" to "От врагов к возлюбленным",
        "childhood friends" to "Друзья детства",
        "shounen" to "Сёнэн",
        "shonen" to "Сёнэн",
        "shoujo" to "Сёдзё",
        "shojo" to "Сёдзё",
        "seinen" to "Сэйнэн",
        "josei" to "Дзёсэй",
        "shoujo ai" to "Сёдзё-ай",
        "shounen ai" to "Сёнэн-ай",
        "yuri" to "Юри",
        "yaoi" to "Яой",
        "smut" to "Смат",
        "gore" to "Жестокость",
        "violence" to "Жестокость",
        "parody" to "Пародия",
        "satire" to "Сатира",
        "gag" to "Гэг",
        "family" to "Семья",
        "family life" to "Семейная жизнь",
        "pets" to "Питомцы",
        "pet companion" to "Питомцы",
        "familiars" to "Фамильяры",
        "familiar" to "Фамильяры",
        "dragons" to "Драконы",
        "dragon" to "Драконы",
        "rebirth" to "Перерождение",
        "second chance" to "Второй шанс",
        "time loop" to "Петля времени",
        "multiple pov" to "Несколько точек зрения",
        "first person" to "От первого лица",
        "third person" to "От третьего лица",
        "gamelit" to "Геймлит",
        "game elements" to "Игровые элементы",
        "villainess" to "Злодейка",
        "villain" to "Злодей",
        "hero" to "Герой",
        "secret identity" to "Скрытая личность",
        "necromancer" to "Некромантия",
        "necromancy" to "Некромантия",
        "assassin" to "Ассасины",
        "assassins" to "Ассасины",
        "thief" to "Воры",
        "thieves" to "Воры",
        "mage" to "Маг",
        "wizard" to "Волшебник",
        "magic user" to "Маг",
        "elemental magic" to "Магия стихий",
        "healing" to "Исцеление",
        "healer" to "Целитель",
        "immortals" to "Бессмертные",
        "immortal" to "Бессмертные",
        "artifacts" to "Артефакты",
        "artifact" to "Артефакты",
        "cheats" to "Читы",
        "cheat" to "Читы",
        "destiny" to "Судьба",
        "fate" to "Судьба",
        "secrets" to "Тайны",
        "secret" to "Тайны",
        "conspiracy" to "Заговор",
        "conspiracies" to "Заговор",
        "dark" to "Тёмное",
        "grimdark" to "Гримдарк",
        "strategy" to "Стратегия",
        "tactics" to "Тактика",
        "stealth" to "Скрытность",
        "shapeshifting" to "Смена облика",
        "body swap" to "Обмен телами",
        "gender bender" to "Смена пола",
        "gender bending" to "Смена пола",
        "otome game" to "Отомэ-игра",
        "otome" to "Отомэ-игра",
        "elven" to "Эльфы",
        "elves" to "Эльфы",
        "elf" to "Эльфы",
        "dwarves" to "Гномы",
        "dwarf" to "Гномы",
        "demi humans" to "Полулюди",
        "demi human" to "Полулюди",
        "beastkin" to "Зверолюди",
        "beastmen" to "Зверолюди",
        "fairy" to "Феи",
        "fairies" to "Феи",
        "angels" to "Ангелы",
        "angel" to "Ангелы",
        "fallen angels" to "Падшие ангелы",
        "underworld" to "Преисподняя",
        "hell" to "Ад",
        "heaven" to "Рай",
        "soul" to "Душа",
        "souls" to "Души",
        "curse" to "Проклятие",
        "curses" to "Проклятия",
        "blessing" to "Благословение",
        "blessings" to "Благословения",
        "portal fantasy" to "Портальное фэнтези",
        "cthulhu mythos" to "Мифы Ктулху",
        "cthulhu" to "Мифы Ктулху",
        "lovecraftian" to "Лавкрафт",
        "victorian era" to "Викторианская эпоха",
        "victorian" to "Викторианская эпоха",
        "tarot" to "Таро",
        "tarot club" to "Таро",
        "divination" to "Гадание",
        "secret organization" to "Тайные организации",
        "secret organizations" to "Тайные организации",
        "madness" to "Безумие",
        "insanity" to "Безумие",
        "cults" to "Культы",
        "cult" to "Культы",
        "occult" to "Оккультизм",
        "occultism" to "Оккультизм",
        "esoteric" to "Эзотерика",
        "esotericism" to "Эзотерика",
        "ancient world" to "Древний мир",
        "ancient" to "Древний мир",
        "age of steam" to "Эпоха пара",
        "steam" to "Эпоха пара",
        "slow pace" to "Медленное развитие",
        "slow pacing" to "Медленное развитие",
        "fast pace" to "Быстрое развитие",
        "power struggle" to "Борьба за власть",
        "deep plot" to "Глубокий сюжет",
        "cruel world" to "Жестокий мир",
        "caring protagonist" to "Заботливый главный герой",
        "orphan" to "Сирота",
        "alternate history" to "Альтернативная история",
        "alternate timeline" to "Альтернативная история",
        "witches" to "Ведьмы",
        "witch" to "Ведьмы",
        "money" to "Деньги",
        "investigation" to "Расследование",
        "dark comedy" to "Чёрная комедия",
        "cooking" to "Кулинария",
        "culinary" to "Кулинария",
        "food" to "Кулинария",
        "gourmet" to "Гурман",
        "commerce" to "Торговля",
        "business" to "Торговля",
        "economics" to "Экономика",
        "farming" to "Фермерство",
        "agriculture" to "Фермерство",
        "medicine" to "Медицина",
        "medical" to "Медицина",
        "space travel" to "Космические путешествия",
        "cyborgs" to "Киборги",
        "cyborg" to "Киборги",
        "robots" to "Роботы",
        "robot" to "Роботы",
        "androids" to "Андроиды",
        "android" to "Андроиды",
        "alien invasion" to "Вторжение пришельцев",
        "time manipulation" to "Управление временем",
        "space manipulation" to "Управление пространством",
        "teleportation" to "Телепортация",
        "mind control" to "Контроль разума",
        "telepathy" to "Телепатия",
        "parallel world" to "Параллельный мир",
        "parallel universe" to "Параллельный мир",
        "another world" to "Другой мир",

        // AniList / MangaDex / Anime tags
        "philosophy" to "Философия",
        "philosophical" to "Философия",
        "travel" to "Путешествие",
        "travels" to "Путешествие",
        "journey" to "Путешествие",
        "journeys" to "Путешествие",
        "time skip" to "Пропуск времени",
        "timeskip" to "Пропуск времени",
        "medieval" to "Средневековье",
        "middle ages" to "Средневековье",
        "rural" to "Сельская жизнь",
        "countryside" to "Сельская жизнь",
        "heterosexual" to "Гетеросексуальность",
        "straight" to "Гетеросексуальность",
        "fishing" to "Рыбалка",
        "urban" to "Город",
        "city" to "Город",
        "coastal" to "Побережье",
        "coast" to "Побережье",
        "seaside" to "Побережье",
        "beach" to "Побережье",
        "episodic" to "Эпизоды",
        "episodes" to "Эпизоды",
        "snowscape" to "Снежные пейзажи",
        "snow" to "Снежные пейзажи",
        "primarily adult cast" to "Взрослые персонажи",
        "primarily adult caast" to "Взрослые персонажи",
        "primary adult cast" to "Взрослые персонажи",
        "adult cast" to "Взрослые персонажи",
        "wilderness" to "Дикая природа",
        "iyashikei" to "Успокоение",
        "iyashikey" to "Успокоение",
        "asexual" to "Асексуальность",
        "asexuality" to "Асексуальность",
        "coming of age" to "Взросление",
        "age gap" to "Разница в возрасте",
        "amnesia" to "Амнезия",
        "body horror" to "Боди-хоррор",
        "battle royale" to "Королевская битва",
        "bullying" to "Травля",
        "classic literature" to "Классическая литература",
        "delinquents" to "Хулиганы",
        "delinquent" to "Хулиганы",
        "dissociative identities" to "Раздвоение личности",
        "educational" to "Образовательное",
        "ensemble cast" to "Ансамбль персонажей",
        "environmental" to "Экология",
        "espionage" to "Шпионаж",
        "spies" to "Шпионаж",
        "spy" to "Шпионаж",
        "fairy tale" to "Сказка",
        "found family" to "Обретенная семья",
        "gambling" to "Азартные игры",
        "guns" to "Огнестрельное оружие",
        "firearms" to "Огнестрельное оружие",
        "henshin" to "Трансформация",
        "transformation" to "Трансформация",
        "idol" to "Идолы",
        "idols" to "Идолы",
        "kaiju" to "Кайдзю",
        "mahou shoujo" to "Махо-сёдзё",
        "magical girl" to "Махо-сёдзё",
        "memory manipulation" to "Манипуляции памятью",
        "motorcycles" to "Мотоциклы",
        "ninja" to "Ниндзя",
        "shinobi" to "Ниндзя",
        "no dialogue" to "Без диалогов",
        "noir" to "Нуар",
        "otaku culture" to "Отаку",
        "otaku" to "Отаку",
        "outdoor" to "Активный отдых",
        "outdoors" to "Активный отдых",
        "camping" to "Активный отдых",
        "photography" to "Фотография",
        "rehabilitation" to "Реабилитация",
        "samurai" to "Самураи",
        "school club" to "Школьный клуб",
        "ships" to "Корабли",
        "slapstick" to "Буффонада",
        "slavery" to "Рабство",
        "software development" to "Программирование",
        "teacher" to "Учитель",
        "teachers" to "Учитель",
        "teens love" to "Тинс-лав",
        "trains" to "Поезда",
        "tsundere" to "Цундэрэ",
        "yandere" to "Яндэрэ",
        "kuudere" to "Кудэрэ",
        "dandere" to "Дандэрэ",
        "werewolf" to "Оборотни",
        "werewolves" to "Оборотни",
        "work" to "Работа",
        "workplace" to "Работа",
        "office" to "Работа",
        "yakuza" to "Якудза",
        "chunibyou" to "Тюнибё",
        "crossdressing" to "Кроссдрессинг",
        "tomboy" to "Пацанка",
        "monster girl" to "Девушки-монстры",
        "monster boy" to "Парни-монстры",
        "regression" to "Регрессия",
        "regressor" to "Регрессия",
        "beast taming" to "Приручение зверей",
        "monster tamer" to "Приручение зверей",
        "luck" to "Удача",
        "maids" to "Горничные",
        "maid" to "Горничные",
        "butlers" to "Дворецкие",
        "butler" to "Дворецкие",
        "knights" to "Рыцари",
        "knight" to "Рыцари",
        "monks" to "Монахи",
        "monk" to "Монахи",
        "priests" to "Священники",
        "priest" to "Священники",
        "mercenaries" to "Наемники",
        "mercenary" to "Наемники",
        "pirates" to "Пираты",
        "pirate" to "Пираты",
        "doctors" to "Врачи",
        "doctor" to "Врачи"
    )

    private val EN_TO_RU: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((en, ru) in EN_TO_RU_BASE) {
            map[normalizeKey(en)] = ru
        }
        map
    }

    // Canonical normalization of common Russian synonyms to merge tags effectively
    private val RU_SYNONYMS: Map<String, String> = mapOf(
        "научная фантастика" to "Фантастика",
        "боевик" to "Экшен",
        "экшн" to "Экшен",
        "приключенческий боевик" to "Приключенческий экшен",
        "попаданец" to "Попаданцы",
        "исекай" to "Попаданцы",
        "переселение душ" to "Попаданцы",
        "перемещение в другой мир" to "Попаданцы",
        "видеоигры" to "Игры",
        "компьютерные игры" to "Игры",
        "подземелье" to "Подземелья",
        "секретные организации" to "Тайные организации",
        "тайная организация" to "Тайные организации",
        "секретная организация" to "Тайные организации",
        "темное фэнтези" to "Тёмное фэнтези",
        "монстр" to "Монстры",
        "чудовища" to "Монстры",
        "демон" to "Демоны",
        "вампир" to "Вампиры",
        "призрак" to "Призраки",
        "дух" to "Духи",
        "бог" to "Боги",
        "богиня" to "Богиня",
        "дракон" to "Драконы",
        "артефакт" to "Артефакты",
        "тайна" to "Тайны",
        "заговоры" to "Заговор",
        "культ" to "Культы",
        "эльф" to "Эльфы",
        "гном" to "Гномы",
        "ангел" to "Ангелы",
        "падший ангел" to "Падшие ангелы",
        "дворяне" to "Аристократия",
        "дворянство" to "Аристократия",
        "знать" to "Аристократия",
        "сумасшествие" to "Безумие",
        "волшебники" to "Волшебник",
        "хоррор" to "Ужасы",
        "прокачка" to "Прокачка уровня",
        "создание предметов" to "Крафтинг",
        "градостроительство" to "Развитие королевства",
        "имба" to "Сильный главный герой",
        "гг мужчина" to "ГГ мужчина",
        "гг-мужчина" to "ГГ мужчина",
        "мужской персонаж" to "ГГ мужчина",
        "мужской протагонист" to "ГГ мужчина",
        "главный герой" to "ГГ мужчина",
        "гг женщина" to "ГГ женщина",
        "гг-женщина" to "ГГ женщина",
        "женский персонаж" to "ГГ женщина",
        "женский протагонист" to "ГГ женщина",
        "главная героиня" to "ГГ женщина",
        "насилие" to "Жестокость",

        // Synonyms for tags from AniList / MangaDex
        "путешествия" to "Путешествие",
        "путешествие" to "Путешествие",
        "странствия" to "Путешествие",
        "философия" to "Философия",
        "философский" to "Философия",
        "философское" to "Философия",
        "пропуск времени" to "Пропуск времени",
        "таймскип" to "Пропуск времени",
        "таймскипы" to "Пропуск времени",
        "таймскпи" to "Пропуск времени",
        "тайм скип" to "Пропуск времени",
        "перескок во времени" to "Пропуск времени",
        "прыжок во времени" to "Пропуск времени",
        "скачок во времени" to "Пропуск времени",
        "средневековье" to "Средневековье",
        "средневековый" to "Средневековье",
        "средние века" to "Средневековье",
        "деревня" to "Сельская жизнь",
        "сельская жизнь" to "Сельская жизнь",
        "сельская местность" to "Сельская жизнь",
        "гетеросексуальность" to "Гетеросексуальность",
        "гетеро" to "Гетеросексуальность",
        "рыбалка" to "Рыбалка",
        "рыболовство" to "Рыбалка",
        "город" to "Город",
        "городская жизнь" to "Город",
        "городская среда" to "Город",
        "побережье" to "Побережье",
        "морское побережье" to "Побережье",
        "пляж" to "Побережье",
        "эпизоды" to "Эпизоды",
        "эпизодическое" to "Эпизоды",
        "эпизодический" to "Эпизоды",
        "эпизодическая" to "Эпизоды",
        "фехтование" to "Фехтование",
        "бои на мечах" to "Фехтование",
        "бой на мечах" to "Фехтование",
        "снежные пейзажи" to "Снежные пейзажи",
        "снег" to "Снежные пейзажи",
        "заснеженная местность" to "Снежные пейзажи",
        "преимущественно взрослые персонажи" to "Взрослые персонажи",
        "преимущественно взрослый состав" to "Взрослые персонажи",
        "взрослые персонажи" to "Взрослые персонажи",
        "взрослый состав" to "Взрослые персонажи",
        "дикая природа" to "Дикая природа",
        "дикая местность" to "Дикая природа",
        "дикие земли" to "Дикая природа",
        "иясикей" to "Успокоение",
        "иясикэй" to "Успокоение",
        "ияшикей" to "Успокоение",
        "ияшикэй" to "Успокоение",
        "исцеление души" to "Успокоение",
        "успокоение" to "Успокоение",
        "асексуальность" to "Асексуальность",
        "асексуал" to "Асексуальность"
    )

    private val RU_TO_EN: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()

        for ((enRaw, ru) in EN_TO_RU_BASE) {
            val formattedEn = enRaw.split(' ').joinToString(" ") { word ->
                when (word.lowercase()) {
                    "sci", "fi" -> "Sci-Fi"
                    "op", "pov", "vr", "rpg" -> word.uppercase()
                    else -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }.replace("Sci-Fi Sci-Fi", "Sci-Fi")

            map[normalizeKey(ru)] = formattedEn
        }

        // Add explicit Russian terms and synonyms with normalizeKey (which handles 'ё' -> 'е')
        map[normalizeKey("фантастика")] = "Sci-Fi"
        map[normalizeKey("научная фантастика")] = "Sci-Fi"
        map[normalizeKey("боевик")] = "Action"
        map[normalizeKey("экшен")] = "Action"
        map[normalizeKey("игры")] = "Games"
        map[normalizeKey("видеоигры")] = "Games"
        map[normalizeKey("попаданцы")] = "Isekai"
        map[normalizeKey("попаданец")] = "Isekai"
        map[normalizeKey("переселение душ")] = "Transmigration"
        map[normalizeKey("перемещение в другой мир")] = "Transmigration"
        map[normalizeKey("перерождение")] = "Rebirth"
        map[normalizeKey("реинкарнация")] = "Reincarnation"
        map[normalizeKey("мистика")] = "Mystery"
        map[normalizeKey("детектив")] = "Mystery"
        map[normalizeKey("сверхъестественное")] = "Supernatural"
        map[normalizeKey("темное фэнтези")] = "Dark Fantasy"
        map[normalizeKey("городское фэнтези")] = "Urban Fantasy"
        map[normalizeKey("высокое фэнтези")] = "High Fantasy"
        map[normalizeKey("эпическое фэнтези")] = "Epic Fantasy"
        map[normalizeKey("прогрессивное фэнтези")] = "Progression Fantasy"
        map[normalizeKey("сенен")] = "Shounen"
        map[normalizeKey("сейнен")] = "Seinen"
        map[normalizeKey("седзе")] = "Shoujo"
        map[normalizeKey("дзесей")] = "Josei"
        map[normalizeKey("седзе-ай")] = "Shoujo Ai"
        map[normalizeKey("сенен-ай")] = "Shounen Ai"
        map[normalizeKey("подземелье")] = "Dungeon"
        map[normalizeKey("подземелья")] = "Dungeon"
        map[normalizeKey("прокачка")] = "Level Up"
        map[normalizeKey("прокачка уровня")] = "Level Up"
        map[normalizeKey("дворяне")] = "Nobles"
        map[normalizeKey("дворянство")] = "Nobles"
        map[normalizeKey("аристократия")] = "Nobles"
        map[normalizeKey("тайные организации")] = "Secret Organizations"
        map[normalizeKey("секретные организации")] = "Secret Organizations"
        map[normalizeKey("мифы ктулху")] = "Cthulhu Mythos"
        map[normalizeKey("лавкрафт")] = "Lovecraftian"
        map[normalizeKey("викторианская эпоха")] = "Victorian Era"
        map[normalizeKey("таро")] = "Tarot"
        map[normalizeKey("гадание")] = "Divination"
        map[normalizeKey("эпоха пара")] = "Age of Steam"
        map[normalizeKey("стимпанк")] = "Steampunk"
        map[normalizeKey("киберпанк")] = "Cyberpunk"
        map[normalizeKey("литрпг")] = "LitRPG"
        map[normalizeKey("культивация")] = "Cultivation"
        map[normalizeKey("культиватор")] = "Cultivator"
        map[normalizeKey("безумие")] = "Madness"
        map[normalizeKey("сумасшествие")] = "Madness"
        map[normalizeKey("заговор")] = "Conspiracy"
        map[normalizeKey("заговоры")] = "Conspiracy"
        map[normalizeKey("тайны")] = "Secrets"
        map[normalizeKey("тайна")] = "Secrets"
        map[normalizeKey("культы")] = "Cults"
        map[normalizeKey("культ")] = "Cults"
        map[normalizeKey("оккультизм")] = "Occultism"
        map[normalizeKey("эзотерика")] = "Esoteric"
        map[normalizeKey("древний мир")] = "Ancient World"
        map[normalizeKey("имба")] = "Overpowered Protagonist"
        map[normalizeKey("гг мужчина")] = "Male Lead"
        map[normalizeKey("гг-мужчина")] = "Male Lead"
        map[normalizeKey("мужской персонаж")] = "Male Lead"
        map[normalizeKey("мужской протагонист")] = "Male Lead"
        map[normalizeKey("гг женщина")] = "Female Lead"
        map[normalizeKey("гг-женщина")] = "Female Lead"
        map[normalizeKey("женский персонаж")] = "Female Lead"
        map[normalizeKey("женский протагонист")] = "Female Lead"
        map[normalizeKey("главная героиня")] = "Female Lead"
        map[normalizeKey("градостроительство")] = "Kingdom Building"
        map[normalizeKey("создание предметов")] = "Crafting"
        map[normalizeKey("насилие")] = "Violence"
        map[normalizeKey("чудовища")] = "Monsters"
        map[normalizeKey("жестокость")] = "Violence"

        // AniList tags to English
        map[normalizeKey("философия")] = "Philosophy"
        map[normalizeKey("философский")] = "Philosophy"
        map[normalizeKey("путешествие")] = "Travel"
        map[normalizeKey("путешествия")] = "Travel"
        map[normalizeKey("странствия")] = "Travel"
        map[normalizeKey("пропуск времени")] = "Time Skip"
        map[normalizeKey("таймскип")] = "Time Skip"
        map[normalizeKey("перескок во времени")] = "Time Skip"
        map[normalizeKey("прыжок во времени")] = "Time Skip"
        map[normalizeKey("скачок во времени")] = "Time Skip"
        map[normalizeKey("средневековье")] = "Medieval"
        map[normalizeKey("средневековый")] = "Medieval"
        map[normalizeKey("сельская жизнь")] = "Rural"
        map[normalizeKey("деревня")] = "Rural"
        map[normalizeKey("гетеросексуальность")] = "Heterosexual"
        map[normalizeKey("гетеро")] = "Heterosexual"
        map[normalizeKey("рыбалка")] = "Fishing"
        map[normalizeKey("город")] = "Urban"
        map[normalizeKey("городская жизнь")] = "Urban"
        map[normalizeKey("побережье")] = "Coastal"
        map[normalizeKey("эпизоды")] = "Episodic"
        map[normalizeKey("эпизодическое")] = "Episodic"
        map[normalizeKey("эпизодический")] = "Episodic"
        map[normalizeKey("фехтование")] = "Swordplay"
        map[normalizeKey("бои на мечах")] = "Swordplay"
        map[normalizeKey("снежные пейзажи")] = "Snowscape"
        map[normalizeKey("снег")] = "Snowscape"
        map[normalizeKey("преимущественно взрослые персонажи")] = "Primary Adult Cast"
        map[normalizeKey("взрослые персонажи")] = "Primary Adult Cast"
        map[normalizeKey("взрослый состав")] = "Primary Adult Cast"
        map[normalizeKey("дикая природа")] = "Wilderness"
        map[normalizeKey("дикая местность")] = "Wilderness"
        map[normalizeKey("успокоение")] = "Iyashikei"
        map[normalizeKey("иясикей")] = "Iyashikei"
        map[normalizeKey("иясикэй")] = "Iyashikei"
        map[normalizeKey("ияшикей")] = "Iyashikei"
        map[normalizeKey("ияшикэй")] = "Iyashikei"
        map[normalizeKey("асексуальность")] = "Asexual"
        map[normalizeKey("асексуал")] = "Asexual"
        map
    }

    private fun findEnglishToRussian(key: String): String? {
        EN_TO_RU[key]?.let { return it }

        // Plural fallbacks
        if (key.endsWith("ies") && key.length > 4) {
            EN_TO_RU[key.removeSuffix("ies") + "y"]?.let { return it }
        }
        if (key.endsWith("es") && key.length > 3) {
            EN_TO_RU[key.removeSuffix("es")]?.let { return it }
        }
        if (key.endsWith("s") && key.length > 2) {
            EN_TO_RU[key.removeSuffix("s")]?.let { return it }
        }

        // Without spaces fallback
        val withNoSpaces = key.replace(" ", "")
        EN_TO_RU[withNoSpaces]?.let { return it }

        return null
    }

    private fun findRussianToEnglish(key: String): String? {
        RU_TO_EN[key]?.let { return it }

        if (key.endsWith("ия") && key.length > 3) {
            RU_TO_EN[key.removeSuffix("ия") + "ие"]?.let { return it }
        }
        if (key.endsWith("ие") && key.length > 3) {
            RU_TO_EN[key.removeSuffix("ие") + "ия"]?.let { return it }
        }
        if (key.endsWith("ы") && key.length > 2) {
            RU_TO_EN[key.removeSuffix("ы")]?.let { return it }
        }
        if (key.endsWith("и") && key.length > 2) {
            RU_TO_EN[key.removeSuffix("и")]?.let { return it }
        }

        return null
    }

    private fun findRussianSynonym(key: String): String? {
        RU_SYNONYMS[key]?.let { return it }

        if (key.endsWith("ия") && key.length > 3) {
            RU_SYNONYMS[key.removeSuffix("ия") + "ие"]?.let { return it }
        }
        if (key.endsWith("ы") && key.length > 2) {
            RU_SYNONYMS[key.removeSuffix("ы")]?.let { return it }
        }
        if (key.endsWith("и") && key.length > 2) {
            RU_SYNONYMS[key.removeSuffix("и")]?.let { return it }
        }

        return null
    }

    /**
     * Translates a single genre or tag based on targetLanguage.
     * If targetLanguage is Russian, foreign tags are translated to Russian and synonyms normalized.
     * If targetLanguage is any other language, Russian tags are translated to English.
     * Acronyms (e.g. SCP, RPG, VR) are preserved in uppercase.
     */
    fun translateTag(tag: String, targetLanguage: Language = Language.RUSSIAN): String {
        val cleaned = cleanTag(tag)
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) return ""
        val lowerKey = normalizeKey(cleaned)

        // Acronyms & proper terms like SCP, RPG, VR, VRMMO, etc.
        if (ACRONYMS.contains(lowerKey) || lowerKey.startsWith("scp ") || lowerKey.startsWith("scp-")) {
            return cleaned.uppercase()
        }

        if (targetLanguage == Language.RUSSIAN) {
            // 1. Check English -> Russian dictionary
            findEnglishToRussian(lowerKey)?.let { ruVal ->
                val normRu = normalizeKey(ruVal)
                return findRussianSynonym(normRu) ?: ruVal
            }

            // 2. Check Russian synonym normalization (e.g. "Научная фантастика" -> "Фантастика", "Боевик" -> "Экшен")
            findRussianSynonym(lowerKey)?.let { return it }

            // 3. Fallback: already Russian or untranslated, format with capital first letter
            return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            // 1. Check Russian -> English dictionary
            findRussianToEnglish(lowerKey)?.let { return it }

            // 2. Check if it's already an English known tag
            findEnglishToRussian(lowerKey)?.let {
                return cleaned.split(' ').joinToString(" ") { w ->
                    w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }

            // 3. Fallback
            return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Translates a list of tags/genres to targetLanguage, normalizes, splits compound entries,
     * and deduplicates them.
     */
    fun translateAndNormalizeChips(items: List<String>, targetLanguage: Language = Language.RUSSIAN): List<String> {
        if (items.isEmpty()) return emptyList()

        // 1. Split compound chips like "Action, Adventure" or "Sci-Fi / Fantasy"
        val flattened = items.flatMap { item ->
            item.split(Regex("""[,;/|]+""")).map { cleanTag(it) }
        }.filter { it.isNotBlank() }

        // 2. Translate each chip
        val translated = flattened.map { translateTag(it, targetLanguage) }.filter { it.isNotBlank() }

        // 3. Normalize casing and deduplicate case-insensitively
        return ParserUtils.normalizeChips(translated)
    }
}
