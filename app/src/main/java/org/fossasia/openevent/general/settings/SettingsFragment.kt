package org.fossasia.openevent.general.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceScreen
import android.view.*
import java.util.prefs.PreferenceChangeEvent
import java.util.prefs.PreferenceChangeListener
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.BuildConfig
import org.fossasia.openevent.general.MainActivity
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompat(), PreferenceChangeListener {
    override fun preferenceChange(evt: PreferenceChangeEvent?) {
        preferenceChange(evt)
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.settings, rootKey)
        val prefScreen: PreferenceScreen = preferenceScreen

        val activity = activity as? MainActivity
        activity?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity?.supportActionBar?.title = "Settings"
        setHasOptionsMenu(true)

        //Set Build Version
        prefScreen.findPreference(resources.getString(R.string.key_version)).title = "Version " + BuildConfig.VERSION_NAME
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference?.key == resources.getString(R.string.key_rating)) {
            //Open Orga app in play store
            startOrgaAppPlayStore("org.fossasia.eventyay")
            return true
        }
        if (preference?.key == resources.getString(R.string.key_suggestion)) {
            //Send feedback to test email
            sendToTestEmail()
            return true
        }
        return false
    }

    private fun startOrgaAppPlayStore(packageName: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)))
        } catch (error: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)))
        }
    }

    private fun sendToTestEmail() {
        val emailIntent = Intent(Intent.ACTION_SENDTO)
        emailIntent.data = Uri.parse("mailto:")
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(resources.getString(R.string.testEmailId)))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.emailSubject))
        emailIntent.putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.emailInfo))

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Suggestion"))
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                activity?.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        val activity = activity as? MainActivity
        activity?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        activity?.supportActionBar?.title = "Profile"
        setHasOptionsMenu(false)
        super.onDestroyView()
    }
}