package com.careertuner.app.configuration;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reports native capabilities that cannot be inferred safely from JavaScript.
 *
 * <p>The Capacitor PushNotifications plugin is packaged even when an Android
 * build has no google-services.json. Calling register() in that state reaches
 * FirebaseMessaging.getInstance(), which throws before the bridge can turn it
 * into a rejected JavaScript promise. Probe the actual default Firebase app so
 * the web layer can request notification permission without making that unsafe
 * registration call.</p>
 */
@CapacitorPlugin(name = "NativeConfiguration")
public final class NativeConfigurationPlugin extends Plugin {
    @PluginMethod
    public void getCapabilities(PluginCall call) {
        JSObject result = new JSObject();
        result.put("pushConfigured", isDefaultFirebaseAppInitialized());
        call.resolve(result);
    }

    private boolean isDefaultFirebaseAppInitialized() {
        try {
            // Reflection keeps this probe independent of Firebase at compile time while the
            // PushNotifications plugin remains the owner of the Firebase Messaging dependency.
            Class<?> firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp");
            Method getInstance = firebaseAppClass.getMethod("getInstance");
            return getInstance.invoke(null) != null;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
            return false;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }
}
