package com.tealamore.kthd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.databinding.FragmentActiveTaskBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class ActiveTaskFragment : Fragment() {

    private var _binding: FragmentActiveTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by viewModels()

    private var currentTask: Task? = null
    private var timerJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeTask.collect { task ->
                    currentTask = task
                    if (task != null) {
                        binding.textActiveTaskLabel.text = task.label
                        binding.textActiveTaskDescription.text = task.description ?: ""
                        binding.chipFocusedTask.visibility = if (task.focusedTask) View.VISIBLE else View.GONE
                        startTimer(task.startedAt)
                    } else {
                        timerJob?.cancel()
                        // If task is null, it might have been completed, go back to start
                        findNavController().navigateUp()
                    }
                }
            }
        }

        binding.buttonCompleteTask.setOnClickListener {
            currentTask?.let {
                viewModel.completeTask(it)
                TaskForegroundService.stopService(requireContext())
            }
        }
    }

    private fun startTimer(startedAt: Long) {
        timerJob?.cancel()
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val elapsedMillis = System.currentTimeMillis() - startedAt
                    val totalSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
                    val seconds = totalSeconds % 60
                    val minutes = (totalSeconds / 60) % 60
                    val hours = totalSeconds / 3600
                    binding.textActiveTaskTimer.text = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                    delay(1000L.milliseconds)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
        _binding = null
    }
}
