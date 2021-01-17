package org.fossasia.openevent.general.auth

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String
)
