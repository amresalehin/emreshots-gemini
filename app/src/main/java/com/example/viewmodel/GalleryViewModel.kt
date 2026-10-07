package com.amresalehin.emreshots.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emreshots.data.model.CollectionItem
import com.amresalehin.emreshots.data.model.GalleryViewMode
import com.amresalehin.emreshots.data.model.MediaGroupBy
import com.amresalehin.emreshots.data.model.MediaSortOption
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.data.repository.CollectionRepository
import com.amresalehin.emreshots.data.repository.ScreenshotRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

enum class ScreenshotFilter(val displayName: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
    AI_PROCESSED("AI Processed"),
    HAS_LINKS("With Links"),
    FAVORITES("Favorites"),
    REMINDERS("Reminders")
}

data class GalleryUiState(
    val screenshots: List<ScreenshotItem> = emptyList(),
    val collections: List<CollectionItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ScreenshotFilter = ScreenshotFilter.ALL,
    val sortOption: MediaSortOption = MediaSortOption.NEWEST,
    val groupBy: MediaGroupBy = MediaGroupBy.NONE,
    val viewMode: GalleryViewMode = GalleryViewMode.GRID
)

class GalleryViewModel(
    application: Application,
    private val screenshotRepository: ScreenshotRepository = ScreenshotRepository(
        com.amresalehin.emreshots.data.local.AppDatabase.getInstance(application).screenshotDao()
    ),
    private val collectionRepository: CollectionRepository = CollectionRepository(
        com.amresalehin.emreshots.data.local.AppDatabase.getInstance(application).collectionDao()
    )
) : AndroidViewModel(application) {

    val allScreenshots: StateFlow<List<ScreenshotItem>> =
        screenshotRepository.allScreenshots
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<CollectionItem>> =
        collectionRepository.allCollections
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScreenshotFilter.ALL)
    val selectedFilter: StateFlow<ScreenshotFilter> = _selectedFilter.asStateFlow()

    val sortOption = MutableStateFlow(MediaSortOption.NEWEST)
    val groupByOption = MutableStateFlow(MediaGroupBy.NONE)
    val viewMode = MutableStateFlow(GalleryViewMode.GRID)

    val filteredScreenshots: StateFlow<List<ScreenshotItem>> = combine(
        allScreenshots, _searchQuery, _selectedFilter, collections
    ) { screenshots, query, filter, collectionList ->
        var result = screenshots
        if (query.isNotBlank()) {
            result = result.filter { item -> matchesAdvancedQuery(item, parseAdvancedQuery(query), collectionList) }
        }
        when (filter) {
            ScreenshotFilter.ALL -> result
            ScreenshotFilter.PHOTOS -> result.filter { !it.isVideo && !it.tags.contains("Screenshot") }
            ScreenshotFilter.VIDEOS -> result.filter { it.isVideo }
            ScreenshotFilter.SCREENSHOTS -> result.filter { it.tags.contains("Screenshot") || it.title.contains("screenshot", true) }
            ScreenshotFilter.AI_PROCESSED -> result.filter { it.aiProcessed }
            ScreenshotFilter.HAS_LINKS -> result.filter { it.links.isNotEmpty() }
            ScreenshotFilter.FAVORITES -> result.filter { it.isFavorite }
            ScreenshotFilter.REMINDERS -> result.filter { it.reminderTime != null }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedScreenshots: StateFlow<List<ScreenshotItem>> = combine(filteredScreenshots, sortOption) { items, sort ->
        when (sort) {
            MediaSortOption.NEWEST -> items.sortedByDescending { it.addedOn }
            MediaSortOption.OLDEST -> items.sortedBy { it.addedOn }
            MediaSortOption.TITLE_AZ -> items.sortedBy { it.title.lowercase() }
            MediaSortOption.TITLE_ZA -> items.sortedByDescending { it.title.lowercase() }
            MediaSortOption.SIZE_DESC -> items.sortedByDescending { it.fileSize }
            MediaSortOption.SIZE_ASC -> items.sortedBy { it.fileSize }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupedScreenshots: StateFlow<Map<String, List<ScreenshotItem>>> =
        combine(sortedScreenshots, groupByOption) { items, groupBy ->
            when (groupBy) {
                MediaGroupBy.NONE -> mapOf("" to items)
                MediaGroupBy.DATE -> items.groupBy { formatDateGroup(it.addedOn) }
                MediaGroupBy.TYPE -> items.groupBy { item ->
                    when {
                        item.isVideo -> "Videos"
                        item.tags.contains("Screenshot") || item.title.contains("screenshot", true) -> "Screenshots"
                        else -> "Photos"
                    }
                }
                MediaGroupBy.AI_STATUS -> items.groupBy {
                    if (it.aiProcessed) "✦ AI Indexed & Tagged" else "Pending AI Analysis"
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val uiState: StateFlow<GalleryUiState> = combine(
        sortedScreenshots, collections, _searchQuery, _selectedFilter, sortOption, groupByOption, viewMode
    ) { shots, cols, query, filter, sort, group, mode ->
        GalleryUiState(shots, cols, query, filter, sort, group, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: ScreenshotFilter) {
        _selectedFilter.value = if (_selectedFilter.value == filter) ScreenshotFilter.ALL else filter
    }

    fun setSortOption(option: MediaSortOption) {
        sortOption.value = option
    }

    fun setGroupByOption(option: MediaGroupBy) {
        groupByOption.value = option
    }

    fun setViewMode(mode: GalleryViewMode) {
        viewMode.value = mode
    }

    private data class SearchClause(val field: String?, val value: String, val negated: Boolean)

    private fun parseAdvancedQuery(query: String): List<SearchClause> {
        val pattern = Pattern.compile("""(-)?([A-Za-z]+):(?:"([^"]+)"|(\S+))|(-)?(?:"([^"]+)"|(\S+))""")
        val matcher = pattern.matcher(query)
        val clauses = mutableListOf<SearchClause>()
        while (matcher.find()) {
            val negated = matcher.group(1) == "-" || matcher.group(5) == "-"
            val field = matcher.group(2)?.lowercase()
            val value = matcher.group(3) ?: matcher.group(4) ?: matcher.group(6) ?: matcher.group(7)
            if (!value.isNullOrBlank()) clauses += SearchClause(field, value.trim(), negated)
        }
        return clauses
    }

    private fun matchesAdvancedQuery(
        item: ScreenshotItem,
        clauses: List<SearchClause>,
        collectionList: List<CollectionItem>
    ): Boolean {
        val haystack = listOf(
            item.title, item.description, item.tags.joinToString(" "),
            item.links.joinToString(" "), item.notes.orEmpty(), item.ocrText.orEmpty(), item.filePath
        ).joinToString(" ").lowercase()
        return clauses.all { clause ->
            val value = clause.value.lowercase()
            val matched = when (clause.field) {
                null -> haystack.contains(value)
                "title" -> item.title.lowercase().contains(value)
                "description", "desc" -> item.description.lowercase().contains(value)
                "tag", "tags" -> item.tags.any { it.lowercase().contains(value) }
                "ocr", "text" -> item.ocrText?.lowercase()?.contains(value) == true
                "link", "url", "domain" -> item.links.any { it.lowercase().contains(value) }
                "note", "notes" -> item.notes?.lowercase()?.contains(value) == true
                "type" -> when (value) {
                    "video" -> item.isVideo
                    "photo" -> !item.isVideo && !item.tags.any { it.equals("Screenshot", true) }
                    "screenshot" -> !item.isVideo && (
                        item.tags.any { it.equals("Screenshot", true) } ||
                            item.title.contains("screenshot", true)
                        )
                    else -> haystack.contains(value)
                }
                "favorite", "fav" -> item.isFavorite == (value == "true" || value == "yes" || value == "1")
                "ai" -> item.aiProcessed == (value == "true" || value == "yes" || value == "1" || value == "indexed")
                "collection", "in" -> item.collectionIds.mapNotNull { id ->
                    collectionList.find { it.id == id }?.name?.lowercase()
                }.any { it.contains(value) }
                "before" -> parseDateStart(value)?.let { item.addedOn < it } ?: false
                "after" -> parseDateEnd(value)?.let { item.addedOn > it } ?: false
                else -> haystack.contains(value)
            }
            if (clause.negated) !matched else matched
        }
    }

    private fun parseDateStart(value: String): Long? =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)?.time
        }.getOrNull()

    private fun parseDateEnd(value: String): Long? =
        parseDateStart(value)?.plus(24 * 60 * 60 * 1000L - 1)

    private fun formatDateGroup(timestamp: Long): String {
        val now = Calendar.getInstance()
        val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val diffDays = (now.timeInMillis - timestamp) / (1000 * 60 * 60 * 24)
        return when {
            diffDays <= 0L &&
                now.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) -> "Today"
            diffDays <= 1L -> "Yesterday"
            diffDays < 7L -> "This Week"
            diffDays < 30L -> "This Month"
            else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
