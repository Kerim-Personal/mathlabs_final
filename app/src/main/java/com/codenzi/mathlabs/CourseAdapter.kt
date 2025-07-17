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

/**
 * Kursları ve konularını RecyclerView içinde göstermek için kullanılan Adapter.
 * Bu Adapter, aynı anda sadece bir kursun genişletilmesine izin verir.
 */
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

                // *** YENİ MANTIK BAŞLANGICI ***
                // O an genişletilmiş olan öğenin pozisyonunu bul.
                // `currentList`, ListAdapter tarafından sağlanan güncel listedir.
                val currentlyExpandedPosition = currentList.indexOfFirst { it.isExpanded }

                // Eğer bir öğe açıksa VE bu öğe şu an tıklanandan farklıysa, önce onu kapat.
                if (currentlyExpandedPosition != -1 && currentlyExpandedPosition != clickedPosition) {
                    val previouslyExpandedCourse = getItem(currentlyExpandedPosition)
                    previouslyExpandedCourse.isExpanded = false
                    // Sadece kapanacak olan öğeyi güncelle, tüm listeyi değil.
                    notifyItemChanged(currentlyExpandedPosition)
                }

                // Tıklanan öğenin durumunu tersine çevir (açıksa kapat, kapalıysa aç).
                val clickedCourse = getItem(clickedPosition)
                clickedCourse.isExpanded = !clickedCourse.isExpanded
                // Sadece tıklanan öğeyi güncelle.
                notifyItemChanged(clickedPosition)
                // *** YENİ MANTIK SONU ***
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