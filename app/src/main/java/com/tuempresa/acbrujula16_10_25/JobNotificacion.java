package com.tuempresa.acbrujula16_10_25;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class JobNotificacion {

    public void enviarNotificacion(Context context, String titulo, String mensaje) {
        String CHANNEL_ID = "canal_brj";

        // Crear canal si no existe (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Brújula Inteligente",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notificaciones de detección al norte");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }

        // Crear notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.iconocamara2)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(1, builder.build());
    }
}
