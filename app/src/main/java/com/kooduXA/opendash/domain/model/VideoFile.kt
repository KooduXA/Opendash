package com.kooduXA.opendash.domain.model

data class VideoFile(
    val filename: String,
    val downloadUrl: String,
    val thumbnailUrl: String, // These cameras usually host a thumbnail at a parallel path
    val size: String,
    val time: String
)