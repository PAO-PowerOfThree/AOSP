package com.example.testui

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import android.widget.ImageView
import android.widget.TextView
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String,
    val streamUrl: String,
    val duration: Long = 0
)

// Jamendo API data classes
data class JamendoResponse(
    @SerializedName("results")
    val results: List<JamendoTrack>
)

data class JamendoTrack(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("artist_name")
    val artistName: String,
    @SerializedName("album_name")
    val albumName: String?,
    @SerializedName("album_image")
    val albumImage: String?,
    @SerializedName("audio")
    val audioUrl: String,
    @SerializedName("duration")
    val duration: Long?
)

// Retrofit API interface
interface JamendoApiService {
    @GET("tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("search") search: String,
        @Query("limit") limit: Int = 20,
        @Query("include") include: String = "musicinfo",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse

    @GET("tracks/")
    suspend fun getPopularTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("order") order: String = "popularity_total",
        @Query("limit") limit: Int = 20,
        @Query("include") include: String = "musicinfo",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse

    @GET("tracks/")
    suspend fun getTracksByTag(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("tags") tags: String,
        @Query("limit") limit: Int = 20,
        @Query("include") include: String = "musicinfo",
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse
}

class MusicService(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Jamendo API client ID
    private val jamendoClientId = "e88ae68a"

    // Retrofit setup
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.jamendo.com/v3.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(JamendoApiService::class.java)

    private val _currentTrack = MutableLiveData<Track?>()
    val currentTrack: LiveData<Track?> = _currentTrack

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData<Int>(0)
    val currentPosition: LiveData<Int> = _currentPosition

    private val _playlist = MutableLiveData<List<Track>>(emptyList())
    val playlist: LiveData<List<Track>> = _playlist

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentIndex = 0
    private var currentVolume = 0.7f  // Default to 70% to match MainActivity default

    init {
        _playlist.value = emptyList()
        _currentTrack.value = null

        // Load popular tracks on initialization
        getPopularTracks {}
    }

    fun searchTracks(query: String, callback: (List<Track>) -> Unit) {
        scope.launch {
            _isLoading.postValue(true)
            try {
                val response = apiService.searchTracks(
                    clientId = jamendoClientId,
                    search = query
                )

                val tracks = response.results.map { it.toTrack() }
                _playlist.postValue(tracks)

                if (tracks.isNotEmpty()) {
                    currentIndex = 0
                    _currentTrack.postValue(tracks.first())
                    loadTrack(tracks.first())
                }

                callback(tracks)
                Log.d("MusicService", "Search completed: ${tracks.size} tracks found")
            } catch (e: Exception) {
                Log.e("MusicService", "Search failed: ${e.message}", e)
                callback(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun getPopularTracks(callback: (List<Track>) -> Unit) {
        scope.launch {
            _isLoading.postValue(true)
            try {
                val response = apiService.getPopularTracks(clientId = jamendoClientId)
                val tracks = response.results.map { it.toTrack() }
                _playlist.postValue(tracks)

                if (tracks.isNotEmpty()) {
                    currentIndex = 0
                    _currentTrack.postValue(tracks.first())
                    loadTrack(tracks.first())
                }

                callback(tracks)
                Log.d("MusicService", "Popular tracks loaded: ${tracks.size} tracks")
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to get popular tracks: ${e.message}", e)
                callback(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun searchByGenre(genre: String, callback: (List<Track>) -> Unit) {
        scope.launch {
            _isLoading.postValue(true)
            try {
                val response = apiService.getTracksByTag(
                    clientId = jamendoClientId,
                    tags = genre
                )

                val tracks = response.results.map { it.toTrack() }
                _playlist.postValue(tracks)

                if (tracks.isNotEmpty()) {
                    currentIndex = 0
                    _currentTrack.postValue(tracks.first())
                    loadTrack(tracks.first())
                }

                callback(tracks)
                Log.d("MusicService", "Genre search completed: ${tracks.size} tracks found")
            } catch (e: Exception) {
                Log.e("MusicService", "Genre search failed: ${e.message}", e)
                callback(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun loadTrack(track: Track) {
        scope.launch {
            try {
                // Release previous MediaPlayer
                mediaPlayer?.release()

                // Create new MediaPlayer
                mediaPlayer = MediaPlayer().apply {
                    // Set error listener first
                    setOnErrorListener { mp, what, extra ->
                        Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra, url=${track.streamUrl}")
                        // Try to play next track on error
                        scope.launch {
                            playNext()
                        }
                        true
                    }

                    setOnPreparedListener { mp ->
                        _currentTrack.postValue(track)
                        Log.d("MusicService", "Track prepared and ready: ${track.title}")
                        // Auto-play the track
                        play()
                    }

                    setOnCompletionListener {
                        playNext()
                    }

                    // Set data source and prepare
                    setDataSource(track.streamUrl)
                    prepareAsync()
                }

                Log.d("MusicService", "Loading track: ${track.title} from ${track.streamUrl}")

            } catch (e: Exception) {
                Log.e("MusicService", "Failed to load track: ${e.message}", e)
                // Try next track on error
                playNext()
            }
        }
    }

    fun play() {
        try {
            mediaPlayer?.let { mp ->
                if (!mp.isPlaying) {
                    mp.start()
                    _isPlaying.postValue(true)
                    startPositionUpdater()
                    Log.d("MusicService", "Playback started")
                }
            } ?: Log.w("MusicService", "Cannot play: MediaPlayer is null")
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to play: ${e.message}", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                    _isPlaying.postValue(false)
                    Log.d("MusicService", "Playback paused")
                }
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to pause: ${e.message}", e)
        }
    }

    fun stop() {
        pause()
        seekTo(0)
        Log.d("MusicService", "Playback stopped and reset")
    }

    fun playPause() {
        if (_isPlaying.value == true) {
            pause()
        } else {
            play()
        }
    }

    fun playNext() {
        val tracks = _playlist.value ?: return
        if (tracks.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % tracks.size
            loadTrack(tracks[currentIndex])
        } else {
            Log.w("MusicService", "Cannot play next: Playlist is empty")
        }
    }

    fun playPrevious() {
        val tracks = _playlist.value ?: return
        if (tracks.isNotEmpty()) {
            currentIndex = if (currentIndex > 0) currentIndex - 1 else tracks.size - 1
            loadTrack(tracks[currentIndex])
        } else {
            Log.w("MusicService", "Cannot play previous: Playlist is empty")
        }
    }

    private fun startPositionUpdater() {
        scope.launch {
            while (_isPlaying.value == true) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _currentPosition.postValue(player.currentPosition)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicService", "Position update error: ${e.message}")
                    break
                }
                delay(1000)
            }
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.let { mp ->
                mp.seekTo(position)
                _currentPosition.postValue(position)
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to seek: ${e.message}", e)
        }
    }

    fun updateUI(
        albumArt: ImageView,
        songTitle: TextView,
        artistName: TextView
    ) {
        _currentTrack.value?.let { track ->
            // Load album art using Picasso with better error handling
            val imageUrl = if (track.imageUrl.isNotBlank() && !track.imageUrl.contains("placeholder")) {
                track.imageUrl
            } else {
                // Use a more colorful placeholder
                "https://picsum.photos/300/300?random=${track.id}"
            }

            Log.d("MusicService", "Loading image from: $imageUrl")

            Picasso.get()
                .load(imageUrl)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .fit()
                .centerCrop()
                .into(albumArt, object : com.squareup.picasso.Callback {
                    override fun onSuccess() {
                        Log.d("MusicService", "Album art loaded successfully")
                    }
                    override fun onError(e: Exception?) {
                        Log.e("MusicService", "Failed to load album art: ${e?.message}")
                    }
                })

            songTitle.text = track.title
            artistName.text = track.artist

            Log.d("MusicService", "UI updated for track: ${track.title}")
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1.0f)
        try {
            mediaPlayer?.setVolume(currentVolume, currentVolume)
            Log.d("MusicService", "Volume set to: $currentVolume")
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to set volume: ${e.message}", e)
        }
    }

    fun getCurrentVolume(): Float {
        return currentVolume
    }

    fun increaseVolume(step: Float = 0.05f) {
        setVolume(currentVolume + step)
    }

    fun decreaseVolume(step: Float = 0.05f) {
        setVolume(currentVolume - step)
    }

    fun mute() {
        setVolume(0.0f)
    }

    fun cleanup() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            scope.cancel()
            Log.d("MusicService", "Service cleaned up")
        } catch (e: Exception) {
            Log.e("MusicService", "Cleanup error: ${e.message}", e)
        }
    }

    // Extension function to convert JamendoTrack to Track
    private fun JamendoTrack.toTrack(): Track {
        val imageUrl = if (!albumImage.isNullOrEmpty()) {
            albumImage
        } else {
            // Generate a placeholder image with track name
            "https://via.placeholder.com/300x300/4ecdc4/ffffff?text=${
                name.replace(" ", "+").take(20)
            }"
        }

        return Track(
            id = id,
            title = name,
            artist = artistName,
            album = albumName ?: "Unknown Album",
            imageUrl = imageUrl,
            streamUrl = audioUrl,
            duration = (duration ?: 0) * 1000
        )
    }
}

// Extension function to format duration
fun Long.formatDuration(): String {
    val minutes = (this / 1000) / 60
    val seconds = (this / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}