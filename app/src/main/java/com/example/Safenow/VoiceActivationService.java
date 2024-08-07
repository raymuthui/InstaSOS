package com.example.Safenow;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.Manifest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class VoiceActivationService extends Service implements RecognitionListener {

    private SpeechRecognizer speechRecognizer;
    private AudioManager mAudioManager;

    private static final String TAG = "VoiceActivationService";
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private final IBinder binder = new LocalBinder();
    private int originalVolume;

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private static final int RECORDING_DURATION_MS = 10 * 1000;

    private boolean emergencyActionsExecuted = false;


    @Override
    public void onCreate() {
        super.onCreate();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        initializeSpeechRecognizer();
        startListening();
    }

    private void initializeSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(this);
    }

    public void startListening() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer();
        }
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
        speechRecognizer.startListening(createRecognizerIntent());
    }

    private Intent createRecognizerIntent() {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        return recognizerIntent;
    }

    public void stopListening() {
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, originalVolume, 0);
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_LONG).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_LONG).show();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {}

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {}

    @Override
    public void onError(int error) {
        Log.e(TAG, "Speech recognition error: " + error);
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                Log.e(TAG, "No recognition result matched.");
                //Toast.makeText(getApplicationContext(), "No match found. Please try again.", Toast.LENGTH_SHORT).show();
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                Log.e(TAG, "Recognition service is busy.");
                new Handler().postDelayed(this::startListening, 1000);
                return;
            default:
                Log.e(TAG, "Other speech recognition error: " + error);
        }
        new Handler().postDelayed(this::startListening, 500);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null) {
            process(matches);
        }
        new Handler().postDelayed(this::startListening, 500);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {}

    @Override
    public void onEvent(int eventType, Bundle params) {}

    private void process(ArrayList<String> suggestedWords) {
        for (String word : suggestedWords) {
            if (word.contains("emergency")) {
                emergencyActionsExecuted = false;
                makeCall();
                startRecording();
                sendTextWithLocation();
                break;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    Log.i(TAG, "Location retrieved: " + location.getLatitude() + ", " + location.getLongitude());
                    LocationStorage.getInstance().setLocation(location.getLatitude(), location.getLongitude());
                    sendSMSToAllContacts(location);
                    stopLocationUpdates();
                } else {
                    Log.e(TAG, "Location is null");
                }
            }
        };
        LocationRequest locationRequest = new LocationRequest.Builder(LocationRequest.PRIORITY_HIGH_ACCURACY, 10000)
                .build();
        Log.i(TAG, "Requesting location updates");
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }

    private void sendTextWithLocation() {
        Log.i(TAG, "Send text method executing");
        getCurrentLocation();
    }

    private void sendSMSToAllContacts(Location location) {
        ContactsRepository contactsRepository = new ContactsRepository(getApplication());
        new Handler(getMainLooper()).post(() -> contactsRepository.getAllContacts().observeForever(contactLists -> {
            if (!emergencyActionsExecuted) {
                for (ContactList contact : contactLists) {
                    sendSMS(contact.getPhoneNumber(), location);
                }
                emergencyActionsExecuted = true;
            }
        }));
    }

    private void sendSMS(String phoneNumber, Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        SmsManager smsManager = SmsManager.getDefault();
        String message = "Help! I am in danger.\nMy current location is: https://maps.google.com/maps?q=" + latitude + "," + longitude + "\nThis is an automated message.";
        try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.i(TAG, "SMS sent to: " + phoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS: " + e.getMessage());
        }
    }

    private void makeCall() {
        Log.i(TAG, "Make call");
        ContactsViewModel contactsViewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()).create(ContactsViewModel.class);
        contactsViewModel.getAllContacts().observeForever(contacts -> {
            if (!emergencyActionsExecuted) {
                for (ContactList contact : contacts) {
                    if (contact.isDefault()) {
                        Uri phoneUri = Uri.parse("tel:" + contact.getPhoneNumber());
                        Intent callIntent = new Intent(Intent.ACTION_CALL, phoneUri);
                        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            startActivity(callIntent);
                        } else {
                            // Handle permission request (You might need to use another mechanism to request permissions in a Service)
                            Toast.makeText(this, "Permission denied to make calls.", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                }
                Toast.makeText(this, "No default contact set.", Toast.LENGTH_SHORT).show();
                emergencyActionsExecuted = true;
            }
        });
    }

    private void startRecording() {
        if (isRecording) return;

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        File audioFile = getOutputFile();
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.i(TAG, "Recording started.");
            Toast.makeText( this,"Recording started.", Toast.LENGTH_SHORT).show();

            // Stop recording after timeout
            new Handler().postDelayed(this::stopRecording, RECORDING_DURATION_MS);
        } catch (IOException e) {
            Log.e(TAG, "Recording failed: " + e.getMessage());
        }
    }

    private File getOutputFile() {
        File recordingsDir = new File(getFilesDir(), "Safenow/Recordings/Audios");
        if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
            Log.e(TAG, "Failed to create directory: " + recordingsDir.getAbsolutePath());
        }
        return new File(recordingsDir, "audio_record_" + System.currentTimeMillis() + ".m4a");
    }

    private void stopRecording() {
        if (!isRecording) return;

        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
        isRecording =false;
        Log.i(TAG, "Recording stopped");
        Toast.makeText(this, "Recording stopped.", Toast.LENGTH_SHORT).show();
    }

    public class LocalBinder extends Binder {
        public VoiceActivationService getService() {
            return VoiceActivationService.this;
        }
    }
}
