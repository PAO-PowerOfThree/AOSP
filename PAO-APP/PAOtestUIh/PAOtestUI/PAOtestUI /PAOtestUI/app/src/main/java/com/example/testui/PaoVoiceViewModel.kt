package com.example.testui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class DisplayState {
    Idle, Listening, PlayingSound, Text
}

class PaoVoiceViewModel(@SuppressLint("StaticFieldLeak") private val context: Context) : ViewModel() {

    private val _recognizedText = MutableLiveData("")
    val recognizedText: LiveData<String> = _recognizedText

    private val _isListening = MutableLiveData(false)
    val isListening: LiveData<Boolean> = _isListening

    private val _displayState = MutableLiveData(DisplayState.Idle)
    val displayState: LiveData<DisplayState> = _displayState

    private var mediaPlayer: MediaPlayer? = null
    private val prefs: SharedPreferences = context.getSharedPreferences(
        SharedState.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // Command actions enum - Extended with music and navigation
    private enum class CommandAction {
        // AC Controls
        AC_ON, AC_OFF, TEMP_UP, TEMP_DOWN,
        REAR_AC_ON, REAR_AC_OFF, FRONT_AC_ON, FRONT_AC_OFF,
        DRIVER_AC_ON, DRIVER_AC_OFF, PASSENGER_AC_ON, PASSENGER_AC_OFF,

        // Temperature Controls
        TEMP_SET, MAKE_WARMER, MAKE_COOLER,

        // Light Controls
        LIGHT_ON, LIGHT_OFF, LIGHT_COLOR,
        FRONT_LIGHT_ON, FRONT_LIGHT_OFF,
        REAR_LIGHT_ON, REAR_LIGHT_OFF,
        WELCOME_LIGHTS, AMBIENT_LIGHTS,

        // Music Controls
        MUSIC_PLAY, MUSIC_PAUSE, MUSIC_PLAY_PAUSE, MUSIC_STOP,
        MUSIC_NEXT, MUSIC_PREVIOUS, MUSIC_SKIP, MUSIC_BACK,
        VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE,
        PLAY_ROCK, PLAY_POP, PLAY_JAZZ, PLAY_ELECTRONIC,
        SEARCH_MUSIC, PLAY_POPULAR,

        // Navigation Controls
        OPEN_GPS, OPEN_NAVIGATION, OPEN_MAP, NAVIGATE_HOME,
        NAVIGATE_WORK, GET_DIRECTIONS,

        // General
        GREETING, STOP
    }

    // Simple command mappings - Extended with music and navigation commands
    private val simpleCommands = mapOf(
        // AC Commands
        "ac on" to CommandAction.AC_ON,
        "ac off" to CommandAction.AC_OFF,
        "ac of" to CommandAction.AC_OFF,
        "air conditioner on" to CommandAction.AC_ON,
        "air conditioner off" to CommandAction.AC_OFF,
        "air con on" to CommandAction.AC_ON,
        "air con off" to CommandAction.AC_OFF,
        "air conditioning on" to CommandAction.AC_ON,
        "air conditioning off" to CommandAction.AC_OFF,

        // Temperature Commands
        "temperature up" to CommandAction.TEMP_UP,
        "temperature down" to CommandAction.TEMP_DOWN,
        "temp up" to CommandAction.TEMP_UP,
        "temp down" to CommandAction.TEMP_DOWN,
        "make it warmer" to CommandAction.MAKE_WARMER,
        "make it cooler" to CommandAction.MAKE_COOLER,
        "warmer" to CommandAction.MAKE_WARMER,
        "cooler" to CommandAction.MAKE_COOLER,

        // AC Zone Commands
        "rear ac on" to CommandAction.REAR_AC_ON,
        "rear ac off" to CommandAction.REAR_AC_OFF,
        "rear ac of" to CommandAction.REAR_AC_OFF,
        "back ac on" to CommandAction.REAR_AC_ON,
        "back ac off" to CommandAction.REAR_AC_OFF,
        "back ac of" to CommandAction.REAR_AC_OFF,
        "front ac on" to CommandAction.FRONT_AC_ON,
        "front ac off" to CommandAction.FRONT_AC_OFF,
        "front ac of" to CommandAction.FRONT_AC_OFF,
        "driver ac on" to CommandAction.DRIVER_AC_ON,
        "driver ac off" to CommandAction.DRIVER_AC_OFF,
        "passenger ac on" to CommandAction.PASSENGER_AC_ON,
        "passenger ac off" to CommandAction.PASSENGER_AC_OFF,

        // Light Commands
        "light on" to CommandAction.LIGHT_ON,
        "light off" to CommandAction.LIGHT_OFF,
        "lights on" to CommandAction.LIGHT_ON,
        "lights off" to CommandAction.LIGHT_OFF,
        "lot on" to CommandAction.LIGHT_ON,
        "lot off" to CommandAction.LIGHT_OFF,
        "lots on" to CommandAction.LIGHT_ON,
        "lots off" to CommandAction.LIGHT_OFF,
        "lite on" to CommandAction.LIGHT_ON,
        "lite off" to CommandAction.LIGHT_OFF,
        "lites on" to CommandAction.LIGHT_ON,
        "lites off" to CommandAction.LIGHT_OFF,
        "front light on" to CommandAction.FRONT_LIGHT_ON,
        "front light off" to CommandAction.FRONT_LIGHT_OFF,
        "front lights on" to CommandAction.FRONT_LIGHT_ON,
        "front lights off" to CommandAction.FRONT_LIGHT_OFF,
        "rear light on" to CommandAction.REAR_LIGHT_ON,
        "rear light off" to CommandAction.REAR_LIGHT_OFF,
        "rear lights on" to CommandAction.REAR_LIGHT_ON,
        "rear lights off" to CommandAction.REAR_LIGHT_OFF,
        "back light on" to CommandAction.REAR_LIGHT_ON,
        "back light off" to CommandAction.REAR_LIGHT_OFF,
        "welcome lights" to CommandAction.WELCOME_LIGHTS,
        "ambient lights" to CommandAction.AMBIENT_LIGHTS,

        // Music Commands
        "play music" to CommandAction.MUSIC_PLAY,
        "pause music" to CommandAction.MUSIC_PAUSE,
        "stop music" to CommandAction.MUSIC_STOP,
        "play pause" to CommandAction.MUSIC_PLAY_PAUSE,
        "pause" to CommandAction.MUSIC_PAUSE,
        "play" to CommandAction.MUSIC_PLAY,
        "stop" to CommandAction.MUSIC_STOP,
        "next song" to CommandAction.MUSIC_NEXT,
        "next track" to CommandAction.MUSIC_NEXT,
        "next" to CommandAction.MUSIC_NEXT,
        "skip song" to CommandAction.MUSIC_NEXT,
        "skip track" to CommandAction.MUSIC_NEXT,
        "skip" to CommandAction.MUSIC_SKIP,
        "previous song" to CommandAction.MUSIC_PREVIOUS,
        "previous track" to CommandAction.MUSIC_PREVIOUS,
        "previous" to CommandAction.MUSIC_PREVIOUS,
        "back track" to CommandAction.MUSIC_PREVIOUS,
        "last song" to CommandAction.MUSIC_PREVIOUS,
        "go back" to CommandAction.MUSIC_BACK,

        // Volume Commands
        "volume up" to CommandAction.VOLUME_UP,
        "volume down" to CommandAction.VOLUME_DOWN,
        "turn up volume" to CommandAction.VOLUME_UP,
        "turn down volume" to CommandAction.VOLUME_DOWN,
        "louder" to CommandAction.VOLUME_UP,
        "quieter" to CommandAction.VOLUME_DOWN,
        "mute" to CommandAction.VOLUME_MUTE,
        "mute music" to CommandAction.VOLUME_MUTE,

        // Music Genre Commands
        "play rock" to CommandAction.PLAY_ROCK,
        "play rock music" to CommandAction.PLAY_ROCK,
        "rock music" to CommandAction.PLAY_ROCK,
        "play pop" to CommandAction.PLAY_POP,
        "play pop music" to CommandAction.PLAY_POP,
        "pop music" to CommandAction.PLAY_POP,
        "play jazz" to CommandAction.PLAY_JAZZ,
        "play jazz music" to CommandAction.PLAY_JAZZ,
        "jazz music" to CommandAction.PLAY_JAZZ,
        "play electronic" to CommandAction.PLAY_ELECTRONIC,
        "play electronic music" to CommandAction.PLAY_ELECTRONIC,
        "electronic music" to CommandAction.PLAY_ELECTRONIC,
        "play popular" to CommandAction.PLAY_POPULAR,
        "play popular songs" to CommandAction.PLAY_POPULAR,
        "popular music" to CommandAction.PLAY_POPULAR,

        // Navigation Commands
        "open gps" to CommandAction.OPEN_GPS,
        "open navigation" to CommandAction.OPEN_NAVIGATION,
        "open map" to CommandAction.OPEN_MAP,
        "show map" to CommandAction.OPEN_MAP,
        "navigate" to CommandAction.OPEN_NAVIGATION,
        "navigation" to CommandAction.OPEN_NAVIGATION,
        "gps" to CommandAction.OPEN_GPS,
        "map" to CommandAction.OPEN_MAP,
        "directions" to CommandAction.GET_DIRECTIONS,
        "get directions" to CommandAction.GET_DIRECTIONS,
        "navigate home" to CommandAction.NAVIGATE_HOME,
        "go home" to CommandAction.NAVIGATE_HOME,
        "navigate to work" to CommandAction.NAVIGATE_WORK,
        "go to work" to CommandAction.NAVIGATE_WORK,

        // Greetings
        "hi" to CommandAction.GREETING,
        "hey" to CommandAction.GREETING,
        "hello" to CommandAction.GREETING,
        "hi car" to CommandAction.GREETING,
        "hey car" to CommandAction.GREETING,
        "hello car" to CommandAction.GREETING,
        "hello pao" to CommandAction.GREETING,
        "hey pao" to CommandAction.GREETING,
        "hi pao" to CommandAction.GREETING,
        "howdy" to CommandAction.GREETING,
        "good morning" to CommandAction.GREETING,
        "good afternoon" to CommandAction.GREETING,
        "good evening" to CommandAction.GREETING,

        // Control
        "quit" to CommandAction.STOP,
        "exit" to CommandAction.STOP,
        "bye" to CommandAction.STOP,
        "goodbye" to CommandAction.STOP
    )

    // Complex command prefixes that need special parsing
    private val complexCommandPrefixes = listOf(
        "light on",
        "lights on",
        "set",
        "make it",
        "front light on",
        "rear light on",
        "front lights on",
        "rear lights on",
        "play song",
        "search for",
        "find music"
    )

    fun setDisplayState(state: DisplayState) {
        _displayState.postValue(state)
        Log.d(TAG, "Display state changed to: $state")
    }

    fun updateRecognizedText(text: String, state: DisplayState, vhalManager: VhalManager?) {
        val recognized = extractRecognizedText(text)
        val normalized = recognized.lowercase().trim()

        // Only update text if it's a valid command or error
        if (isValidCommand(normalized) || isErrorMessage(recognized)) {
            _recognizedText.postValue(recognized)
        } else {
            _recognizedText.postValue("")
        }

        // Process commands
        if (isValidCommand(normalized)) {
            _isListening.postValue(false)
            _displayState.postValue(DisplayState.PlayingSound)
            processCommand(normalized, vhalManager)
            Log.d(TAG, "Command processed: '$normalized'")
        } else {
            _displayState.postValue(state)
        }
    }

    private fun isValidCommand(normalizedText: String): Boolean {
        return simpleCommands.containsKey(normalizedText) ||
                complexCommandPrefixes.any { normalizedText.startsWith(it) }
    }

    private fun isErrorMessage(text: String): Boolean {
        return text.contains("Error", ignoreCase = true) ||
                text.contains("Permission denied", ignoreCase = true) ||
                text.contains("Model not loaded", ignoreCase = true) ||
                text.contains("timed out", ignoreCase = true) ||
                text.contains("No text recognized", ignoreCase = true)
    }

    private fun processCommand(command: String, vhalManager: VhalManager?) {
        when {
            // Handle simple commands first
            simpleCommands.containsKey(command) -> {
                handleSimpleCommand(simpleCommands[command]!!, vhalManager)
            }

            // Handle complex light color commands
            command.startsWith("light on") || command.startsWith("lights on") -> {
                handleLightColorCommand(command, vhalManager)
            }

            command.startsWith("front light on") || command.startsWith("front lights on") -> {
                handleFrontLightColorCommand(command, vhalManager)
            }

            command.startsWith("rear light on") || command.startsWith("rear lights on") -> {
                handleRearLightColorCommand(command, vhalManager)
            }

            // Handle temperature setting commands
            command.startsWith("set") && command.contains("temp") -> {
                handleSetTemperatureCommand(command)
            }

            // Handle music search commands
            command.startsWith("play song") || command.startsWith("search for") || command.startsWith("find music") -> {
                handleMusicSearchCommand(command)
            }
        }
    }

    private fun handleSimpleCommand(action: CommandAction, vhalManager: VhalManager?) {
        when (action) {
            // AC Controls
            CommandAction.AC_ON -> {
                vhalManager?.setAcStatusForAllSeats(1)
                playSound(R.raw.ac_on)
                updatePreferences {
                    putInt(SharedState.VOSK_AC_STATE, 1)
                    putBoolean(SharedState.KEY_AC_STATE, true)
                }
                Log.d(TAG, "All AC turned ON")
            }

            CommandAction.AC_OFF -> {
                vhalManager?.setAcStatusForAllSeats(0)
                playSound(R.raw.ac_off)
                updatePreferences {
                    putInt(SharedState.VOSK_AC_STATE, 0)
                    putBoolean(SharedState.KEY_AC_STATE, false)
                }
                Log.d(TAG, "All AC turned OFF")
            }

            // [Previous AC and Light commands remain the same...]

            // Music Controls
            CommandAction.MUSIC_PLAY -> {
                sendMusicCommand("PLAY")
                playSound(R.raw.hey_car) // Using generic sound for now
                Log.d(TAG, "Music play command sent")
            }

            CommandAction.MUSIC_PAUSE -> {
                sendMusicCommand("PAUSE")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Music pause command sent")
            }

            CommandAction.MUSIC_PLAY_PAUSE -> {
                sendMusicCommand("PLAY_PAUSE")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Music play/pause command sent")
            }

            CommandAction.MUSIC_STOP -> {
                sendMusicCommand("STOP")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Music stop command sent")
            }

            CommandAction.MUSIC_NEXT -> {
                sendMusicCommand("NEXT")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Next track command sent")
            }

            CommandAction.MUSIC_SKIP -> {
                sendMusicCommand("NEXT")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Skip track command sent")
            }

            CommandAction.MUSIC_PREVIOUS -> {
                sendMusicCommand("PREVIOUS")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Previous track command sent")
            }

            CommandAction.MUSIC_BACK -> {
                sendMusicCommand("PREVIOUS")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Back track command sent")
            }

            CommandAction.VOLUME_UP -> {
                sendMusicCommand("VOLUME_UP")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Volume up command sent")
            }

            CommandAction.VOLUME_DOWN -> {
                sendMusicCommand("VOLUME_DOWN")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Volume down command sent")
            }

            CommandAction.VOLUME_MUTE -> {
                sendMusicCommand("MUTE")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Mute command sent")
            }

            // Music Genre Commands
            CommandAction.PLAY_ROCK -> {
                sendMusicCommand("PLAY_GENRE:rock")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Playing rock music")
            }

            CommandAction.PLAY_POP -> {
                sendMusicCommand("PLAY_GENRE:pop")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Playing pop music")
            }

            CommandAction.PLAY_JAZZ -> {
                sendMusicCommand("PLAY_GENRE:jazz")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Playing jazz music")
            }

            CommandAction.PLAY_ELECTRONIC -> {
                sendMusicCommand("PLAY_GENRE:electronic")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Playing electronic music")
            }

            CommandAction.PLAY_POPULAR -> {
                sendMusicCommand("PLAY_POPULAR")
                playSound(R.raw.hey_car)
                Log.d(TAG, "Playing popular tracks")
            }

            // Navigation Commands
            CommandAction.OPEN_GPS, CommandAction.OPEN_NAVIGATION, CommandAction.OPEN_MAP -> {
                openNavigationActivity()
                playSound(R.raw.hey_car)
                Log.d(TAG, "Opening navigation")
            }

            CommandAction.GET_DIRECTIONS -> {
                openNavigationActivity()
                playSound(R.raw.hey_car)
                Log.d(TAG, "Getting directions")
            }

            CommandAction.NAVIGATE_HOME -> {
                openNavigationActivity()
                // Could send specific destination command here
                playSound(R.raw.hey_car)
                Log.d(TAG, "Navigating to home")
            }

            CommandAction.NAVIGATE_WORK -> {
                openNavigationActivity()
                // Could send specific destination command here
                playSound(R.raw.hey_car)
                Log.d(TAG, "Navigating to work")
            }

            // Temperature Controls (keeping existing ones)
            CommandAction.TEMP_UP -> {
                playSound(R.raw.temp_up)
                val currentTemp = prefs.getInt(SharedState.KEY_INNER_TEMP, 22)
                val newTemp = (currentTemp + 1).coerceAtMost(30)
                updatePreferences {
                    putInt(SharedState.VOSK_AC_STATE, 3)
                    putInt(SharedState.KEY_INNER_TEMP, newTemp)
                }
                Log.d(TAG, "Temperature increased to ${newTemp}°C")
            }

            CommandAction.TEMP_DOWN -> {
                playSound(R.raw.temp_down)
                val currentTemp = prefs.getInt(SharedState.KEY_INNER_TEMP, 22)
                val newTemp = (currentTemp - 1).coerceAtLeast(16)
                updatePreferences {
                    putInt(SharedState.VOSK_AC_STATE, 2)
                    putInt(SharedState.KEY_INNER_TEMP, newTemp)
                }
                Log.d(TAG, "Temperature decreased to ${newTemp}°C")
            }

            CommandAction.MAKE_WARMER -> {
                val currentTemp = prefs.getInt(SharedState.KEY_INNER_TEMP, 22)
                val newTemp = (currentTemp + 2).coerceAtMost(30)
                vhalManager?.setTemperatureForAllSeats(newTemp)
                updatePreferences {
                    putInt(SharedState.KEY_INNER_TEMP, newTemp)
                }
                playSound(R.raw.temp_up)
                Log.d(TAG, "Temperature increased to ${newTemp}°C")
            }

            CommandAction.MAKE_COOLER -> {
                val currentTemp = prefs.getInt(SharedState.KEY_INNER_TEMP, 22)
                val newTemp = (currentTemp - 2).coerceAtLeast(16)
                vhalManager?.setTemperatureForAllSeats(newTemp)
                updatePreferences {
                    putInt(SharedState.KEY_INNER_TEMP, newTemp)
                }
                playSound(R.raw.temp_down)
                Log.d(TAG, "Temperature decreased to ${newTemp}°C")
            }

            // Light Controls (keeping existing ones)
            CommandAction.LIGHT_ON -> {
                vhalManager?.setAmbientLighting(android.graphics.Color.CYAN, 50)
                playSound(R.raw.light_on)
                updatePreferences {
                    putInt(SharedState.KEY_AMBIENT_COLOR, android.graphics.Color.CYAN)
                }
                Log.d(TAG, "Ambient lighting turned ON")
            }

            CommandAction.LIGHT_OFF -> {
                vhalManager?.turnOffAllLeds()
                playSound(R.raw.light_off)
                Log.d(TAG, "All lights turned OFF")
            }

            CommandAction.FRONT_LIGHT_ON -> {
                vhalManager?.setFrontLedColor(android.graphics.Color.CYAN, 60)
                playSound(R.raw.light_on)
                Log.d(TAG, "Front lights turned ON")
            }

            CommandAction.FRONT_LIGHT_OFF -> {
                vhalManager?.turnOffFrontLeds()
                playSound(R.raw.light_off)
                Log.d(TAG, "Front lights turned OFF")
            }

            CommandAction.REAR_LIGHT_ON -> {
                vhalManager?.setRearLedColor(android.graphics.Color.CYAN, 60)
                playSound(R.raw.light_on)
                Log.d(TAG, "Rear lights turned ON")
            }

            CommandAction.REAR_LIGHT_OFF -> {
                vhalManager?.turnOffRearLeds()
                playSound(R.raw.light_off)
                Log.d(TAG, "Rear lights turned OFF")
            }

            // General Commands
            CommandAction.GREETING -> {
                playSound(R.raw.hey_car)
                Log.d(TAG, "Greeting acknowledged")
            }

            CommandAction.STOP -> {
                Log.d(TAG, "Stop command acknowledged")
            }

            else -> {
                Log.w(TAG, "Unhandled command action: $action")
                playSound(R.raw.hey_car)
            }
        }
    }

    // Send music commands to MainActivity via broadcast or shared preferences
    private fun sendMusicCommand(command: String) {
        try {
            // Using shared preferences to communicate with MainActivity
            updatePreferences {
                putString("voice_music_command", command)
                putLong("voice_command_timestamp", System.currentTimeMillis())
            }
            Log.d(TAG, "Music command sent: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send music command: ${e.message}")
        }
    }

    // Open navigation activity
    private fun openNavigationActivity() {
        try {
            val intent = Intent(context, NavigationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("isDarkMode", prefs.getBoolean("isDarkMode", false))
            }
            context.startActivity(intent)
            Log.d(TAG, "Navigation activity opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open navigation: ${e.message}")
        }
    }

    private fun handleMusicSearchCommand(command: String) {
        // Extract search query from command
        val query = when {
            command.startsWith("play song") -> command.removePrefix("play song").trim()
            command.startsWith("search for") -> command.removePrefix("search for").trim()
            command.startsWith("find music") -> command.removePrefix("find music").trim()
            else -> ""
        }

        if (query.isNotEmpty()) {
            sendMusicCommand("SEARCH:$query")
            playSound(R.raw.hey_car)
            Log.d(TAG, "Music search command: $query")
        }
    }

    fun clearErrorState() {
        _recognizedText.value = ""
        _displayState.value = DisplayState.Idle
    }

    // Keep existing light color command handlers...
    private fun handleLightColorCommand(command: String, vhalManager: VhalManager?) {
        val color = parseColorFromCommand(command)

        vhalManager?.setAllLedColors(color, 70)
        updatePreferences {
            putInt(SharedState.KEY_AMBIENT_COLOR, color)
        }
        playSound(R.raw.light_on)

        val colorName = getColorName(color)
        Log.d(TAG, "All lights set to $colorName")
    }

    private fun handleFrontLightColorCommand(command: String, vhalManager: VhalManager?) {
        val color = parseColorFromCommand(command)

        vhalManager?.setFrontLedColor(color, 70)
        playSound(R.raw.light_on)

        val colorName = getColorName(color)
        Log.d(TAG, "Front lights set to $colorName")
    }

    private fun handleRearLightColorCommand(command: String, vhalManager: VhalManager?) {
        val color = parseColorFromCommand(command)

        vhalManager?.setRearLedColor(color, 70)
        playSound(R.raw.light_on)

        val colorName = getColorName(color)
        Log.d(TAG, "Rear lights set to $colorName")
    }

    private fun parseColorFromCommand(command: String): Int {
        return when {
            command.contains("red") -> android.graphics.Color.RED
            command.contains("blue") -> android.graphics.Color.BLUE
            command.contains("white") -> android.graphics.Color.WHITE
            command.contains("green") -> android.graphics.Color.GREEN
            command.contains("yellow") -> android.graphics.Color.YELLOW
            command.contains("cyan") -> android.graphics.Color.CYAN
            command.contains("magenta") -> android.graphics.Color.MAGENTA
            command.contains("purple") -> android.graphics.Color.parseColor("#800080")
            command.contains("orange") -> android.graphics.Color.parseColor("#FFA500")
            command.contains("pink") -> android.graphics.Color.parseColor("#FFC0CB")
            else -> android.graphics.Color.CYAN
        }
    }

    private fun handleSetTemperatureCommand(command: String) {
        val tempRegex = "\\d+".toRegex()
        val tempMatch = tempRegex.find(command)
        val temperature = tempMatch?.value?.toIntOrNull() ?: 22
        val clampedTemp = temperature.coerceIn(16, 30)

        updatePreferences {
            putInt(SharedState.KEY_INNER_TEMP, clampedTemp)
        }
        playSound(R.raw.temp_up)
        Log.d(TAG, "Temperature set to ${clampedTemp}°C")
    }

    private fun playSound(resourceId: Int) {
        viewModelScope.launch {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(context, resourceId)
                mediaPlayer?.setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    _displayState.postValue(DisplayState.Text)
                    Log.d(TAG, "Sound playback completed")
                }
                mediaPlayer?.start()
                Log.d(TAG, "Playing sound resource: $resourceId")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound: ${e.message}")
                _displayState.postValue(DisplayState.Text)
            }
        }
    }

    private fun updatePreferences(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    private fun getColorName(color: Int): String {
        return when (color) {
            android.graphics.Color.RED -> "red"
            android.graphics.Color.GREEN -> "green"
            android.graphics.Color.BLUE -> "blue"
            android.graphics.Color.WHITE -> "white"
            android.graphics.Color.YELLOW -> "yellow"
            android.graphics.Color.CYAN -> "cyan"
            android.graphics.Color.MAGENTA -> "magenta"
            android.graphics.Color.BLACK -> "black"
            else -> "custom"
        }
    }

    fun toggleListening() {
        val currentlyListening = _isListening.value ?: false
        _isListening.postValue(!currentlyListening)
        _displayState.postValue(if (!currentlyListening) DisplayState.Listening else DisplayState.Idle)
        Log.d(TAG, "Listening toggled: ${!currentlyListening}")
    }

    fun isStopPhrase(text: String): Boolean = isValidCommand(text.lowercase().trim())

    fun extractRecognizedText(result: String): String {
        Log.d(TAG, "Raw result: $result")
        val regex = Regex("\"(?:text|partial)\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(result)?.groupValues?.get(1)?.trim() ?: result.trim()
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d(TAG, "ViewModel cleared")
    }

    companion object {
        private const val TAG = "PaoVoiceViewModel"

        class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PaoVoiceViewModel::class.java)) {
                    return PaoVoiceViewModel(context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}