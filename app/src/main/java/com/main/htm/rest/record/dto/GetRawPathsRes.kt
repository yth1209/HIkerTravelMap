package com.main.htm.rest.record.dto

import com.main.htm.common.dto.Response

class GetRawPathsRes(
    val paths: List<List<List<Double>>>
): Response() {
}