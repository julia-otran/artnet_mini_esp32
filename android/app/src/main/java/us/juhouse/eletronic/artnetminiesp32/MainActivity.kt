package us.juhouse.eletronic.artnetminiesp32

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewmodel.compose.viewModel
import us.juhouse.eletronic.artnetminiesp32.ui.theme.ArtNetMiniESP32Theme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable

interface MainActivityCallbacks {
    fun findDevices()
    fun sendRequest(data: BluetoothSerialData)
}

data class MainUiState(val statusText: String)

fun String.trim(trimLength: Int): String {
    if (this.length <= trimLength) {
        return this
    }

    return this.substring(0, trimLength)
}

@OptIn(SavedStateHandleSaveableApi::class)
class MainViewModel(state: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(""))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()


    var currentPassword by state.saveable { mutableStateOf("") }
    var newPassword by state.saveable { mutableStateOf("") }
    var channelCount by state.saveable { mutableStateOf("") }
    var net by state.saveable { mutableStateOf("") }
    var subnet by state.saveable { mutableStateOf("") }
    var universe by state.saveable { mutableStateOf("") }
    var wirelessSSID by state.saveable { mutableStateOf("") }
    var wirelessPassword by state.saveable { mutableStateOf("") }
    var wirelessMode by state.saveable { mutableStateOf(WirelessMode.NONE) }

    fun setData(data: String) {
        _uiState.update {
            it.copy(
                statusText = data
            )
        }
    }

    fun getSettingsRequest(): BluetoothSerialDataSettings {
        return BluetoothSerialDataSettings(
            currentPassword,
            channelCount.toUInt(),
            net.toUInt(),
            subnet.toUInt(),
            universe.toUInt(),
            wirelessMode,
            wirelessSSID,
            wirelessPassword
        )
    }
}

class MainActivity : ComponentActivity(), BluetoothSerialCommunicator.Callbacks, MainActivityCallbacks {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val viewModel: MainViewModel by viewModels()
    private var bluetoothDevice: BluetoothDevice? = null
    private var serialCommunicator: BluetoothSerialCommunicator? = null

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            findDevices()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            findDevices()
        }
    }

    override fun findDevices() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return
        }

        bluetoothDevice = bluetoothAdapter.bondedDevices.find { it.name == "ArtNet Mini ESP32" }

        serialCommunicator?.dispose()

        bluetoothDevice?.let {
            serialCommunicator = BluetoothSerialCommunicator(it, this)
            serialCommunicator?.reload()
        } ?: {
            serialCommunicator = null
        }
    }

    override fun sendRequest(data: BluetoothSerialData) {
        serialCommunicator?.sendRequest(data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        setContent {
            ArtNetMiniESP32Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting(this, viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (!bluetoothAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(intent)
        } else {
            findDevices()
        }
    }

    override fun onStop() {
        super.onStop()

        serialCommunicator?.dispose()
        serialCommunicator = null
    }

    override fun onDataReceived(data: String) {
        runOnUiThread {
            viewModel.setData(data)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(callbacks: MainActivityCallbacks, mainViewModel: MainViewModel, modifier: Modifier = Modifier) {
    val uiState by mainViewModel.uiState.collectAsState()
    var wirelessModeDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(32.dp)) {
        Button(onClick = { callbacks.findDevices() }) {
            Text(text = "Refresh")
        }
        Text(
            text = uiState.statusText,
            modifier = modifier
        )
        TextField(
            value = mainViewModel.currentPassword,
            onValueChange = { mainViewModel.currentPassword = it.trim(12) },
            label = {
                Text(text = "Current Password")
            },
            singleLine = true,
        )
        TextField(
            value = mainViewModel.newPassword,
            onValueChange = { mainViewModel.newPassword = it.trim(12) },
            label = {
                Text(text = "New Password")
            },
            singleLine = true,
        )
        Button(onClick = {
            callbacks.sendRequest(
                BluetoothSerialDataPasswordChange(mainViewModel.currentPassword, mainViewModel.newPassword)
            )
        }) {
            Text(text = "Save New Password")
        }
        TextField(
            value = mainViewModel.channelCount,
            onValueChange = { mainViewModel.channelCount = it.replace(Regex("\\D"), "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = {
                Text(text = "Channel Count")
            },
            placeholder = {
                Text(text = "Min 192 Max 512")
            },
            singleLine = true,
        )
        TextField(
            value = mainViewModel.net,
            onValueChange = { mainViewModel.net = it.replace(Regex("\\D"), "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = {
                Text(text = "ArtNet Net")
            },
            placeholder = {
                Text(text = "Min 0 Max 127")
            },
            singleLine = true,
        )
        TextField(
            value = mainViewModel.subnet,
            onValueChange = { mainViewModel.subnet = it.replace(Regex("\\D"), "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = {
                Text(text = "ArtNet Subnet")
            },
            placeholder = {
                Text(text = "Min 0 Max 15")
            },
            singleLine = true,
        )
        TextField(
            value = mainViewModel.universe,
            onValueChange = { mainViewModel.universe = it.replace(Regex("\\D"), "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = {
                Text(text = "ArtNet Universe")
            },
            placeholder = {
                Text(text = "Min 0 Max 15")
            },
            singleLine = true,
        )
        Button(onClick = { wirelessModeDropdownExpanded = !wirelessModeDropdownExpanded }) {
            Text(text = "Wireless Mode: ${mainViewModel.wirelessMode.name}")
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Open")
        }
        DropdownMenu(expanded = wirelessModeDropdownExpanded, onDismissRequest = { wirelessModeDropdownExpanded = false }) {
            DropdownMenuItem(text = { Text(text = "None")}, onClick = {
                wirelessModeDropdownExpanded = false
                mainViewModel.wirelessMode = WirelessMode.NONE
            })
            DropdownMenuItem(text = { Text(text = "Client DHCP")}, onClick = {
                mainViewModel.wirelessMode = WirelessMode.CLIENT_DHCP
                wirelessModeDropdownExpanded = false
            })
            DropdownMenuItem(text = { Text(text = "AP")}, onClick = {
                mainViewModel.wirelessMode = WirelessMode.AP
                wirelessModeDropdownExpanded = false
            })
        }
        TextField(
            value = mainViewModel.wirelessSSID,
            onValueChange = { mainViewModel.wirelessSSID = it.trim(200) },
            label = {
                Text(text = "Wireless SSID")
            },
            singleLine = true,
        )
        TextField(
            value = mainViewModel.wirelessPassword,
            onValueChange = { mainViewModel.wirelessPassword = it.trim(200) },
            label = {
                Text(text = "Wireless Password")
            },
            singleLine = true,
        )
        Button(onClick = { callbacks.sendRequest(mainViewModel.getSettingsRequest()) }) {
            Text(text = "Set Settings")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ArtNetMiniESP32Theme {
        Greeting(callbacks = object : MainActivityCallbacks {
            override fun findDevices() {
            }

            override fun sendRequest(data: BluetoothSerialData) {
            }
        }, viewModel())
    }
}