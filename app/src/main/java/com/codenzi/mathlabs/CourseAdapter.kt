package com.codenzi.mathlabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class CourseAdapter(
    private val onTopicClickListener: (courseTitle: String, topicTitle: String) -> Unit,
    private val onPdfClickListener: (courseTitle: String, topic: Topic) -> Unit
) : ListAdapter<Course, CourseAdapter.CourseViewHolder>(CourseDiffCallback()) {

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val courseHeaderLayout: LinearLayout = itemView.findViewById(R.id.courseHeaderLayout)
        val courseTitleTextView: TextView = itemView.findViewById(R.id.textViewCourseTitle)
        val expandIconImageView: ImageView = itemView.findViewById(R.id.imageViewExpandIcon)
        val topicsContainerLayout: LinearLayout = itemView.findViewById(R.id.topicsContainerLayout)

        init {
            courseHeaderLayout.setOnClickListener {
                UIFeedbackHelper.provideFeedback(it)
                val clickedPosition = bindingAdapterPosition
                if (clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                val currentlyExpandedPosition = currentList.indexOfFirst { it.isExpanded }

                if (currentlyExpandedPosition != -1 && currentlyExpandedPosition != clickedPosition) {
                    val previouslyExpandedCourse = getItem(currentlyExpandedPosition)
                    previouslyExpandedCourse.isExpanded = false
                    notifyItemChanged(currentlyExpandedPosition)
                }

                val clickedCourse = getItem(clickedPosition)
                clickedCourse.isExpanded = !clickedCourse.isExpanded
                notifyItemChanged(clickedPosition)
            }
        }

        fun bind(course: Course) {
            courseTitleTextView.text = course.title
            topicsContainerLayout.removeAllViews()

            if (course.isExpanded) {
                expandIconImageView.setImageResource(R.drawable.ic_expand_less)
                topicsContainerLayout.visibility = View.VISIBLE

                course.topics.forEach { topic ->
                    val topicView = LayoutInflater.from(itemView.context)
                        .inflate(R.layout.item_topic, topicsContainerLayout, false)

                    val topicTextView: TextView = topicView.findViewById(R.id.textViewTopicTitle)
                    val pdfIconImageView: ImageView = topicView.findViewById(R.id.imageViewPdfIcon)
                    topicTextView.text = topic.title

                    if (topic.hasPdf) {
                        pdfIconImageView.visibility = View.VISIBLE
                        topicView.setOnClickListener {
                            // Tıklama olayını doğrudan MainActivity'e bildiriyoruz.
                            // Premium kontrolü burada YAPILMAYACAK.
                            onPdfClickListener(course.title, topic)
                        }
                    } else {
                        pdfIconImageView.visibility = View.GONE
                        topicView.setOnClickListener {
                            onTopicClickListener(course.title, topic.title)
                        }
                    }
                    topicsContainerLayout.addView(topicView)
                }
            } else {
                expandIconImageView.setImageResource(R.drawable.ic_expand_more)
                topicsContainerLayout.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course_with_topics, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class CourseDiffCallback : DiffUtil.ItemCallback<Course>() {
    override fun areItemsTheSame(oldItem: Course, newItem: Course): Boolean {
        return oldItem.title == newItem.title
    }

    override fun areContentsTheSame(oldItem: Course, newItem: Course): Boolean {
        return oldItem == newItem
    }
}