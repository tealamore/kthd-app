package com.tealamore.kthd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tealamore.kthd.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

/**
 * The home screen that checks for an active task and allows starting a new one or viewing history.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, you can show notifications now
        } else {
            // Permission denied, explain to the user why you need it
            Toast.makeText(requireContext(), "Notifications are disabled. You won't get task updates.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!checkConsentAndPermission()) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeTask.collect { task ->
                    if (task != null) {
                        TaskForegroundService.startService(requireContext(), task.label, task.id)
                        val navController = findNavController()
                        if (navController.currentDestination?.id == R.id.HomeFragment) {
                            navController.navigate(R.id.action_HomeFragment_to_ActiveTaskFragment)
                        }
                    }
                }
            }
        }

        binding.buttonStartNew.setOnClickListener {
            lifecycleScope.launch {
                if (viewModel.hasActiveTask()) {
                    Toast.makeText(requireContext(), "A task is already active!", Toast.LENGTH_SHORT).show()
                } else {
                    findNavController().navigate(R.id.action_HomeFragment_to_CreateTaskFragment)
                }
            }
        }

        binding.buttonViewCompleted.setOnClickListener {
            findNavController().navigate(R.id.action_HomeFragment_to_CompletedTasksFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        checkConsentAndPermission()
    }

    private fun checkConsentAndPermission(): Boolean {
        if (!ConsentManager.hasConsented(requireContext())) {
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.HomeFragment) {
                navController.navigate(R.id.action_HomeFragment_to_ConsentFragment)
            }
            return false
        }
        checkPermission()
        checkNotificationPermission()
        return true
    }

    private fun checkPermission() {
        if (!PermissionFragment.hasUsageAccess(requireContext())) {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.PermissionFragment) {
                navController.navigate(R.id.action_HomeFragment_to_PermissionFragment)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
