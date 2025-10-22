package com.tuempresa.acbrujula16_10_25;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tuempresa.acbrujula16_10_25.sensors.CompassThread;
import com.tuempresa.acbrujula16_10_25.JobNotificacion;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor magnetometer;
    private ImageView imgBrujula;
    private TextView tvHeading, tvMode;
    private FloatingActionButton btnOpenCamera;
    
    private float currentDegree = 0f;
    private boolean isNorthDetected = false;
    private float ultimaDireccion = 0f; // Para rastrear la última dirección
    
    private CompassThread compassThread;

    // 🔹 Receiver para recibir broadcasts internos
    private final BroadcastReceiver detectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if ("com.app.ORIENTACION_ACTUAL".equals(action)) {
                float azimuth = intent.getFloatExtra("azimuth", 0);
                ultimaDireccion = azimuth; // Guardar la última dirección
                Log.d("MainActivity", "Orientación recibida: " + azimuth);
                
                // Rotar la imagen de la brújula
                imgBrujula.setRotation(-azimuth);
                
                // Actualizar el texto del heading con dirección cardinal
                String direction = getDirection(azimuth);
                tvHeading.setText(String.format("%.1f° (%s)", azimuth, direction));
                
                // Verificar si apunta al norte
                boolean pointingNorth = azimuth >= 345 || azimuth <= 15;
                if (pointingNorth && !isNorthDetected) {
                    isNorthDetected = true;
                    tvMode.setText("Modo actual: Norte detectado - Cámara disponible");
                } else if (!pointingNorth && isNorthDetected) {
                    isNorthDetected = false;
                    tvMode.setText("Modo actual: Solo brújula");
                }
            } 
            else if ("com.app.BROADCAST_DETECCION".equals(action)) {
                String objeto = intent.getStringExtra("objeto");
                float centerX = intent.getFloatExtra("centerX", 0);
                float centerY = intent.getFloatExtra("centerY", 0);
                
                // Calcular dirección del objeto desde el centro de la pantalla
                // Asumiendo que la pantalla es de 1080x1920 (ajustar según dispositivo)
                int screenWidth = 1080;
                int screenHeight = 1920;
                int centerScreenX = screenWidth / 2;
                int centerScreenY = screenHeight / 2;
                
                float deltaX = centerX - centerScreenX;
                float deltaY = centerScreenY - centerY; // Invertir Y
                
                double angleRad = Math.atan2(deltaY, deltaX);
                double angleDeg = Math.toDegrees(angleRad);
                if (angleDeg < 0) angleDeg += 360;
                
                String direction = getDirection((float) angleDeg);
                


                // 🔹 Si apunta al norte (entre 345° y 15°), mostramos notificación
                if (ultimaDireccion >= 345 || ultimaDireccion <= 15) {
                    Log.d("MainActivity", "Detección al Norte: " + objeto);

                    // 🔹 Llamamos al JobNotificacion (estilo del profe)
                    JobNotificacion job = new JobNotificacion();
                    job.enviarNotificacion(
                            MainActivity.this,      // contexto
                            "Detección al Norte",  // título
                            "Objeto detectado: " + objeto + " en dirección " + direction // mensaje
                    );
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializar sensorManager primero
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        // Pedir permiso de notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        
        initViews();
        setupSensors();
        setupCameraButton();
        
        // Inicializar CompassThread
        compassThread = new CompassThread(this, sensorManager);
        
        // 🔔 Crear canal de notificación (solo se hace una vez)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "canal_brj",
                    "Brújula Inteligente",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notificaciones de detección al norte");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private void initViews() {
        imgBrujula = findViewById(R.id.imgBrujula);
        tvHeading = findViewById(R.id.tvHeading);
        tvMode = findViewById(R.id.tvMode);
        btnOpenCamera = findViewById(R.id.btnOpenCamera);
    }

    private void setupSensors() {
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void setupCameraButton() {
        btnOpenCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                requestPermission.launch(Manifest.permission.CAMERA);
            }
        });
    }

    private final ActivityResultLauncher<String> requestPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
            });

    private void openCamera() {
        Intent intent = new Intent(this, com.tuempresa.acbrujula16_10_25.ml.CameraActivity.class);
        startActivity(intent);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Esta implementación se mantiene como respaldo, pero el CompassThread
        // manejará la lógica principal de la brújula
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            // Solo actualizar la UI, la lógica principal está en CompassThread
            Log.d("MainActivity", "Sensor magnético detectado: " + event.values[0]);
        }
    }

    private String getDirection(float degree) {
        if (degree >= 337.5 || degree < 22.5) return "N";
        if (degree >= 22.5 && degree < 67.5) return "NE";
        if (degree >= 67.5 && degree < 112.5) return "E";
        if (degree >= 112.5 && degree < 157.5) return "SE";
        if (degree >= 157.5 && degree < 202.5) return "S";
        if (degree >= 202.5 && degree < 247.5) return "SO";
        if (degree >= 247.5 && degree < 292.5) return "O";
        if (degree >= 292.5 && degree < 337.5) return "NO";
        return "N";
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No necesario para este caso
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        
        // Registrar el receiver dinámicamente
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.app.ORIENTACION_ACTUAL");
        filter.addAction("com.app.BROADCAST_DETECCION");
        registerReceiver(detectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d("MainActivity", "Receiver registrado");
        
        // Iniciar CompassThread solo si no existe o está detenido
        if (compassThread == null || !compassThread.isAlive()) {
            compassThread = new CompassThread(this, sensorManager);
            compassThread.start();
            Log.d("MainActivity", "CompassThread iniciado");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        
        // Detener CompassThread de forma segura
        if (compassThread != null && compassThread.isAlive()) {
            compassThread.detener();
            try {
                compassThread.join(1000); // Esperar máximo 1 segundo
            } catch (InterruptedException e) {
                Log.e("MainActivity", "Error esperando CompassThread: " + e.getMessage());
            }
            compassThread = null;
            Log.d("MainActivity", "CompassThread detenido");
        }
        
        // Desregistrar el receiver de forma segura
        try {
            unregisterReceiver(detectionReceiver);
            Log.d("MainActivity", "Receiver desregistrado");
        } catch (IllegalArgumentException e) {
            Log.w("MainActivity", "Receiver ya estaba desregistrado");
        }
    }
}