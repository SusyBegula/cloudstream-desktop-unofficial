package com.lagradost.cloudstream3.desktop.ui.screens.browse

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.time.LocalDate

enum class BrowseCategory(val label: String) { MOVIES("Movies"), SHOWS("Shows"), ANIME("Anime") }
val BrowseCategory.headerTitle: String
    get() = when (this) {
        BrowseCategory.MOVIES -> "Movies"
        BrowseCategory.SHOWS -> "TV-Shows"
        BrowseCategory.ANIME -> "Animes"
    }

enum class BrowseSort(val label: String) {
    POPULAR("Most popular"),
    RATING("Highest rated"),
    NEWEST("Newest releases"),
    VOTES("Most rated"),
}

data class BrowseGenre(val id: Int, val label: String)
data class BrowseAgeRating(val id: String, val label: String, val movieCert: String, val tvCert: String)

data class BrowseFilters(
    val category: BrowseCategory = BrowseCategory.MOVIES,
    val animeMovies: Boolean = false,
    val genre: Int? = null,
    val year: Int? = null,
    val minRating: Int = 0,
    val language: String? = null,
    val sort: BrowseSort = BrowseSort.POPULAR,
    val ageRating: String? = null,
) {
    val isMovie: Boolean get() = category == BrowseCategory.MOVIES || (category == BrowseCategory.ANIME && animeMovies)
    val genres: List<BrowseGenre> get() = (if (isMovie) movieGenres else tvGenres)
        .filterNot { category == BrowseCategory.ANIME && it.id == 16 }
    val ageRatings: List<BrowseAgeRating> get() = availableAgeRatings
}

private val commonGenres = listOf(
    BrowseGenre(16, "Animation"),
    BrowseGenre(35, "Comedy"),
    BrowseGenre(80, "Crime"),
    BrowseGenre(99, "Documentary"),
    BrowseGenre(18, "Drama"),
    BrowseGenre(10751, "Family"),
    BrowseGenre(9648, "Mystery"),
    BrowseGenre(37, "Western"),
)
private val movieGenres = (
    commonGenres + listOf(
        BrowseGenre(28, "Action"), BrowseGenre(12, "Adventure"), BrowseGenre(14, "Fantasy"),
        BrowseGenre(36, "History"), BrowseGenre(27, "Horror"), BrowseGenre(10402, "Music"),
        BrowseGenre(10749, "Romance"), BrowseGenre(878, "Science fiction"), BrowseGenre(53, "Thriller"),
        BrowseGenre(10752, "War"), BrowseGenre(10770, "TV movie"),
    )
    ).sortedBy { it.label }
private val tvGenres = (
    commonGenres + listOf(
        BrowseGenre(10759, "Action & adventure"),
        BrowseGenre(10762, "Kids"),
        BrowseGenre(10763, "News"),
        BrowseGenre(10764, "Reality"),
        BrowseGenre(10765, "Sci-fi & fantasy"),
        BrowseGenre(10766, "Soap"),
        BrowseGenre(10767, "Talk"),
        BrowseGenre(10768, "War & politics"),
    )
    ).sortedBy { it.label }

val availableAgeRatings = listOf(
    BrowseAgeRating("all", "All ages (G / TV-G)", "G", "TV-Y|TV-G"),
    BrowseAgeRating("7+", "7+ (PG / TV-PG)", "PG", "TV-Y7|TV-PG"),
    BrowseAgeRating("13+", "13+ (PG-13 / TV-14)", "PG-13", "TV-14"),
    BrowseAgeRating("17+", "17+ (R / TV-MA)", "R", "TV-MA"),
    BrowseAgeRating("18+", "18+ (NC-17 / Adults)", "NC-17", "TV-MA"),
    BrowseAgeRating("NR", "Not rated (NR)", "NR", "NR"),
)

fun findAgeRating(ratingId: String?): BrowseAgeRating? {
    if (ratingId == null) return null
    return availableAgeRatings.firstOrNull {
        it.id.equals(ratingId, ignoreCase = true) ||
            it.movieCert.equals(ratingId, ignoreCase = true) ||
            it.tvCert.split("|").any { c -> c.equals(ratingId, ignoreCase = true) }
    }
}

