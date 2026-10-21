package com.tealamore.kthd

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tealamore.kthd.databinding.FragmentConsentBinding
import com.tealamore.kthd.network.ApiClient
import com.tealamore.kthd.network.ConsentPayload
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ConsentFragment : Fragment() {

    private var _binding: FragmentConsentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().finish()
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonAgree.setOnClickListener {
            binding.buttonAgree.isEnabled = false
            binding.buttonDecline.isEnabled = false

            val deviceId = ApiClient.getDeviceId(requireContext())
            val utcTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.format(Date())
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    ApiClient.service.sendConsent(
                        ConsentPayload(
                            deviceId = deviceId,
                            consented = true,
                            consentTimeUtc = utcTime
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ConsentFragment", "Failed to send consent to backend", e)
                }

                ConsentManager.setConsented(requireContext(), true)
                requireActivity().invalidateOptionsMenu()
                findNavController().navigate(R.id.action_ConsentFragment_to_HomeFragment)
            }
        }

        binding.buttonDecline.setOnClickListener {
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
