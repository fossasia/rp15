package org.fossasia.openevent.general.auth

import io.reactivex.Completable
import io.reactivex.Single
import org.fossasia.openevent.general.attendees.AttendeeDao
import org.fossasia.openevent.general.auth.change.ChangeRequestToken
import org.fossasia.openevent.general.auth.change.ChangeRequestTokenResponse
import org.fossasia.openevent.general.auth.change.Password
import org.fossasia.openevent.general.auth.forgot.Email
import org.fossasia.openevent.general.auth.forgot.RequestToken
import org.fossasia.openevent.general.auth.forgot.RequestTokenResponse
import org.fossasia.openevent.general.event.EventDao
import org.fossasia.openevent.general.order.OrderDao
import timber.log.Timber

class AuthService(
    private val authApi: AuthApi,
    private val authHolder: AuthHolder,
    private val userDao: UserDao,
    private val orderDao: OrderDao,
    private val attendeeDao: AttendeeDao,
    private val eventDao: EventDao
) {
    fun login(username: String, password: String): Single<LoginResponse> {
        if (username.isEmpty() || password.isEmpty())
            throw IllegalArgumentException("Username or password cannot be empty")

        return authApi.login(Login(username, password))
                .map {
                    authHolder.accessToken = it.accessToken
                    authHolder.refreshToken = it.refreshToken
                    it
                }
    }

    fun checkPasswordValid(email: String, password: String): Single<LoginResponse> =
        authApi.login(Login(email, password))

    fun signUp(signUp: SignUp): Single<User> {
        val email = signUp.email
        val password = signUp.password
        if (email.isNullOrEmpty() || password.isNullOrEmpty())
            throw IllegalArgumentException("Username or password cannot be empty")

        return authApi.signUp(signUp).map {
            userDao.insertUser(it)
            it
        }
    }

    fun updateUser(user: User): Single<User> {
        return authApi.updateUser(user, user.id).map {
            userDao.insertUser(it)
            it
        }
    }

    fun uploadImage(uploadImage: UploadImage): Single<ImageResponse> {
        return authApi.uploadImage(uploadImage)
    }

    fun isLoggedIn() = authHolder.isLoggedIn()

    fun getId() = authHolder.getId()

    fun logout(): Completable {
        return Completable.fromAction {
            authHolder.accessToken = null
            userDao.deleteUser(authHolder.getId())
            orderDao.deleteAllOrders()
            attendeeDao.deleteAllAttendees()
            eventDao.clearFavoriteEvents()
        }
    }

    fun deleteProfile(userId: Long = authHolder.getId()) =
        authApi.deleteAccount(userId)

    fun getProfile(id: Long = authHolder.getId()): Single<User> {
        return userDao.getUser(id)
                .onErrorResumeNext {
                    Timber.d(it, "User not found in Database %d", id)
                    authApi.getProfile(id)
                            .map {
                                userDao.insertUser(it)
                                it
                            }
                }
    }

    fun syncProfile(id: Long = authHolder.getId()): Single<User> {
        return authApi.getProfile(id)
            .map {
                userDao.insertUser(it)
                it
            }
    }

    fun sendResetPasswordEmail(email: String): Single<RequestTokenResponse> {
        val requestToken = RequestToken(Email(email))
        return authApi.requestToken(requestToken)
    }

    fun changePassword(oldPassword: String, newPassword: String): Single<ChangeRequestTokenResponse> {
        val changeRequestToken = ChangeRequestToken(Password(oldPassword, newPassword))
        return authApi.changeRequestToken(changeRequestToken)
    }

    fun checkEmail(email: String): Single<CheckEmailResponse> {
        return authApi.checkEmail(Email(email))
    }

    fun resendVerificationEmail(email: String): Single<EmailVerificationResponse> {
        return authApi.resendVerificationEmail(RequestToken(Email(email)))
    }

    fun verifyEmail(token: String): Single<EmailVerificationResponse> {
        return authApi.verifyEmail(RequestEmailVerification(Token(token)))
    }

    fun resetPassword(requestPasswordReset: RequestPasswordReset): Single<ResetPasswordResponse> {
        return authApi.resetPassword(requestPasswordReset)
    }

    fun refreshToken(): Single<LoginResponse> {
        return authApi.refreshToken()
    }
}
