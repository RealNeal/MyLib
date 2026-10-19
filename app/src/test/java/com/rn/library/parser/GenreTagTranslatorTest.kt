package com.rn.library.parser

import com.rn.library.ui.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreTagTranslatorTest {

    @Test
    fun testEnglishToRussianTranslation() {
        assertEquals("Экшен", GenreTagTranslator.translateTag("Action", Language.RUSSIAN))
        assertEquals("Приключения", GenreTagTranslator.translateTag("Adventure", Language.RUSSIAN))
        assertEquals("Фантастика", GenreTagTranslator.translateTag("Sci-Fi", Language.RUSSIAN))
        assertEquals("Фантастика", GenreTagTranslator.translateTag("Science Fiction", Language.RUSSIAN))
        assertEquals("Попаданцы", GenreTagTranslator.translateTag("Transmigration", Language.RUSSIAN))
        assertEquals("Попаданцы", GenreTagTranslator.translateTag("Isekai", Language.RUSSIAN))
        assertEquals("Стимпанк", GenreTagTranslator.translateTag("Steampunk", Language.RUSSIAN))
        assertEquals("Мифы Ктулху", GenreTagTranslator.translateTag("Cthulhu Mythos", Language.RUSSIAN))
        assertEquals("Тайные организации", GenreTagTranslator.translateTag("Secret Organizations", Language.RUSSIAN))
        assertEquals("Викторианская эпоха", GenreTagTranslator.translateTag("Victorian Era", Language.RUSSIAN))
        assertEquals("Таро", GenreTagTranslator.translateTag("Tarot", Language.RUSSIAN))
        assertEquals("Гадание", GenreTagTranslator.translateTag("Divination", Language.RUSSIAN))
        assertEquals("Безумие", GenreTagTranslator.translateTag("Madness", Language.RUSSIAN))
        assertEquals("Оккультизм", GenreTagTranslator.translateTag("Occult", Language.RUSSIAN))
        assertEquals("Эзотерика", GenreTagTranslator.translateTag("Esoteric", Language.RUSSIAN))
        assertEquals("Культы", GenreTagTranslator.translateTag("Cults", Language.RUSSIAN))
        assertEquals("ЛитРПГ", GenreTagTranslator.translateTag("LitRPG", Language.RUSSIAN))
        assertEquals("Экшен", GenreTagTranslator.translateTag("Боевик", Language.RUSSIAN))
    }

    @Test
    fun testHashesAndPunctuationStripped() {
        assertEquals("Экшен", GenreTagTranslator.translateTag("#Action", Language.RUSSIAN))
        assertEquals("Фантастика", GenreTagTranslator.translateTag("#Sci-Fi,", Language.RUSSIAN))
        assertEquals("Попаданцы", GenreTagTranslator.translateTag("@transmigration;", Language.RUSSIAN))
        assertEquals("SCP", GenreTagTranslator.translateTag("#scp", Language.RUSSIAN))
        assertEquals("RPG", GenreTagTranslator.translateTag("#rpg", Language.RUSSIAN))
    }

    @Test
    fun testCompoundChipsSplittingAndMergingRussian() {
        val rawList = listOf(
            "Action, Adventure",
            "Sci-Fi",
            "Фантастика",
            "Попаданцы",
            "Transmigration",
            "Steampunk",
            "Стимпанк",
            "#Secret Organizations",
            "Секретные организации",
            "Cthulhu Mythos",
            "Таро",
            "Divination",
            "Mystery",
            "Мистика"
        )

        val merged = GenreTagTranslator.translateAndNormalizeChips(rawList, Language.RUSSIAN)

        // Verifications:
        // Sci-Fi and Фантастика must merge into a single "Фантастика"
        assertTrue("Must contain 'Фантастика'", merged.contains("Фантастика"))
        assertEquals(1, merged.count { it.equals("Фантастика", ignoreCase = true) })

        // Transmigration and Попаданцы must merge into a single "Попаданцы"
        assertTrue("Must contain 'Попаданцы'", merged.contains("Попаданцы"))
        assertEquals(1, merged.count { it.equals("Попаданцы", ignoreCase = true) })

        // Steampunk and Стимпанк must merge into a single "Стимпанк"
        assertTrue("Must contain 'Стимпанк'", merged.contains("Стимпанк"))
        assertEquals(1, merged.count { it.equals("Стимпанк", ignoreCase = true) })

        // Secret Organizations and Секретные организации must merge into "Тайные организации"
        assertTrue("Must contain 'Тайные организации'", merged.contains("Тайные организации"))
        assertEquals(1, merged.count { it.equals("Тайные организации", ignoreCase = true) })

        // Action, Adventure compound must be split into Экшен and Приключения
        assertTrue("Must contain 'Экшен'", merged.contains("Экшен"))
        assertTrue("Must contain 'Приключения'", merged.contains("Приключения"))

        // Tarot and Divination
        assertTrue("Must contain 'Таро'", merged.contains("Таро"))
        assertTrue("Must contain 'Гадание'", merged.contains("Гадание"))
        assertTrue("Must contain 'Мифы Ктулху'", merged.contains("Мифы Ктулху"))
    }

    @Test
    fun testRussianToEnglishTranslation() {
        assertEquals("Sci-Fi", GenreTagTranslator.translateTag("Фантастика", Language.ENGLISH))
        assertEquals("Sci-Fi", GenreTagTranslator.translateTag("Научная фантастика", Language.ENGLISH))
        assertEquals("Action", GenreTagTranslator.translateTag("Боевик", Language.ENGLISH))
        assertEquals("Action", GenreTagTranslator.translateTag("Экшен", Language.ENGLISH))
        assertEquals("Isekai", GenreTagTranslator.translateTag("Попаданцы", Language.ENGLISH))
        assertEquals("Isekai", GenreTagTranslator.translateTag("Попаданец", Language.ENGLISH))
        assertEquals("Steampunk", GenreTagTranslator.translateTag("Стимпанк", Language.ENGLISH))
        assertEquals("Tarot", GenreTagTranslator.translateTag("Таро", Language.ENGLISH))
        assertEquals("Divination", GenreTagTranslator.translateTag("Гадание", Language.ENGLISH))
        assertEquals("Secret Organizations", GenreTagTranslator.translateTag("Тайные организации", Language.ENGLISH))
        assertEquals("Secret Organizations", GenreTagTranslator.translateTag("Секретные организации", Language.ENGLISH))
        assertEquals("Dark Fantasy", GenreTagTranslator.translateTag("Тёмное фэнтези", Language.ENGLISH))
        assertEquals("Dark Fantasy", GenreTagTranslator.translateTag("Темное фэнтези", Language.ENGLISH))
        assertEquals("Shounen", GenreTagTranslator.translateTag("Сёнэн", Language.ENGLISH))
        assertEquals("Shounen", GenreTagTranslator.translateTag("Сенен", Language.ENGLISH))
    }

    @Test
    fun testCompoundChipsSplittingAndMergingEnglish() {
        val rawList = listOf(
            "Боевик, Приключения",
            "Фантастика",
            "Sci-Fi",
            "Попаданцы",
            "Isekai",
            "Секретные организации",
            "Тайные организации",
            "Таро",
            "Tarot"
        )

        val merged = GenreTagTranslator.translateAndNormalizeChips(rawList, Language.ENGLISH)

        assertTrue(merged.contains("Action"))
        assertTrue(merged.contains("Adventure"))
        assertTrue(merged.contains("Sci-Fi"))
        assertEquals(1, merged.count { it.equals("Sci-Fi", ignoreCase = true) })
        assertTrue(merged.contains("Isekai"))
        assertEquals(1, merged.count { it.equals("Isekai", ignoreCase = true) })
        assertTrue(merged.contains("Secret Organizations"))
        assertEquals(1, merged.count { it.equals("Secret Organizations", ignoreCase = true) })
        assertTrue(merged.contains("Tarot"))
        assertEquals(1, merged.count { it.equals("Tarot", ignoreCase = true) })
    }

    @Test
    fun testUserFrierenTagsTranslationAndMerging() {
        val userTags = listOf(
            "Философия", "Путешествие", "Time Skip", "Medieval", "rural", "heterosexual",
            "Fishing", "Urban", "Coastal", "Episodic", "Swordplay", "Snowscape",
            "Travel", "Philosophy", "Primary Adult Cast", "Iyashikei"
        )

        val mergedRu = GenreTagTranslator.translateAndNormalizeChips(userTags, Language.RUSSIAN)

        // Philosophy & Философия must merge into single "Философия"
        assertTrue(mergedRu.contains("Философия"))
        assertEquals(1, mergedRu.count { it.equals("Философия", ignoreCase = true) })

        // Travel & Путешествие must merge into single "Путешествие"
        assertTrue(mergedRu.contains("Путешествие"))
        assertEquals(1, mergedRu.count { it.equals("Путешествие", ignoreCase = true) })

        assertTrue(mergedRu.contains("Пропуск времени"))
        assertTrue(mergedRu.contains("Средневековье"))
        assertTrue(mergedRu.contains("Сельская жизнь"))
        assertTrue(mergedRu.contains("Гетеросексуальность"))
        assertTrue(mergedRu.contains("Рыбалка"))
        assertTrue(mergedRu.contains("Город"))
        assertTrue(mergedRu.contains("Побережье"))
        assertTrue(mergedRu.contains("Эпизоды"))
        assertTrue(mergedRu.contains("Фехтование"))
        assertTrue(mergedRu.contains("Снежные пейзажи"))
        assertTrue(mergedRu.contains("Преимущественно взрослые персонажи"))
        assertTrue(mergedRu.contains("Иясикей"))

        // Exactly 14 distinct merged tags (since 16 tags had 2 pairs merging: Philosophy/Философия and Travel/Путешествие)
        assertEquals(14, mergedRu.size)

        // Target language English
        val mergedEn = GenreTagTranslator.translateAndNormalizeChips(userTags, Language.ENGLISH)
        assertTrue(mergedEn.contains("Philosophy"))
        assertEquals(1, mergedEn.count { it.equals("Philosophy", ignoreCase = true) })
        assertTrue(mergedEn.contains("Travel"))
        assertEquals(1, mergedEn.count { it.equals("Travel", ignoreCase = true) })
        assertTrue(mergedEn.contains("Time Skip"))
        assertTrue(mergedEn.contains("Medieval"))
        assertTrue(mergedEn.contains("Rural"))
        assertTrue(mergedEn.contains("Heterosexual"))
        assertTrue(mergedEn.contains("Fishing"))
        assertTrue(mergedEn.contains("Urban"))
        assertTrue(mergedEn.contains("Coastal"))
        assertTrue(mergedEn.contains("Episodic"))
        assertTrue(mergedEn.contains("Swordplay"))
        assertTrue(mergedEn.contains("Snowscape"))
        assertTrue(mergedEn.contains("Primary Adult Cast"))
        assertTrue(mergedEn.contains("Iyashikei"))
        assertEquals(14, mergedEn.size)
    }

    @Test
    fun testUserRequestedNewTagUnifications() {
        // Adult cast
        assertEquals("Преимущественно взрослые персонажи", GenreTagTranslator.translateTag("Primarily Adult Cast", Language.RUSSIAN))
        assertEquals("Преимущественно взрослые персонажи", GenreTagTranslator.translateTag("Primarily Adult Caast", Language.RUSSIAN))
        assertEquals("Преимущественно взрослые персонажи", GenreTagTranslator.translateTag("Взрослые персонажи", Language.RUSSIAN))

        // Wilderness
        assertEquals("Дикая природа", GenreTagTranslator.translateTag("Wilderness", Language.RUSSIAN))
        assertEquals("Дикая природа", GenreTagTranslator.translateTag("дикая местность", Language.RUSSIAN))

        // Time skip unifications
        assertEquals("Пропуск времени", GenreTagTranslator.translateTag("Перескок во времени", Language.RUSSIAN))
        assertEquals("Пропуск времени", GenreTagTranslator.translateTag("таймскип", Language.RUSSIAN))
        assertEquals("Пропуск времени", GenreTagTranslator.translateTag("таймскпи", Language.RUSSIAN))
        assertEquals("Пропуск времени", GenreTagTranslator.translateTag("Time Skip", Language.RUSSIAN))

        // Iyashikei unifications
        assertEquals("Иясикей", GenreTagTranslator.translateTag("Иясикэй", Language.RUSSIAN))
        assertEquals("Иясикей", GenreTagTranslator.translateTag("Ияшикэй", Language.RUSSIAN))
        assertEquals("Иясикей", GenreTagTranslator.translateTag("Ияшикей", Language.RUSSIAN))
        assertEquals("Иясикей", GenreTagTranslator.translateTag("Iyashikei", Language.RUSSIAN))

        // Episodic unifications
        assertEquals("Эпизоды", GenreTagTranslator.translateTag("Эпизодическое", Language.RUSSIAN))
        assertEquals("Эпизоды", GenreTagTranslator.translateTag("Эпизодический", Language.RUSSIAN))
        assertEquals("Эпизоды", GenreTagTranslator.translateTag("Episodic", Language.RUSSIAN))

        // Asexual unifications
        assertEquals("Асексуальность", GenreTagTranslator.translateTag("Асексуал", Language.RUSSIAN))
        assertEquals("Асексуальность", GenreTagTranslator.translateTag("Асексуальность", Language.RUSSIAN))
        assertEquals("Асексуальность", GenreTagTranslator.translateTag("Asexual", Language.RUSSIAN))

        // Protagonist
        assertEquals("ГГ женщина", GenreTagTranslator.translateTag("Женский протагонист", Language.RUSSIAN))
        assertEquals("ГГ женщина", GenreTagTranslator.translateTag("Female Protagonist", Language.RUSSIAN))
        assertEquals("ГГ женщина", GenreTagTranslator.translateTag("Female Lead", Language.RUSSIAN))
        assertEquals("ГГ мужчина", GenreTagTranslator.translateTag("Мужской протагонист", Language.RUSSIAN))
        assertEquals("ГГ мужчина", GenreTagTranslator.translateTag("Male Protagonist", Language.RUSSIAN))
    }
}
