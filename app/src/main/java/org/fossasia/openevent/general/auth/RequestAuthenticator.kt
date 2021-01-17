package org.fossasia.openevent.general.auth

import okhttp3.Interceptor
import okhttp3.Response
import org.fossasia.openevent.general.HEADER_TYPE
import org.fossasia.openevent.general.JWT_ACCESS_TOKEN
import org.fossasia.openevent.general.JWT_REFRESH_TOKEN
import org.fossasia.openevent.general.data.Preference

class RequestAuthenticator(
    private val authHolder: AuthHolder,
    private val preference: Preference
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val headerType = preference.getString(HEADER_TYPE, JWT_ACCESS_TOKEN)
        val authorization =
            if (headerType == JWT_REFRESH_TOKEN)
                authHolder.getRefreshAuthorization()
            else
                authHolder.getAccessAuthorization()
        val original = chain.request()
        return if (authorization != null) {
            val request = original.newBuilder()
                .header("Authorization", authorization)
                .build()
            chain.proceed(request)
        } else
            chain.proceed(original)
    }
}
