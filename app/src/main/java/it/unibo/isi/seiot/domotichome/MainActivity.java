package it.unibo.isi.seiot.domotichome;

import static it.unibo.isi.seiot.domotichome.utils.GarageState.*;
import static it.unibo.isi.seiot.domotichome.utils.KeyReqString.*;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.unibo.isi.seiot.domotichome.utils.C;
import unibo.btlib.BluetoothChannel;
import unibo.btlib.BluetoothUtils;
import unibo.btlib.ConnectToBluetoothServerTask;
import unibo.btlib.ConnectionTask;
import unibo.btlib.RealBluetoothChannel;
import unibo.btlib.exceptions.BluetoothDeviceNotFound;

public class MainActivity extends AppCompatActivity {

    private static final String DBG_TAG = "esiot";
    private static final String DEFAULT_COUNTERS_LABEL = "0";
    private static final String AUTO_BTN_STRING = "Auto Control";
    private static final String MANUAL_BTN_STRING = "Manual Control";
    private static final String BT_DEVICE_NOT_FOUND_ERROR = "Bluetooth device not found !";
    private BluetoothChannel btChannel;
    private boolean btConnected = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.d(DBG_TAG, "[ERROR] no bluetooth permissions!");
                return;
            }
            startActivityForResult(new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    C.bluetooth.ENABLE_BT_REQUEST);
        }
        initUI();
    }

    private void initUI() {
        disableAllButtons();
        findViewById(R.id.connectBtn).setOnClickListener(this::onConnectBtnClicked);

        findViewById(R.id.light_in_switch).setOnClickListener(l ->
                onSwitchPressed(R.id.light_in_switch, INDOOR_LIGHT_KEY,1,0));
        findViewById(R.id.heating_switch).setOnClickListener(l ->
                onSwitchPressed(R.id.heating_switch, HEATING_KEY,2,0));
        findViewById(R.id.alarm_switch).setOnClickListener(l ->
            onSwitchPressed(R.id.alarm_switch, ALARM_KEY,5,4));

        ((SeekBar)findViewById(R.id.sliderOutLight)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        final int value = ((SeekBar)findViewById(R.id.sliderOutLight)).getProgress();
                        sendData(OUTDOOR_LIGHT_KEY, String.valueOf(value));
                    }
                });

        findViewById(R.id.close_garage_btn).setOnClickListener(l ->
                sendData(GARAGE_KEY, String.valueOf(REQ_CLOSE.getOrdinal())));
        findViewById(R.id.pause_garage_btn).setOnClickListener(l ->
                sendData(GARAGE_KEY, String.valueOf(REQ_PAUSE.getOrdinal())));
        findViewById(R.id.open_garage_btn).setOnClickListener(l ->
                sendData(GARAGE_KEY, String.valueOf(REQ_OPEN.getOrdinal())));

        findViewById(R.id.stop_alarm_btn).setOnClickListener(l-> {
            sendData(RESET_ALARM_KEY,"");
        });

    }

    private void onSwitchPressed(final int textViewId, final String desc, final int onValue, final int offValue) {
        final SwitchCompat sc = findViewById(textViewId);
        sendData(desc, sc.isChecked() ? onValue : offValue);
        sc.setEnabled(false);
    }

    private void onConnectBtnClicked(final View view) {
        view.setEnabled(false);
        try {
            connectToBTServer();
        } catch (BluetoothDeviceNotFound bluetoothDeviceNotFound) {
            Toast.makeText(this, BT_DEVICE_NOT_FOUND_ERROR, Toast.LENGTH_LONG).show();
            bluetoothDeviceNotFound.printStackTrace();
        } finally {
            view.setEnabled(true);
        }
    }

    private void controlComponentsEnable(final boolean active) {
        final var components = List.of(R.id.light_in_switch, R.id.alarm_switch,
                R.id.close_garage_btn, R.id.open_garage_btn, R.id.pause_garage_btn,
                R.id.heating_switch, R.id.sliderOutLight, R.id.alarm_switch, R.id.heating_switch,
                R.id.light_in_switch);
        components.forEach(id -> findViewById(id).setEnabled(active));
    }
    private void enableAll() {
        controlComponentsEnable(true);
    }

    private void disableAllButtons() {
        controlComponentsEnable(false);
        findViewById(R.id.stop_alarm_btn).setEnabled(false);
    }

    private void sendData(final String desc, final Object obj) {
        final String msg = desc + ":" + obj;
        if (this.btConnected) {
            btChannel.sendMessage(msg);
            Log.d(DBG_TAG,msg);
        } else {
            Log.d(DBG_TAG, "sendData: task bluetooth non in esecuzione e bt non conneso");
        }
    }

    private void connectToBTServer() throws BluetoothDeviceNotFound {
        final BluetoothDevice serverDevice = BluetoothUtils
                .getPairedDeviceByName(C.bluetooth.BT_DEVICE_ACTING_AS_SERVER_NAME);

        final UUID uuid = BluetoothUtils.getEmbeddedDeviceDefaultUuid();

        new ConnectToBluetoothServerTask(serverDevice, uuid, new ConnectionTask.EventListener() {
            @Override
            public void onConnectionActive(final BluetoothChannel channel) {
                Toast.makeText(getApplicationContext(),"Connected", Toast.LENGTH_LONG).show();
                ((Button)findViewById(R.id.connectBtn)).setText(R.string.disconnect_btn_label);
                enableAll();
                btConnected = true;
                btChannel = channel;
                btChannel.registerListener(new RealBluetoothChannel.Listener() {
                    @Override
                    public void onMessageReceived(String receivedMessage) {
                        Log.d(DBG_TAG, "RECEIVED: " + receivedMessage);
                        try {
                            JSONObject jsonDataRecv = new JSONObject(receivedMessage);
                            ((SwitchCompat)findViewById(R.id.light_in_switch)).setChecked(jsonDataRecv.getInt("iL") == 1);
                            ((SeekBar)findViewById(R.id.sliderOutLight)).setProgress(jsonDataRecv.getInt("oL"));

                            ((SwitchCompat)findViewById(R.id.alarm_switch)).setChecked(jsonDataRecv.getInt("alarm") >= 1);
                            (findViewById(R.id.stop_alarm_btn)).setEnabled(jsonDataRecv.getInt("alarm") == 2);

                            ((SwitchCompat)findViewById(R.id.heating_switch)).setChecked(jsonDataRecv.getInt("hP") == 1);
                            ((TextView)findViewById(R.id.degree_value_temp)).setText(String.valueOf(jsonDataRecv.getInt("hT")));

                            int garageState = jsonDataRecv.getInt("gar");
                            String garageStateString = Optional.ofNullable(values()[garageState].getDescr()).orElse("No info");
                            ((TextView)findViewById(R.id.garage_status_label)).setText(garageStateString);

                            enableAll();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.d(DBG_TAG, "Errore nel leggere il json ricevuto");
                            Toast.makeText(getApplicationContext(),"Errore ricezione dati", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onMessageSent(String sentMessage) {
                        Log.d(DBG_TAG, sentMessage);
                    }
                });

            }

            @Override
            public void onConnectionCanceled() {
                disableAllButtons();
                ((SwitchCompat)findViewById(R.id.light_in_switch)).setChecked(false);
                ((Button)findViewById(R.id.connectBtn)).setText(R.string.connect_btn_label);
            }
        }).execute();
    }

}
