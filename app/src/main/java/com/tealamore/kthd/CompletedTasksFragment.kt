package com.tealamore.kthd

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.databinding.FragmentCompletedTasksBinding
import com.tealamore.kthd.databinding.ItemCompletedTaskBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CompletedTasksFragment : Fragment() {

    private var _binding: FragmentCompletedTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompletedTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CompletedTasksAdapter()
        binding.recyclerCompletedTasks.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completedTasks.collect { tasks ->
                    adapter.submitList(tasks)
                    binding.textEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CompletedTasksAdapter : ListAdapter<Task, CompletedTasksAdapter.ViewHolder>(DiffCallback) {

        class ViewHolder(private val binding: ItemCompletedTaskBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(task: Task) {
                binding.textLabel.text = task.label
                
                val dateFormat = DateFormat.getTimeFormat(binding.root.context)
                binding.textStartTime.text = "Started: ${dateFormat.format(task.startedAt)}"
                binding.textEndTime.text = "Ended: ${dateFormat.format(task.completedAt ?: 0)}"
                
                val durationMillis = (task.completedAt ?: task.startedAt) - task.startedAt
                val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
                binding.textDuration.text = "Duration: ${minutes}m ${seconds}s"
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCompletedTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        object DiffCallback : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem == newItem
        }
    }
}
