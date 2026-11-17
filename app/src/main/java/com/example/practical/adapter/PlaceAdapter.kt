package com.example.practical.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.practical.R
import com.example.practical.model.Place
import com.google.android.material.button.MaterialButton
import java.util.Locale

class PlaceAdapter(
    private val onDirectionsClick: (Place) -> Unit,
    private val onCallClick: (Place) -> Unit,
    private val onFavoriteClick: (Place) -> Unit
) : ListAdapter<Place, PlaceAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCategoryIcon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
        private val tvPlaceName: TextView = itemView.findViewById(R.id.tvPlaceName)
        private val tvPlaceCategory: TextView = itemView.findViewById(R.id.tvPlaceCategory)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnDirections: MaterialButton = itemView.findViewById(R.id.btnDirections)

        fun bind(place: Place) {
            tvPlaceName.text = place.name
            tvDistance.text = "${place.getFormattedDistance()} away"
            tvPlaceCategory.text = place.amenity.uppercase(Locale.getDefault())

            // Set icon by amenity category
            val iconRes = when (place.amenity.lowercase(Locale.getDefault())) {
                "hospital" -> R.drawable.ic_hospital
                "pharmacy" -> R.drawable.ic_pharmacy
                "police" -> R.drawable.ic_police
                else -> R.drawable.ic_hospital
            }
            ivCategoryIcon.setImageResource(iconRes)

            // Set favorite heart state
            if (place.isFavourite) {
                btnFavorite.setImageResource(R.drawable.ic_favorite_filled)
            } else {
                btnFavorite.setImageResource(R.drawable.ic_favorite_border)
            }

            // Show Call button ONLY when phone tag exists
            if (!place.phone.isNullOrBlank()) {
                btnCall.visibility = View.VISIBLE
                btnCall.setOnClickListener { onCallClick(place) }
            } else {
                btnCall.visibility = View.GONE
            }

            // Click listeners
            btnDirections.setOnClickListener { onDirectionsClick(place) }
            btnFavorite.setOnClickListener { onFavoriteClick(place) }
        }
    }

    class PlaceDiffCallback : DiffUtil.ItemCallback<Place>() {
        override fun areItemsTheSame(oldItem: Place, newItem: Place): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Place, newItem: Place): Boolean {
            return oldItem == newItem
        }
    }
}
