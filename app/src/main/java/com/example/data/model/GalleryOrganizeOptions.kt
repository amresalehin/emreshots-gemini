package com.example.data.model

enum class MediaSortOption(val displayName: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    TITLE_AZ("Name A-Z"),
    TITLE_ZA("Name Z-A"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest")
}

enum class MediaGroupBy(val displayName: String) {
    NONE("None"),
    DATE("By Date"),
    TYPE("Media Type"),
    AI_STATUS("AI Status")
}

enum class GalleryViewMode(val displayName: String) {
    GRID("Grid"),
    FEED("Feed"),
    LIST("List")
}
