package com.tuempresa.acbrujula16_10_25.ml;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
// import com.google.mlkit.vision.pose.Pose;
// import com.google.mlkit.vision.pose.PoseDetection;
// import com.google.mlkit.vision.pose.PoseDetector;
// import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class ObjectDetectorML {
    
    private final ObjectDetector detector;
    // private final PoseDetector poseDetector;
    private final Context context;
    
    public interface DetectionCallback {
        void onDetected(List<DetectedObject> objects);
    }
    
    public ObjectDetectorML(Context context) {
        this.context = context;
        // Configurar el detector de objetos con configuración optimizada para mejor detección
        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE) // Cambiar a SINGLE_IMAGE_MODE para mejor detección
                        .enableMultipleObjects()
                        .enableClassification()
                        .build();
        
        detector = ObjectDetection.getClient(options);
        
        // Inicializar detector de personas (Pose Detection) - Temporalmente deshabilitado
        // AccuratePoseDetectorOptions poseOptions = new AccuratePoseDetectorOptions.Builder()
        //         .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
        //         .build();
        // poseDetector = PoseDetection.getClient(poseOptions);
        
        Log.d("ObjectDetectorML", "Detector ML Kit inicializado con configuración SINGLE_IMAGE_MODE para mejor detección");
        Log.d("ObjectDetectorML", "Modelo: MobileObjectLocalizerV3_1TfLiteClient");
        Log.d("ObjectDetectorML", "Detector de personas (Pose) inicializado");
    }
    
    public void processImage(@NonNull InputImage image, @NonNull DetectionCallback callback) {
        // Validar que la imagen no sea null
        if (image == null) {
            Log.w("ObjectDetectorML", "Imagen null recibida");
            callback.onDetected(List.of());
            return;
        }
        
        // Verificar que la imagen no esté cerrada
        try {
            // Intentar acceder a los planos de la imagen para verificar si está cerrada
            image.getPlanes();
            Log.d("ObjectDetectorML", "Imagen válida recibida, dimensiones: " + image.getWidth() + "x" + image.getHeight());
        } catch (IllegalStateException e) {
            Log.w("ObjectDetectorML", "Imagen ya cerrada, saltando detección");
            callback.onDetected(List.of());
            return;
        }
        
        // Verificar que el detector no esté cerrado
        if (detector == null) {
            Log.w("ObjectDetectorML", "Detector no disponible");
            callback.onDetected(List.of());
            return;
        }
        
        Log.d("ObjectDetectorML", "Iniciando procesamiento de imagen con ML Kit...");
        Log.d("ObjectDetectorML", "Configuración: SINGLE_IMAGE_MODE, MultipleObjects=true, Classification=true");
        
        // Procesar objetos y personas en paralelo
        detector.process(image)
            .addOnSuccessListener(detectedObjects -> {
                Log.d("ObjectDetectorML", "Detección exitosa: " + detectedObjects.size() + " objetos");
                
                // Procesar objetos detectados
                if (!detectedObjects.isEmpty()) {
                    for (int i = 0; i < detectedObjects.size(); i++) {
                        DetectedObject obj = detectedObjects.get(i);
                        String label = "Objeto desconocido";

                        if (!obj.getLabels().isEmpty()) {
                            String originalLabel = obj.getLabels().get(0).getText();
                            float confidence = obj.getLabels().get(0).getConfidence();
                            
                            // Traducir categorías al español
                            label = translateCategoryToSpanish(originalLabel);
                            
                            // Logging especial para celulares
                            if (originalLabel.toLowerCase().contains("phone") || 
                                originalLabel.toLowerCase().contains("mobile") || 
                                originalLabel.toLowerCase().contains("smartphone")) {
                                Log.d("ObjectDetectorML", "📱 ¡CELULAR DETECTADO! " + originalLabel + 
                                      " → " + label + " (confianza: " + String.format("%.1f", confidence * 100) + "%)");
                            } else {
                                Log.d("ObjectDetectorML", "Objeto " + (i+1) + " detectado: " + originalLabel + 
                                      " → " + label + " (confianza: " + String.format("%.1f", confidence * 100) + "%)");
                            }
                        } else {
                            Log.d("ObjectDetectorML", "Objeto " + (i+1) + " detectado sin etiqueta");
                        }

                        // 🔹 Enviar broadcast con el objeto detectado y su posición
                        Intent intent = new Intent("com.app.BROADCAST_DETECCION");
                        intent.putExtra("objeto", label);
                        
                        // Calcular posición relativa del objeto
                        android.graphics.Rect box = obj.getBoundingBox();
                        float centerX = box.centerX();
                        float centerY = box.centerY();
                        intent.putExtra("centerX", centerX);
                        intent.putExtra("centerY", centerY);
                        
                        Log.d("ObjectDetectorML", "Enviando broadcast para objeto: " + label + 
                              " en posición (" + centerX + ", " + centerY + ")");
                        
                        context.sendBroadcast(intent);
                    }
                } else {
                    Log.d("ObjectDetectorML", "No se detectaron objetos en esta imagen");
                    Log.d("ObjectDetectorML", "Posibles causas: objeto muy pequeño, poca iluminación, o fuera del rango de detección");
                }
                
                // Detección de personas temporalmente deshabilitada
                // TODO: Reactivar cuando se resuelvan los problemas de dependencias
                Log.d("ObjectDetectorML", "Detección de personas temporalmente deshabilitada");
                callback.onDetected(detectedObjects);
            })
            .addOnFailureListener(e -> {
                Log.e("ObjectDetectorML", "Error en detección: " + e.getMessage(), e);
                // En caso de error, devolver lista vacía
                callback.onDetected(List.of());
            });
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
                return "📱 Teléfono";
            case "mobile phone":
                return "📱 Teléfono Móvil";
            case "smartphone":
                return "📱 Smartphone";
            case "cell phone":
                return "📱 Celular";
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
    
    // Método temporalmente deshabilitado hasta resolver dependencias de Pose Detection
    // private DetectedObject createPersonObject(Pose pose, int imageWidth, int imageHeight) {
    //     // Código comentado temporalmente
    //     return null;
    // }
    
    public void close() {
        if (detector != null) {
            detector.close();
        }
        // if (poseDetector != null) {
        //     poseDetector.close();
        // }
    }
}