package org.fossasia.openevent.general.auth

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy::class)
data class Login(
    val email: String,
    val password: String,
    val rememberMe: Boolean = true,
    val includeInResponse: Boolean = true
)
