package com.staltz.react.workers;

import android.util.Log;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import javax.annotation.Nullable;

@ReactModule(name="WorkersInstanceManager")
public class WorkersInstanceManager extends ReactContextBaseJavaModule {

  private final ReactContext context;

  private Integer key;
  private ReactApplicationContext parentContext;
  private Promise startedPromise;

  public WorkersInstanceManager(
    final ReactApplicationContext context
  ) {
    super(context);
    this.context = context;
  }

  public void initialize(
    final Integer key,
    final ReactApplicationContext parentContext,
    final Promise startedPromise
  ) {
    this.key = key;
    this.parentContext = parentContext;
    this.startedPromise = startedPromise;
  }

  /**
   * Public interface
   */

  @Override
  public String getName() {
    return "WorkersInstanceManager";
  }

  @ReactMethod
  public void workerStarted() {
    if (this.startedPromise != null) {
      this.startedPromise.resolve(null);
      this.startedPromise = null;
    }
  }

  @ReactMethod
  public void postMessage(final String message) {
    if (this.parentContext == null) {
      Log.w(
        WorkersPackage.TAG,
        "You're invoking self.postMessage from the main JavaScript context, " +
        "which is a no-op. To send a message to a specific Worker instance, " +
        "invoke worker.postMessage on that instance."
      );
      return;
    }

    WritableMap body = Arguments.createMap();
    body.putInt("key", Assertions.assertNotNull(this.key));
    body.putString("message", message);

    this.parentContext
      .getNativeModule(WorkersManager.class)
      .emit("message", body);
  }

  /**
   * Events
   */

  private interface RCTDeviceEventEmitter extends JavaScriptModule {
    void emit(String eventName, @Nullable Object data);
  }

  protected void emit(final String name, final Object body) {
    this.context
      .getJSModule(WorkersInstanceManager.RCTDeviceEventEmitter.class)
      .emit(name, body);
  }

}
