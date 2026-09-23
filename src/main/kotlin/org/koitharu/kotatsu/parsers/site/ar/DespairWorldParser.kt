package org.koitharu.kotatsu.parsers.site.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("DESPAIR_WORLD", "Despair World", "ar")
internal class DespairWorldParser(
    context: MangaLoaderContext,
) : PagedMangaParser(context, MangaParserSource.DESPAIR_WORLD, 24) {

    override val configKeyDomain = ConfigKey.Domain("despair-world.com")

    override val availableSortOrders: Set<SortOrder> =
        EnumSet.of(
            SortOrder.UPDATED,
            SortOrder.ALPHABETICAL,
            SortOrder.POPULARITY,
        )

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override suspend fun getListPage(
        page: Int,
        order: SortOrder,
        filter: MangaListFilter,
    ): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)

            if (!filter.query.isNullOrBlank()) {
                append("/?s=")
                append(filter.query.urlEncoded())
            } else {
                append("/all-manga/")
                if (page > 1) {
                    append("page/")
                    append(page)
                    append("/")
                }
            }
        }

        val doc = webClient.httpGet(url).parseHtml()

        return doc.select("article, .manga-item, .page-item-detail").mapNotNull { el ->
            val a = el.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val href = a.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
            val title = a.text().trim()
            if (title.isEmpty()) return@mapNotNull null

            val cover = el.selectFirst("img")?.attrAsAbsoluteUrlOrNull("src")

            Manga(
                id = generateUid(href),
                title = title,
                coverUrl = cover,
                source = MangaParserSource.DESPAIR_WORLD,
                altTitles = emptySet(),
                authors = emptySet(),
                tags = emptySet(),
                description = null,
                state = null,
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                contentRating = null,
                rating = RATING_UNKNOWN,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

        val title = doc.selectFirst("h1")?.text()?.trim() ?: manga.title
        val description = doc.selectFirst(".summary, .description, .entry-content")
            ?.text()
            ?.trim()
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val tags = doc.select(".genres a, .tags a, .manga-genres a").mapNotNull { a ->
            val tagTitle = a.text().trim()
            if (tagTitle.isEmpty()) return@mapNotNull null
            MangaTag(
                title = tagTitle,
                key = a.attr("href"),
                source = MangaParserSource.DESPAIR_WORLD,
            )
        }.toSet()

        val chapterList = doc.select(".chapter-list li, li.wp-manga-chapter, .wp-manga-chapter").mapNotNull { li ->
            val a = li.selectFirst("a[href*=/chapter-]") ?: return@mapNotNull null
            val href = a.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
            val chapterNumber = href
                .replace(Regex(".*chapter[-_ ]?"), "", RegexOption.IGNORE_CASE)
                .replace(Regex("[^0-9.\\-]"), "")
                .toFloatOrNull() ?: 0f

            MangaChapter(
                id = generateUid(href),
                url = href,
                source = MangaParserSource.DESPAIR_WORLD,
                number = chapterNumber,
                volume = 0,
                uploadDate = 0L,
                title = a.text().trim().ifEmpty { null },
                scanlator = null,
                branch = null,
            )
        }.reversed()

        return manga.copy(
            title = title,
            description = description,
            tags = tags,
            chapters = chapterList,
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

        return doc.select("img[src], .reading-content img, #readerarea img").mapNotNull { img ->
            val url = img.attrAsAbsoluteUrlOrNull("src") ?: return@mapNotNull null
            if (url.contains("logo") || url.contains("avatar")) return@mapNotNull null

            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = MangaParserSource.DESPAIR_WORLD,
            )
        }
    }

    override suspend fun getPageUrl(page: MangaPage): String {
        return page.url
    }
}
