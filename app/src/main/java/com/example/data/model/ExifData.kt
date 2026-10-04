package com.example.data.model

data class ExifData(
    val dateTaken: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val imageWidth: Int? = null,
    val imageLength: Int? = null,
    val iso: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val focalLength: String? = null,
    val orientation: Int = 0,
    val software: String? = null,
    val userComment: String? = null,
    val imageDescription: String? = null,
    val artist: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun hasDetails(): Boolean {
        return !dateTaken.isNullOrBlank() ||
                !cameraMake.isNullOrBlank() ||
                !cameraModel.isNullOrBlank() ||
                !userComment.isNullOrBlank() ||
                !imageDescription.isNullOrBlank() ||
                !software.isNullOrBlank() ||
                !artist.isNullOrBlank() ||
                latitude != null ||
                longitude != null
    }
}
