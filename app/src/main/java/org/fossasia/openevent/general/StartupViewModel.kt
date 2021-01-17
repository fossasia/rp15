package org.fossasia.openevent.general

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.fossasia.openevent.general.auth.AuthHolder
import org.fossasia.openevent.general.auth.AuthService
import org.fossasia.openevent.general.auth.RequestPasswordReset
import org.fossasia.openevent.general.auth.forgot.PasswordReset
import org.fossasia.openevent.general.common.SingleLiveEvent
import org.fossasia.openevent.general.data.Preference
import org.fossasia.openevent.general.data.Resource
import org.fossasia.openevent.general.event.NEW_NOTIFICATIONS
import org.fossasia.openevent.general.notification.NotificationService
import org.fossasia.openevent.general.settings.SettingsService
import org.fossasia.openevent.general.utils.HttpErrors
import org.fossasia.openevent.general.utils.extensions.withDefaultSchedulers
import retrofit2.HttpException
import timber.log.Timber

const val HEADER_TYPE = "headerType"
const val JWT_ACCESS_TOKEN = "jwtAccessToken"
const val JWT_REFRESH_TOKEN = "jwtRefreshToken"

class StartupViewModel(
    private val preference: Preference,
    private val resource: Resource,
    private val authHolder: AuthHolder,
    private val authService: AuthService,
    private val notificationService: NotificationService,
    private val settingsService: SettingsService
) : ViewModel() {
    private val compositeDisposable = CompositeDisposable()
    val mutableNewNotifications = MutableLiveData<Boolean>()
    val newNotifications: LiveData<Boolean> = mutableNewNotifications
    private val mutableDialogProgress = MutableLiveData<Boolean>()
    val dialogProgress: LiveData<Boolean> = mutableDialogProgress
    private val mutableIsRefresh = MutableLiveData<Boolean>()
    val isRefresh: LiveData<Boolean> = mutableIsRefresh
    private val mutableResetPasswordEmail = MutableLiveData<String>()
    val resetPasswordEmail: LiveData<String> = mutableResetPasswordEmail
    private val mutableMessage = SingleLiveEvent<String>()
    val message: SingleLiveEvent<String> = mutableMessage

    fun isLoggedIn() = authHolder.isLoggedIn()

    fun getId() = authHolder.getId()

    fun syncNotifications() {
        if (!isLoggedIn())
            return
        compositeDisposable += notificationService.syncNotifications(getId())
            .withDefaultSchedulers()
            .subscribe({ list ->
                list?.forEach {
                    if (!it.isRead) {
                        preference.putBoolean(NEW_NOTIFICATIONS, true)
                        mutableNewNotifications.value = true
                    }
                }
            }, {
                if (it is HttpException) {
                    if (authHolder.isLoggedIn() && it.code() == HttpErrors.UNAUTHORIZED) {
                        logoutAndRefresh()
                    }
                }
                Timber.e(it, "Error fetching notifications")
            })
    }

    private fun logoutAndRefresh() {
        compositeDisposable += authService.logout()
            .withDefaultSchedulers()
            .subscribe({
                mutableIsRefresh.value = true
            }, {
                Timber.e(it, "Error while logout")
                mutableMessage.value = resource.getString(R.string.error)
            })
    }

    fun checkAndReset(token: String, newPassword: String) {
        val resetRequest = RequestPasswordReset(PasswordReset(token, newPassword))
        if (authHolder.isLoggedIn()) {
            compositeDisposable += authService.logout()
                .withDefaultSchedulers()
                .doOnSubscribe {
                    mutableDialogProgress.value = true
                }.subscribe {
                    resetPassword(resetRequest)
                }
        } else
            resetPassword(resetRequest)
    }

    private fun resetPassword(resetRequest: RequestPasswordReset) {
        compositeDisposable += authService.resetPassword(resetRequest)
            .withDefaultSchedulers()
            .doOnSubscribe {
                mutableDialogProgress.value = true
            }.doFinally {
                mutableDialogProgress.value = false
            }.subscribe({
                Timber.e(it.toString())
                mutableMessage.value = resource.getString(R.string.reset_password_message)
                mutableResetPasswordEmail.value = it.email
            }, {
                Timber.e(it, "Failed to reset password")
            })
    }

    fun fetchSettings() {
        compositeDisposable += settingsService.fetchSettings()
            .withDefaultSchedulers()
            .subscribe({
                Timber.d("Settings fetched successfully")
            }, {
                Timber.e(it, "Error in fetching settings form API")
            })
    }

    fun checkAccessToken() {
        if (!authHolder.isLoggedIn() || authHolder.isTokenValid())
            return
        preference.putString(HEADER_TYPE, JWT_REFRESH_TOKEN)
        compositeDisposable += authService.refreshToken()
            .withDefaultSchedulers()
            .doFinally {
                preference.putString(HEADER_TYPE, JWT_ACCESS_TOKEN)
            }.subscribe({
                authHolder.accessToken = it.accessToken
            }, {
                if (it is HttpException) {
                    if (it.code() == HttpErrors.UNAUTHORIZED)
                        logoutAndRefresh()
                }
                Timber.e(it, "Error refreshing token, logged out.")
            })
    }
}
