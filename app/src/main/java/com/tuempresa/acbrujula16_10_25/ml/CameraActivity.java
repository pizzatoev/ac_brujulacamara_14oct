package com.tuempresa.acbrujula16_10_25.ml;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.tuempresa.acbrujula16_10_25.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView txtDetected;
    private ObjectDetectorML detector;
    private ExecutorService cameraExecutor;
    private long lastProcessTime = 0;
    private static final long PROCESSING_INTERVAL = 2000; // Procesar cada 2 segundos para evitar cambios rápidos
    
    // Sistema de persistencia para objetos detectados
    private List<DetectedObject> lastDetectedObjects = new ArrayList<>();
    private long lastDetectionTime = 0;
    private static final long DETECTION_PERSISTENCE_TIME = 5000; // Mostrar objetos por 5 segundos
    
    // Sistema de enfriamiento para evitar detecciones demasiado frecuentes
    private long lastSuccessfulDetection = 0;
    private static final long DETECTION_COOLDOWN = 3000; // Esperar 3 segundos entre detecciones exitosas

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        txtDetected = findViewById(R.id.txtDetectedObject);

        // Inicializar detector con manejo de errores
        try {
            detector = new ObjectDetectorML(this);
            Log.d("CameraActivity", "Detector ML Kit inicializado correctamente");
        } catch (Exception e) {
            Log.e("CameraActivity", "Error inicializando detector: " + e.getMessage());
            txtDetected.setText("Error inicializando detector ML Kit");
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private final ActivityResultLauncher<String> requestPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
            });

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1920, 1080)) // Resolución alta para detectar celulares pequeños
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, image -> analyzeImage(image));

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        // Throttling: solo procesar cada cierto intervalo
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < PROCESSING_INTERVAL) {
            imageProxy.close();
            return;
        }
        lastProcessTime = currentTime;
        
        if (imageProxy.getImage() != null && detector != null) {
            try {
                Log.d("CameraActivity", "Procesando imagen: " + imageProxy.getImage().getWidth() + "x" + imageProxy.getImage().getHeight());
                
                // Crear InputImage ANTES de procesar
                InputImage image = InputImage.fromMediaImage(
                        imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

                Log.d("CameraActivity", "InputImage creado, enviando a detector ML Kit...");

                // Procesar imagen de forma síncrona para evitar race conditions
                detector.processImage(image, objects -> {
                    // Cerrar ImageProxy solo DESPUÉS de procesar
                    imageProxy.close();
                    runOnUiThread(() -> showObjects(objects));
                });
                
                // NO cerrar aquí - se cierra en el callback
                return;
                
            } catch (Exception e) {
                Log.e("CameraActivity", "Error procesando imagen: " + e.getMessage(), e);
                runOnUiThread(() -> txtDetected.setText("Error procesando imagen: " + e.getMessage()));
            }
        } else {
            Log.w("CameraActivity", "Imagen o detector no disponible - imagen: " + (imageProxy.getImage() != null) + ", detector: " + (detector != null));
        }
        // Solo cerrar si no se procesó la imagen
        imageProxy.close();
    }

    private void showObjects(List<DetectedObject> objects) {
        Log.d("CameraActivity", "Mostrando " + objects.size() + " objetos detectados");
        
        long currentTime = System.currentTimeMillis();
        
        // Si se detectaron objetos nuevos, verificar cooldown y actualizar
        if (!objects.isEmpty()) {
            long timeSinceLastDetection = currentTime - lastSuccessfulDetection;
            
            if (timeSinceLastDetection >= DETECTION_COOLDOWN) {
                lastDetectedObjects = new ArrayList<>(objects);
                lastDetectionTime = currentTime;
                lastSuccessfulDetection = currentTime;
                Log.d("CameraActivity", "Objetos detectados actualizados, persistencia iniciada por 5 segundos");
            } else {
                Log.d("CameraActivity", "Detección ignorada por cooldown, tiempo restante: " + 
                      ((DETECTION_COOLDOWN - timeSinceLastDetection) / 1000) + " segundos");
                // Mostrar objetos anteriores si aún están en período de persistencia
                objects = lastDetectedObjects;
            }
        }
        
        // Verificar si aún estamos en el período de persistencia
        boolean inPersistencePeriod = (currentTime - lastDetectionTime) < DETECTION_PERSISTENCE_TIME;
        
        if (lastDetectedObjects.isEmpty() || !inPersistencePeriod) {
            if (lastDetectedObjects.isEmpty()) {
                txtDetected.setText("🔍 Sin objetos detectados\n\n📱 Para CELULARES:\n• Apunta directo al celular\n• Distancia: 30-50cm\n• Buena iluminación\n• Sin reflejos\n• Visible completo\n\n💡 Consejos:\n• Iluminación clave\n• Cámara estable\n• Objetos: celulares, laptops\n\n🔧 Config: 1920x1080, 2s");
            } else {
                txtDetected.setText("⏰ Objetos expirados\n\n🔍 Esperando detección...");
            }
            return;
        }
        
        // Mostrar objetos persistentes
        objects = lastDetectedObjects;

        StringBuilder result = new StringBuilder();
        
        // Calcular tiempo restante de persistencia
        long timeRemaining = DETECTION_PERSISTENCE_TIME - (currentTime - lastDetectionTime);
        int secondsRemaining = (int) (timeRemaining / 1000);
        result.append("🎯 ").append(objects.size()).append(" objeto(s)\n");
        result.append("⏰ ").append(secondsRemaining).append("s\n\n");
        
        int screenWidth = previewView.getWidth();
        int screenHeight = previewView.getHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        Log.d("CameraActivity", "Pantalla: " + screenWidth + "x" + screenHeight + ", Centro: (" + centerX + ", " + centerY + ")");
        Log.d("CameraActivity", "Tiempo restante de persistencia: " + secondsRemaining + " segundos");

        for (int i = 0; i < objects.size(); i++) {
            DetectedObject obj = objects.get(i);
            String label = "Objeto sin clasificar";
            float confidence = 0.0f;
            
            if (!obj.getLabels().isEmpty()) {
                String originalLabel = obj.getLabels().get(0).getText();
                confidence = obj.getLabels().get(0).getConfidence();
                
                // Traducir categorías al español
                label = translateCategoryToSpanish(originalLabel);
            }

            Rect box = obj.getBoundingBox();
            int objCenterX = box.centerX();
            int objCenterY = box.centerY();
            
            Log.d("CameraActivity", "Objeto " + (i+1) + ": " + label + " en (" + objCenterX + ", " + objCenterY + ")");
            
            // Calcular dirección angular desde el centro
            float deltaX = objCenterX - centerX;
            float deltaY = centerY - objCenterY; // Invertir Y porque la pantalla tiene origen arriba
            
            // Calcular ángulo en grados
            double angleRad = Math.atan2(deltaY, deltaX);
            double angleDeg = Math.toDegrees(angleRad);
            if (angleDeg < 0) angleDeg += 360;
            
            // Determinar dirección cardinal
            String direction = getCardinalDirection((float) angleDeg);
            
            // Calcular distancia relativa
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            double maxDistance = Math.sqrt(centerX * centerX + centerY * centerY);
            int distancePercent = (int) ((distance / maxDistance) * 100);

            result.append("🔍 ").append(label)
                  .append(" (").append(String.format("%.0f", confidence * 100)).append("%)\n")
                  .append("📍 ").append(direction).append(" ").append(String.format("%.0f", angleDeg)).append("°\n")
                  .append("📏 ").append(distancePercent).append("% centro\n\n");
        }

        txtDetected.setText(result.toString());
    }
    
    private String translateCategoryToSpanish(String englishCategory) {
        switch (englishCategory.toLowerCase()) {
            case "home good":
                return "Bien del Hogar";
            case "fashion good":
                return "Bien de Moda";
            case "vehicle":
                return "Vehículo";
            case "person":
                return "Persona";
            case "pet":
                return "Mascota";
            case "electronics":
                return "Electrónico";
            case "food":
                return "Alimento";
            case "book":
                return "Libro";
            case "furniture":
                return "Mueble";
            case "clothing":
                return "Ropa";
            case "shoe":
                return "Zapato";
            case "bag":
                return "Bolso";
            case "phone":
                return "Teléfono";
            case "laptop":
                return "Laptop";
            case "tablet":
                return "Tablet";
            case "watch":
                return "Reloj";
            case "bottle":
                return "Botella";
            case "cup":
                return "Taza";
            case "plate":
                return "Plato";
            case "bowl":
                return "Cuenco";
            case "chair":
                return "Silla";
            case "sofa":
                return "Sofá";
            case "bed":
                return "Cama";
            case "table":
                return "Mesa";
            case "lamp":
                return "Lámpara";
            case "car":
                return "Coche";
            case "truck":
                return "Camión";
            case "bus":
                return "Autobús";
            case "motorcycle":
                return "Motocicleta";
            case "bicycle":
                return "Bicicleta";
            case "dog":
                return "Perro";
            case "cat":
                return "Gato";
            case "bird":
                return "Pájaro";
            case "fish":
                return "Pez";
            default:
                return englishCategory; // Si no se encuentra traducción, devolver original
        }
    }
    
    private String getCardinalDirection(float angle) {
        if (angle >= 337.5 || angle < 22.5) return "Norte";
        if (angle >= 22.5 && angle < 67.5) return "Noreste";
        if (angle >= 67.5 && angle < 112.5) return "Este";
        if (angle >= 112.5 && angle < 157.5) return "Sureste";
        if (angle >= 157.5 && angle < 202.5) return "Sur";
        if (angle >= 202.5 && angle < 247.5) return "Suroeste";
        if (angle >= 247.5 && angle < 292.5) return "Oeste";
        if (angle >= 292.5 && angle < 337.5) return "Noroeste";
        return "Norte";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) {
            detector.close();
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
    }
}
