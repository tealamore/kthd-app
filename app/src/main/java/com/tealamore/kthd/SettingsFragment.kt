package com.tealamore.kthd

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tealamore.kthd.databinding.FragmentSettingsBinding
import com.tealamore.kthd.network.ApiClient
import com.tealamore.kthd.network.DeleteParticipantPayload
import com.tealamore.kthd.network.RequestSyncManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val androidId = ApiClient.getDeviceId(requireContext())
        binding.textAndroidId.text = getString(R.string.android_id_label, androidId)

        binding.buttonDeleteParticipantData.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_data_dialog_title)
                .setMessage(R.string.delete_data_dialog_message)
                .setPositiveButton(R.string.delete_data_confirm_button) { dialog, _ ->
                    dialog.dismiss()
                    sendDataDeletionRequest(androidId)
                }
                .setNegativeButton(R.string.delete_data_cancel_button) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.getAllTasks().collect { tasks ->
                        binding.textTasksDump.text = tasks.joinToString("\n\n")
                    }
                }
                launch {
                    viewModel.getAllPayloads().collect { payloads ->
                        binding.textPayloadsDump.text = payloads.joinToString("\n\n")
                    }
                }
            }
        }
    }

    private fun sendDataDeletionRequest(deviceId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                RequestSyncManager.enqueueDeleteParticipant(requireContext(), DeleteParticipantPayload(deviceId = deviceId))
                Toast.makeText(requireContext(), R.string.delete_data_submitted_toast, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to enqueue data deletion request", e)
                Toast.makeText(requireContext(), R.string.delete_data_failed_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
