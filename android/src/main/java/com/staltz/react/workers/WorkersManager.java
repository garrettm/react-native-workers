// support CodePush

package com.staltz.react.workers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

@ReactModule(name = "WorkersManager")
public class WorkersManager extends ReactContextBaseJavaModule {

  private final ReactApplicationContext context;
  private final ReactPackage packages[];
  private Integer key;
  private String bundleRoot;
  private String bundleResource;
  private Integer bundlerPort;
  private Promise promise;

  private final HashMap<Integer, WorkersInstance> workers = new HashMap<>();
  private final List<Integer> bundlerPorts = new ArrayList<>();


  public WorkersManager(
          final ReactApplicationContext context,
          final ReactPackage packages[]
  ) {
    super(context);
    this.context = context;
    this.packages = packages;
  }

  /**
   * Public interface
   */

  @Override
  public String getName() {
    return "WorkersManager";
  }

  @ReactMethod
  public void startWorker(
          final Integer key,
          final String bundleRoot,
          final String bundleResource,
          final Integer bundlerPort,
          final Promise promise
  ) {
    Assertions.assertCondition(!this.workers.containsKey(key), "Key already in use");

    this.key = key;
    this.bundleRoot = bundleRoot;
    this.bundleResource = bundleResource;
    this.bundlerPort = bundlerPort;
    this.promise = promise;

    final boolean hasBundlerPort = this.allocateBundlerPort(bundlerPort);

    final WorkersInstance worker = new WorkersInstance(
            key,
            context,
            this.packages,
            bundleRoot,
            bundleResource,
            hasBundlerPort ? bundlerPort : null,
            promise
    );

    this.workers.put(key, worker);

    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        worker.start();
      }
    });
  }

  @ReactMethod
  public void stopWorker(final Integer key) {
    this.workers.remove(key);
  }

  @ReactMethod
  public void postMessage(final Integer key, final String message) {
    WorkersInstance worker = this.workers.get(key);
    if (worker == null) {
      startWorker(this.key, this.bundleRoot, this.bundleResource, this.bundlerPort, this.promise);
      worker = this.workers.get(key);
    }
    Assertions.assertNotNull(worker).postMessage(message);
  }

  /**
   * Events
   */

  private interface RCTDeviceEventEmitter extends JavaScriptModule {
    void emit(String eventName, @Nullable Object data);
  }

  protected void emit(final String name, final Object body) {
    this.context
            .getJSModule(WorkersManager.RCTDeviceEventEmitter.class)
            .emit(name, body);
  }

  /**
   * Helpers
   */

  private boolean allocateBundlerPort(Integer port) {
    if (port == 0) {
      return false;
    }

    if (port == 8081) {
      Log.e(
              WorkersPackage.TAG,
              String.format(
                      "Bundler port %d is already in use by the inspector. " +
                              "Remote debugging is not possible without a unique port.",
                      port
              )
      );
      return false;
    }

    if (this.bundlerPorts.contains(port)) {
      Log.e(
              WorkersPackage.TAG,
              String.format(
                      "Bundler port %d is already in use by another worker. " +
                              "Remote debugging is not possible without a unique port.",
                      port
              )
      );
      return false;
    }

    this.bundlerPorts.add(port);
    return true;
  }

}
