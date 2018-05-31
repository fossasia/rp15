package org.fossasia.openevent.general.auth

import android.arch.lifecycle.Observer
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.squareup.picasso.Picasso

import kotlinx.android.synthetic.main.fragment_profile.view.*
import org.fossasia.openevent.general.CircleTransform
import org.fossasia.openevent.general.MainActivity
import org.fossasia.openevent.general.R
import org.koin.android.architecture.ext.viewModel


class ProfileFragment : Fragment() {
    private val profileFragmentViewModel by viewModel<ProfileFragmentViewModel>()

    private lateinit var rootView: View

    private fun redirectToLogin() {
        startActivity(Intent(activity, LoginActivity::class.java))
    }

    private fun redirectToMain() {
        startActivity(Intent(activity, MainActivity::class.java))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false)

        if (!profileFragmentViewModel.isLoggedIn())
            redirectToLogin()

        rootView.logout.setOnClickListener {
            profileFragmentViewModel.logout()
            redirectToMain()
        }

        profileFragmentViewModel.progress.observe(this, Observer {
            it?.let { showProgressBar(it) }
        })

        profileFragmentViewModel.error.observe(this, Observer {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        })

        profileFragmentViewModel.user.observe(this, Observer {
            it?.let {
                rootView.name.text = "${it.firstName} ${it.lastName}"
                rootView.email.text = it.email

                Picasso.get()
                        .load(it.avatarUrl)
                        .placeholder(R.drawable.ic_person_black_24dp)
                        .transform(CircleTransform())
                        .into(rootView.avatar)
            }
        })

        fetchProfile()

        return rootView
    }

    private fun fetchProfile() {
        if (!profileFragmentViewModel.isLoggedIn())
            return

        rootView.progressBar.isIndeterminate = true
        profileFragmentViewModel.fetchProfile()

    }

    private fun showProgressBar(show: Boolean) {
        rootView.progressBar.isIndeterminate = show
        rootView.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

}