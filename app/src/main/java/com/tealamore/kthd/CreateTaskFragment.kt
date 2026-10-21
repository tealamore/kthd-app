package com.tealamore.kthd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tealamore.kthd.databinding.FragmentCreateTaskBinding
import kotlinx.coroutines.launch

/**
 * Screen where the user enters a task label to start a new active task.
 */
class CreateTaskFragment : Fragment() {

    private var _binding: FragmentCreateTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonStartTask.setOnClickListener {
            val label = binding.editTaskLabel.text.toString()
            val isFocused = binding.switchFocusedTask.isChecked
            if (label.isBlank()) {
                binding.layoutTaskLabel.error = "Label is required"
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    val taskId = viewModel.startTask(label, isFocused)
                    TaskForegroundService.startService(requireContext(), label, taskId)
                    Toast.makeText(requireContext(), "Task started!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_CreateTaskFragment_to_ActiveTaskFragment)
                }
            }
        }

        binding.buttonBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
