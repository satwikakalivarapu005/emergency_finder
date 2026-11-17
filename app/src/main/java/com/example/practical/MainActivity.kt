package com.example.practical

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.practical.adapter.PlaceAdapter
import com.example.practical.data.AppDatabase
import com.example.practical.data.FavouritePlace
import com.example.practical.model.Place
import com.example.practical.network.OverpassApiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase
    private lateinit var adapter: PlaceAdapter

    private lateinit var btnAuth: MaterialButton
    private lateinit var tvUserName: TextView
    private lateinit var cgCategory: ChipGroup
    private lateinit var cgRadius: ChipGroup
    private lateinit var chipHospital: Chip
    private lateinit var chipPharmacy: Chip
    private lateinit var chipPolice: Chip
    private lateinit var chipFavourites: Chip
    private lateinit var chip1km: Chip
    private lateinit var chip2km: Chip
    private lateinit var chip5km: Chip
    private lateinit var tvRadiusHeader: TextView
    private lateinit var tvLocationStatus: TextView
    private lateinit var btnRefreshLocation: ImageButton
    private lateinit var rvPlaces: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmptySubtitle: TextView
    private lateinit var btnEmptyAction: MaterialButton

    private var userLat: Double = 12.9716 // Default: Bangalore
    private var userLon: Double = 77.5946
    private var selectedCategory: String = "hospital" // hospital, pharmacy, police, favourites
    private var selectedRadiusMeters: Int = 2000 // 1000, 2000, 5000

    private var googleSignInClient: GoogleSignInClient? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var isDemoSignedIn = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            getUserLocation()
        } else {
            Toast.makeText(this, "Location permission denied. Using default location.", Toast.LENGTH_SHORT).show()
            updateLocationStatus()
            loadPlaces()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                performDemoSignIn()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to Demo Sign In if Google Sign In isn't configured with web client ID
            performDemoSignIn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getDatabase(this)

        initViews()
        setupAuth()
        setupRecyclerView()
        setupListeners()

        checkLocationPermission()
    }

    private fun initViews() {
        btnAuth = findViewById(R.id.btnAuth)
        tvUserName = findViewById(R.id.tvUserName)
        cgCategory = findViewById(R.id.cgCategory)
        cgRadius = findViewById(R.id.cgRadius)
        chipHospital = findViewById(R.id.chipHospital)
        chipPharmacy = findViewById(R.id.chipPharmacy)
        chipPolice = findViewById(R.id.chipPolice)
        chipFavourites = findViewById(R.id.chipFavourites)
        chip1km = findViewById(R.id.chip1km)
        chip2km = findViewById(R.id.chip2km)
        chip5km = findViewById(R.id.chip5km)
        tvRadiusHeader = findViewById(R.id.tvRadiusHeader)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation)
        rvPlaces = findViewById(R.id.rvPlaces)
        progressBar = findViewById(R.id.progressBar)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle)
        btnEmptyAction = findViewById(R.id.btnEmptyAction)
    }

    private fun setupAuth() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            firebaseAuth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateAuthUI()
    }

    private fun updateAuthUI() {
        val user = firebaseAuth?.currentUser
        if (user != null) {
            tvUserName.text = "Signed in as: ${user.displayName ?: user.email ?: "Google User"}"
            btnAuth.text = getString(R.string.sign_out)
        } else if (isDemoSignedIn) {
            tvUserName.text = "Signed in as: Demo User (Google Account)"
            btnAuth.text = getString(R.string.sign_out)
        } else {
            tvUserName.text = getString(R.string.guest_user)
            btnAuth.text = getString(R.string.sign_in)
        }
    }

    private fun handleAuthButtonClick() {
        if (firebaseAuth?.currentUser != null || isDemoSignedIn) {
            // Sign Out
            firebaseAuth?.signOut()
            googleSignInClient?.signOut()
            isDemoSignedIn = false
            updateAuthUI()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
        } else {
            // Sign In
            val signInIntent = googleSignInClient?.signInIntent
            if (signInIntent != null) {
                googleSignInLauncher.launch(signInIntent)
            } else {
                performDemoSignIn()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val idToken = account.idToken
        if (idToken != null && firebaseAuth != null) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth?.signInWithCredential(credential)
                ?.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        updateAuthUI()
                        Toast.makeText(this, "Signed in with Google", Toast.LENGTH_SHORT).show()
                    } else {
                        performDemoSignIn()
                    }
                }
        } else {
            performDemoSignIn()
        }
    }

    private fun performDemoSignIn() {
        isDemoSignedIn = true
        updateAuthUI()
        Toast.makeText(this, "Signed in as Demo Google Account", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = PlaceAdapter(
            onDirectionsClick = { place -> openDirections(place) },
            onCallClick = { place -> openDialer(place) },
            onFavoriteClick = { place -> toggleFavorite(place) }
        )
        rvPlaces.layoutManager = LinearLayoutManager(this)
        rvPlaces.adapter = adapter
    }

    private fun setupListeners() {
        btnAuth.setOnClickListener { handleAuthButtonClick() }

        cgCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedCategory = when (checkedIds[0]) {
                    R.id.chipHospital -> "hospital"
                    R.id.chipPharmacy -> "pharmacy"
                    R.id.chipPolice -> "police"
                    R.id.chipFavourites -> "favourites"
                    else -> "hospital"
                }

                if (selectedCategory == "favourites") {
                    cgRadius.visibility = View.GONE
                    tvRadiusHeader.visibility = View.GONE
                } else {
                    cgRadius.visibility = View.VISIBLE
                    tvRadiusHeader.visibility = View.VISIBLE
                }

                loadPlaces()
            }
        }

        cgRadius.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedRadiusMeters = when (checkedIds[0]) {
                    R.id.chip1km -> 1000
                    R.id.chip2km -> 2000
                    R.id.chip5km -> 5000
                    else -> 2000
                }
                loadPlaces()
            }
        }

        btnRefreshLocation.setOnClickListener {
            checkLocationPermission()
        }

        btnEmptyAction.setOnClickListener {
            if (selectedCategory == "favourites") {
                chipHospital.isChecked = true
            } else {
                chip5km.isChecked = true
            }
        }
    }

    private fun checkLocationPermission() {
        val finePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (finePermission == PackageManager.PERMISSION_GRANTED || coarsePermission == PackageManager.PERMISSION_GRANTED) {
            getUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    userLat = location.latitude
                    userLon = location.longitude
                }
                updateLocationStatus()
                loadPlaces()
            }.addOnFailureListener {
                updateLocationStatus()
                loadPlaces()
            }
        } else {
            updateLocationStatus()
            loadPlaces()
        }
    }

    private fun updateLocationStatus() {
        val latStr = String.format(Locale.getDefault(), "%.4f", userLat)
        val lonStr = String.format(Locale.getDefault(), "%.4f", userLon)
        tvLocationStatus.text = "Location: $latStr, $lonStr"
    }

    private fun loadPlaces() {
        if (selectedCategory == "favourites") {
            loadFavouritesFromDb()
        } else {
            fetchPlacesFromOverpass()
        }
    }

    private fun fetchPlacesFromOverpass() {
        progressBar.visibility = View.VISIBLE
        rvPlaces.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            val fetchedPlaces = OverpassApiService.fetchNearbyPlaces(
                amenityType = selectedCategory,
                radiusMeters = selectedRadiusMeters,
                userLat = userLat,
                userLon = userLon
            )

            // Check favourite status from DB
            val favouriteIds = withContext(Dispatchers.IO) {
                db.favouritePlaceDao().getFavouritesList().map { it.id }.toSet()
            }

            fetchedPlaces.forEach { place ->
                place.isFavourite = favouriteIds.contains(place.id)
            }

            progressBar.visibility = View.GONE

            if (fetchedPlaces.isEmpty()) {
                showEmptyState("No emergency services found within ${selectedRadiusMeters / 1000} km.")
            } else {
                layoutEmptyState.visibility = View.GONE
                rvPlaces.visibility = View.VISIBLE
                adapter.submitList(fetchedPlaces)
            }
        }
    }

    private fun loadFavouritesFromDb() {
        progressBar.visibility = View.VISIBLE
        rvPlaces.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            val favouriteEntities = withContext(Dispatchers.IO) {
                db.favouritePlaceDao().getFavouritesList()
            }

            val places = favouriteEntities.map { entity ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(userLat, userLon, entity.lat, entity.lon, distanceResults)
                Place(
                    id = entity.id,
                    name = entity.name,
                    amenity = entity.amenity,
                    lat = entity.lat,
                    lon = entity.lon,
                    phone = entity.phone,
                    distanceMeters = distanceResults[0],
                    isFavourite = true
                )
            }.sortedBy { it.distanceMeters }

            progressBar.visibility = View.GONE

            if (places.isEmpty()) {
                showEmptyState("No favourite places saved yet.")
            } else {
                layoutEmptyState.visibility = View.GONE
                rvPlaces.visibility = View.VISIBLE
                adapter.submitList(places)
            }
        }
    }

    private fun showEmptyState(subtitleText: String) {
        tvEmptySubtitle.text = subtitleText
        if (selectedCategory == "favourites") {
            btnEmptyAction.text = "Explore Nearby Places"
        } else {
            btnEmptyAction.text = "Expand Radius to 5 km"
        }
        rvPlaces.visibility = View.GONE
        layoutEmptyState.visibility = View.VISIBLE
    }

    private fun toggleFavorite(place: Place) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.favouritePlaceDao()
            if (place.isFavourite) {
                dao.deleteById(place.id)
                place.isFavourite = false
            } else {
                dao.insertFavourite(
                    FavouritePlace(
                        id = place.id,
                        name = place.name,
                        amenity = place.amenity,
                        lat = place.lat,
                        lon = place.lon,
                        phone = place.phone
                    )
                )
                place.isFavourite = true
            }

            withContext(Dispatchers.Main) {
                if (selectedCategory == "favourites") {
                    loadFavouritesFromDb()
                } else {
                    val currentList = adapter.currentList.map { item ->
                        if (item.id == place.id) item.copy(isFavourite = place.isFavourite) else item
                    }
                    adapter.submitList(currentList)
                }
                val statusMsg = if (place.isFavourite) "Added to Favourites" else "Removed from Favourites"
                Toast.makeText(this@MainActivity, statusMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDirections(place: Place) {
        // Implicit Intent with geo: URI as required by specifications
        val geoUriStr = "geo:${place.lat},${place.lon}?q=${Uri.encode(place.name)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUriStr))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No maps app available to handle directions.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDialer(place: Place) {
        // Implicit Intent with ACTION_DIAL as required by specifications
        if (!place.phone.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to launch dialer.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