internal fun resolveCertification(ratingId: String, isMovie: Boolean): String? {
    val tier = availableAgeRatings.firstOrNull { it.id.equals(ratingId, ignoreCase = true) }
    if (tier != null) {
        return if (isMovie) tier.movieCert else tier.tvCert
    }
    return when (ratingId.uppercase()) {
        "G" -> if (isMovie) "G" else "TV-Y|TV-G"
        "PG" -> if (isMovie) "PG" else "TV-Y7|TV-PG"
        "PG-13" -> if (isMovie) "PG-13" else "TV-14"
        "R" -> if (isMovie) "R" else "TV-MA"
        "NC-17" -> if (isMovie) "NC-17" else "TV-MA"
        "TV-Y" -> if (isMovie) "G" else "TV-Y"
        "TV-Y7" -> if (isMovie) "PG" else "TV-Y7"
        "TV-G" -> if (isMovie) "G" else "TV-G"
        "TV-PG" -> if (isMovie) "PG" else "TV-PG"
        "TV-14" -> if (isMovie) "PG-13" else "TV-14"
        "TV-MA" -> if (isMovie) "R" else "TV-MA"
        "NR" -> "NR"
        else -> null
    }
}

data class BrowseTitle(
    val id: Int,
    val name: String,
    val isMovie: Boolean,
    val poster: String?,
    val overview: String,
    val year: Int?,
    val rating: Double?,
) {
    val key: String get() = "${if (isMovie) "movie" else "tv"}/$id"
}

data class BrowsePage(val titles: List<BrowseTitle>, val page: Int, val totalPages: Int, val totalResults: Int)

class BrowseRepository {
    suspend fun discover(filters: BrowseFilters, page: Int): BrowsePage = withContext(Dispatchers.IO) {
        val response = app.get(buildDiscoverUrl(filters, page), timeout = 20)
        check(response.code == 200) { "Catalog request failed (${response.code}). Please try again." }
        parseDiscoverPage(response.text, filters.isMovie)
    }
}

internal fun buildDiscoverUrl(filters: BrowseFilters, page: Int, today: LocalDate = LocalDate.now()): String {
    require(page in 1..500)
    val media = if (filters.isMovie) "movie" else "tv"
    val dateField = if (filters.isMovie) "primary_release_date" else "first_air_date"
    return "https://api.themoviedb.org/3/discover/$media".toHttpUrl().newBuilder().apply {
        // Same public application key used by the existing TMDB metadata provider.
        addQueryParameter("api_key", "e6333b32409e02a4a6eba6fb7ff866bb")
        addQueryParameter("language", "en-US")
        addQueryParameter("include_adult", "false")
        addQueryParameter("page", page.toString())
        addQueryParameter(
            "sort_by",
            when (filters.sort) {
                BrowseSort.POPULAR -> "popularity.desc"
                BrowseSort.RATING -> "vote_average.desc"
                BrowseSort.NEWEST -> "$dateField.desc"
                BrowseSort.VOTES -> "vote_count.desc"
            },
        )
        addQueryParameter("$dateField.lte", today.toString())
        filters.year?.let {
            addQueryParameter(if (filters.isMovie) "primary_release_year" else "first_air_date_year", it.toString())
        }
        val genres = listOfNotNull(if (filters.category == BrowseCategory.ANIME) 16 else null, filters.genre).distinct()
        if (genres.isNotEmpty()) addQueryParameter("with_genres", genres.joinToString(","))
        // Anime is represented by Japanese animation in TMDB's discovery catalog.
        val language = if (filters.category == BrowseCategory.ANIME) "ja" else filters.language
        language?.let { addQueryParameter("with_original_language", it) }
        filters.ageRating?.let { rating ->
            resolveCertification(rating, filters.isMovie)?.let { cert ->
                addQueryParameter("certification_country", "US")
                addQueryParameter("certification", cert)
            }
        }
        if (filters.minRating > 0) addQueryParameter("vote_average.gte", filters.minRating.toString())
        if (filters.sort == BrowseSort.RATING || filters.minRating > 0) addQueryParameter("vote_count.gte", "100")
    }.build().toString()
}

internal fun parseDiscoverPage(body: String, isMovie: Boolean): BrowsePage {
    val json = JSONObject(body)
    val results = json.getJSONArray("results")
    val titles = (0 until results.length()).mapNotNull { index ->
        val entry = results.getJSONObject(index)
        val id = entry.optInt("id", -1)
        val name = entry.optString(if (isMovie) "title" else "name", "").trim()
        if (id <= 0 || name.isBlank()) return@mapNotNull null
        val poster = if (entry.isNull("poster_path")) null else entry.optString("poster_path").takeIf { it.startsWith("/") }
        BrowseTitle(
            id,
            name,
            isMovie,
            poster?.let { "https://image.tmdb.org/t/p/w500$it" },
            if (entry.isNull("overview")) "" else entry.optString("overview", ""),
            entry.optString(if (isMovie) "release_date" else "first_air_date", "").take(4).toIntOrNull(),
            entry.optDouble("vote_average", 0.0).takeIf { it.isFinite() && it > 0 && entry.optInt("vote_count") > 0 },
        )
    }
    return BrowsePage(titles.distinctBy { it.key }, json.getInt("page"), json.getInt("total_pages").coerceIn(0, 500), json.getInt("total_results"))
}
